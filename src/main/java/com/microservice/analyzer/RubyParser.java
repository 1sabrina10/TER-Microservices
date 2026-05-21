package com.microservice.analyzer;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Analyse statique des fichiers Ruby (.rb) d'un projet Rails.
 * Extrait les classes, methodes, parametres et appels par expressions regulieres.
 */
public class RubyParser {

    /**
     * Represente une methode Ruby avec ses parametres et les appels qu'elle effectue.
     */
    public static class RubyMethod {

        public String name;
        public String className;
        public List<String> params;
        public List<String> calls;
        public boolean isOptional;
        public int optionalParamsCount = 0;
        public String body;

        public RubyMethod(String name, String className, List<String> params, String body) {
            this.name      = name;
            this.className = className;
            this.params    = params;
            this.body      = body;
            this.calls     = new ArrayList<>();
            this.isOptional = false;
        }

        /**
         * Score heuristique : ratio de parametres optionnels sur le total.
         */
        public double getOptionalHeuristic() {
            if (params.isEmpty()) return 0.0;
            return (double) optionalParamsCount / params.size();
        }

        /** Signature unique utilisee comme identifiant de noeud dans le graphe. */
        public String getSignature() {
            return className + "." + name;
        }

        @Override
        public String toString() {
            return getSignature() + "(" + String.join(", ", params) + ")";
        }
    }

    /**
     * Represente une classe Ruby avec son heritage et ses methodes.
     */
    public static class RubyClass {

        public String name;
        public String parent;
        public List<RubyMethod> methods;

        public RubyClass(String name, String parent) {
            this.name    = name;
            this.parent  = parent;
            this.methods = new ArrayList<>();
        }
    }

    // ─────────────────────────────────────────────────────────────

    /**
     * Parcourt recursivement le projet et parse tous les fichiers .rb trouves.
     */
    public List<RubyClass> parseProject(String directoryPath) throws IOException {
        List<RubyClass> classes = new ArrayList<>();
        Path path = Paths.get(directoryPath);

        if (!Files.exists(path)) return classes;

        Files.walk(path)
                .filter(p -> p.toString().endsWith(".rb"))
                .forEach(p -> {
                    try {
                        classes.addAll(parseFile(p.toFile()));
                    } catch (IOException e) {
                        System.err.println("Erreur lecture : " + p + " -> " + e.getMessage());
                    }
                });

        return classes;
    }

    /**
     * Parse un fichier Ruby ligne par ligne et extrait classes, methodes et appels.
     */
    public List<RubyClass> parseFile(File file) throws IOException {
        List<RubyClass> classes = new ArrayList<>();
        List<String> lines = Files.readAllLines(file.toPath());

        // Patterns de detection
        Pattern classPattern        = Pattern.compile(
                "^\\s*class\\s+([A-Za-z0-9_:]+)(?:\\s*<\\s*([A-Za-z0-9_:]+))?");
        Pattern defPattern          = Pattern.compile(
                "^\\s*def\\s+([A-Za-z0-9_\\.!?]+)(?:\\s*\\((.*)\\))?");
        Pattern callPattern         = Pattern.compile(
                "([A-Z][A-Za-z0-9_]*)\\.([a-z0-9_]+)");
        Pattern beforeActionPattern = Pattern.compile(
                "before_action\\s+:([a-z0-9_]+)");
        Pattern bareCallPattern     = Pattern.compile(
                "^\\s*([a-z0-9_]+[!?]?)(?:\\s|\\(|$)");

        // Receivers  Rails a ignorer (pas de classes interne Ruby)
        Set<String> ignoreReceivers = new HashSet<>(Arrays.asList(
                "ActiveSupport", "ActionDispatch", "ApplicationSystemTestCase"));

        // Mots-cles Ruby a ne pas confondre avec des appels de methode
        Set<String> keywords = new HashSet<>(Arrays.asList(
                "def", "end", "class", "module", "if", "else", "elsif",
                "unless", "while", "until", "for", "return", "yield",
                "require", "include", "extend"));

        // Etat courant du parsing
        RubyClass  currentClass  = null;
        RubyMethod currentMethod = null;

        for (String line : lines) {
            String trimmed = line.trim();

            // Ignorer lignes vides et commentaires
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            // Detection d'une classe
            Matcher classM = classPattern.matcher(line);
            if (classM.find()) {
                currentClass  = new RubyClass(classM.group(1), classM.group(2));
                currentMethod = null;
                classes.add(currentClass);
                continue;
            }

            // "end" ferme la methode en cours, puis la classe
            if (trimmed.equals("end")) {
                if (currentMethod != null) currentMethod = null;
                else if (currentClass != null) currentClass = null;
                continue;
            }

            // Detection d'une methode
            Matcher defM = defPattern.matcher(line);
            if (defM.find() && currentClass != null) {
                List<String> params = new ArrayList<>();
                int optCount = 0;

                if (defM.group(2) != null && !defM.group(2).trim().isEmpty()) {
                    for (String p : defM.group(2).split(",")) {
                        String param = p.trim().replaceAll("=.*", "").trim();
                        if (!param.isEmpty()) params.add(param);
                        if (p.contains("=")) optCount++;
                    }
                }

                currentMethod = new RubyMethod(
                        defM.group(1), currentClass.name, params, "");
                currentMethod.optionalParamsCount = optCount;
                currentMethod.isOptional = (optCount > 0);
                currentClass.methods.add(currentMethod);
                continue;
            }

            // Extraction des appels dans le corps de la methode courante
            if (currentMethod != null && currentClass != null) {

                // Appels qualifies : Receiver.methode
                Matcher callM = callPattern.matcher(line);
                while (callM.find()) {
                    if (!ignoreReceivers.contains(callM.group(1)))
                        currentMethod.calls.add(
                                callM.group(1) + "." + callM.group(2));
                }

                // Hooks Rails : before_action :nom_methode
                Matcher bam = beforeActionPattern.matcher(line);
                while (bam.find())
                    currentMethod.calls.add(
                            currentClass.name + "."
                                    + bam.group(1).replace(":", "").trim());

                // Appels internes sans receiver
                Matcher bareM = bareCallPattern.matcher(line);
                if (bareM.find()) {
                    String bare = bareM.group(1).trim();
                    if (!keywords.contains(bare))
                        currentMethod.calls.add(currentClass.name + "." + bare);
                }
            }
        }

        return classes;
    }
}