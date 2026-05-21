package com.microservice.analyzer;

import java.util.*;
/**
 * Identifie et classifie les taches du projet Ruby.
 * Chaque methode devient une tache PRIMITIVE ou COMPOSITE selon sa position
 */
public class TaskIdentifier {

    public static final String SAVE = "save";
    public static final String UPDATE = "update";
    public static final String DESTROY = "destroy";
    public static final String FIND = "find";
    public static final String WHERE = "where";

    public enum TaskType {
        PRIMITIVE, COMPOSITE
    }

    /** Represente une methode classifiee avec ses entrees, sorties et indicateurs. */
    public static class Task {
        public String signature;
        public TaskType type;
        public List<String> inputs;
        public List<String> outputs;
        public boolean inCycle;           // appartient a une dependance circulaire
        public boolean hasOptionalParams; // methode avec parametres optionnels
        public String className;
        public String methodName;

        public Task(String signature, TaskType type, String className, String methodName) {
            this.signature  = signature;
            this.type       = type;
            this.className  = className;
            this.methodName = methodName;
            this.inputs     = new ArrayList<>();
            this.outputs    = new ArrayList<>();
            this.inCycle    = false;
            this.hasOptionalParams = false;
        }

        @Override
        public String toString() {
            return "[" + type + "] " + signature +
                    (inCycle          ? " (CYCLE)"     : "") +
                    (hasOptionalParams ? " (OPTIONNEL)" : "") +
                    "\n    Inputs  : " + inputs +
                    "\n    Outputs : " + outputs;
        }
    }

    private CallGraph callGraph;
    private List<RubyParser.RubyClass> classes;
    private List<Task> tasks = new ArrayList<>();

    public TaskIdentifier(CallGraph callGraph, List<RubyParser.RubyClass> classes) {
        this.callGraph = callGraph;
        this.classes   = classes;
    }

    /**
     * Classifie chaque methode en PRIMITIVE ou COMPOSITE.
     *
     * Regles appliquees dans l'ordre :
     *  - Methode feuille (aucun appel sortant)          -> PRIMITIVE
     *  - Dans un cycle, tous les appels restent dedans  -> PRIMITIVE
     *  - Dans un cycle avec appels hors cycle           -> COMPOSITE
     *  - Corps contient operations DB (save + find)     -> COMPOSITE (reclassification)
     *  - Sinon                                          -> COMPOSITE
     */
    public List<Task> identify() {
        tasks.clear();
        Set<String> leaves = callGraph.getLeaves();

        // Collecter tous les noeuds appartenant a un cycle
        List<List<String>> cycles = callGraph.detectCycles();
        Set<String> cycleNodes = new HashSet<>();
        for (List<String> cycle : cycles) cycleNodes.addAll(cycle);

        for (RubyParser.RubyClass c : classes) {
            for (RubyParser.RubyMethod m : c.methods) {
                String sig = m.getSignature();

                TaskType type;
                if (cycleNodes.contains(sig)) {
                    // Methode dans un cycle : PRIMITIVE seulement si tous
                    // ses appels restent a l'interieur du meme cycle
                    boolean allCallsInCycle = callGraph.getEdges().stream()
                            .filter(e -> e.from.equals(sig))
                            .allMatch(e -> cycleNodes.contains(e.to));
                    type = allCallsInCycle ? TaskType.PRIMITIVE : TaskType.COMPOSITE;
                } else {
                    // Hors cycle : PRIMITIVE si feuille, COMPOSITE sinon
                    type = leaves.contains(sig) ? TaskType.PRIMITIVE : TaskType.COMPOSITE;
                }

                // si elle combine lecture (find/where) et ecriture (save/update/destroy),
                // elle est probablement composite meme si le graphe dit PRIMITIVE
                String body = m.body == null ? "" : m.body;
                int complexity = 0;
                if (body.contains(SAVE) || body.contains(UPDATE) || body.contains(DESTROY)) complexity++;
                if (body.contains(FIND) || body.contains(WHERE)) complexity++;
                if (complexity >= 2 && type == TaskType.PRIMITIVE)
                    type = TaskType.COMPOSITE;

                // Construction de la tache
                Task task = new Task(sig, type, c.name, m.name);
                task.inCycle          = cycleNodes.contains(sig);
                task.hasOptionalParams = m.isOptional;

                // Entrees : l'instance courante + les parametres de la methode
                task.inputs.add("this (" + c.name + ")");
                task.inputs.addAll(m.params);

                // Sorties : valeur de retour pour les methodes de lecture
                task.outputs.add("this (modified)");
                if (m.name.startsWith("get") || m.name.startsWith("find")
                        || m.name.startsWith("check") || m.name.startsWith("calculate"))
                    task.outputs.add("return value");

                tasks.add(task);
            }
        }
        return tasks;
    }

    /**
     * Detecte les ambiguites de polymorphisme et d'heritage.
     *
     * Une ambiguite existe quand plusieurs classes definissent une methode de meme nom.
     *  - POLYMORPHISME SIMPLE : meme nom, classes sans lien d'heritage
     *  - HERITAGE : la methode est surchargee dans une classe fille
     *  - COMBINE : heritage + polymorphisme avec >= 3 classes
     */
    public List<String> detectPolymorphism() {
        // Construire la map : nom_methode -> [classes qui la definissent]
        Map<String, List<String>> methodMap = new HashMap<>();
        for (RubyParser.RubyClass c : classes)
            for (RubyParser.RubyMethod m : c.methods)
                methodMap.computeIfAbsent(m.name, k -> new ArrayList<>()).add(c.name);

        List<String> ambiguities = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : methodMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                boolean hasInheritance = false;
                List<String> classList = entry.getValue();

                // Verifier si l'une des classes est la parente d'une autre
                for (String className : classList) {
                    RubyParser.RubyClass current = findClass(className);
                    if (current != null && current.parent != null
                            && classList.contains(current.parent)) {
                        hasInheritance = true;
                        break;
                    }
                }

                // Classification de l'ambiguite
                String type;
                if (hasInheritance && classList.size() > 2) type = "COMBINE (Heritage + Poly)";
                else if (hasInheritance)                     type = "HERITAGE";
                else                                         type = "POLYMORPHISME SIMPLE";

                ambiguities.add(type + " : '" + entry.getKey() + "' dans " + classList);
            }
        }
        return ambiguities;
    }

    /** Recherche une classe par son nom dans la liste du projet. */
    private RubyParser.RubyClass findClass(String name) {
        return classes.stream()
                .filter(c -> c.name.equals(name))
                .findFirst().orElse(null);
    }

    public void print() {
        System.out.println("\n=== IDENTIFICATION DES TACHES ===");
        List<Task> identified = identify();
        long primitives = identified.stream().filter(t -> t.type == TaskType.PRIMITIVE).count();
        long composites  = identified.stream().filter(t -> t.type == TaskType.COMPOSITE).count();
        System.out.println("Primitives : " + primitives + " | Composites : " + composites);
        System.out.println();
        for (Task t : identified) System.out.println(t);

        System.out.println("\n=== AMBIGUITES (Polymorphisme / Heritage) ===");
        List<String> ambiguities = detectPolymorphism();
        if (ambiguities.isEmpty())
            System.out.println("Aucune ambiguite detectee.");
        else
            ambiguities.forEach(a -> System.out.println("   " + a));
    }
}