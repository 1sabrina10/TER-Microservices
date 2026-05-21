package com.microservice.analyzer;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/**
 * Génère le graphe des microservices candidats au format DOT (Graphviz)
 * en utilisant la classification sémantique DDD fournie par le LLM.
 */
public class GraphExporter {

    private CallGraph callGraph;
    private List<TaskIdentifier.Task> tasks;
    private LLMAnalyzer llmAnalyzer; // Ajout du LLM pour l'arbitrage sémantique
    private Map<String, String> classToServiceMap = new HashMap<>(); // Association Classe -> Microservice
    private Map<String, String> colorCache = new HashMap<>();

    public GraphExporter(CallGraph callGraph, List<TaskIdentifier.Task> tasks, LLMAnalyzer llmAnalyzer) {
        this.callGraph = callGraph;
        this.tasks = tasks;
        this.llmAnalyzer = llmAnalyzer;
    }

    /**
     * Interroge le LLM pour regrouper TOUTES les classes de manière sémantique (DDD).
     */
    private void initializeMicroserviceMapping() {
        System.out.println(" Consultation du LLM pour cartographier les Bounded Contexts (DDD)...");

        // On récupère toutes les classes uniques du projet
        Set<String> uniqueClasses = new HashSet<>();
        for (TaskIdentifier.Task t : tasks) {
            uniqueClasses.add(t.className);
        }
        for (CallGraph.Edge e : callGraph.getEdges()) {
            uniqueClasses.add(e.from.split("\\.")[0]);
            uniqueClasses.add(e.to.split("\\.")[0]);
        }

        // On construit un prompt structuré demandant un format JSON strict au LLM
        StringBuilder prompt = new StringBuilder();
        prompt.append("Associe CHAQUE classe de cette liste à un microservice cible cohérent selon tes conclusions DDD précédentes.\n");
        prompt.append("Liste des classes : ").append(uniqueClasses.toString()).append("\n\n");
        prompt.append("Réponds UNIQUEMENT sous la forme d'un objet JSON plat au format : {\"NomDeLaClasse\": \"NomDuMicroservice\"}.\n");
        prompt.append("Exemple attendu : {\"UsersController\": \"UserManagementService\", \"User\": \"UserManagementService\", \"Document\": \"DocumentManagementService\"}\n");
        prompt.append("Ne rajoute aucun texte avant ou après le JSON.");

        try {
            String rawJson = llmAnalyzer.ask(prompt.toString());

            // Nettoyage au cas où le LLM met des balises markdown ```json ... ```
            if (rawJson.contains("{")) {
                rawJson = rawJson.substring(rawJson.indexOf("{"), rawJson.lastIndexOf("}") + 1);
            }

            JSONObject json = new JSONObject(rawJson);
            for (String className : json.keySet()) {
                classToServiceMap.put(className, json.getString(className));
            }

            System.out.println(" Cartographie sémantique initialisée avec succès !");
        } catch (Exception e) {
            System.out.println("⚠️ Échec du mapping LLM automatique, repli sur la règle par défaut : " + e.getMessage());
            // Fallback par défaut si le parsing JSON échoue
            for (String className : uniqueClasses) {
                classToServiceMap.put(className, fallbackGetMicroservice(className));
            }
        }
    }

    /**
     * Récupère le nom du microservice via la décision sémantique du LLM.
     */
    private String getMicroservice(String className) {
        if (classToServiceMap.isEmpty()) {
            initializeMicroserviceMapping();
        }
        return classToServiceMap.getOrDefault(className, "InfrastructureService");
    }

    /**
     * Règle de secours si le LLM n'a pas répondu correctement.
     */
    private String fallbackGetMicroservice(String className) {
        if (className == null || className.isEmpty()) return "UnknownService";
        if (className.contains("Record") || className.contains("Shared")) return "InfrastructureShared";
        return className.replace("Controller", "").replace("Test", "") + "Service";
    }

    private String getDynamicColor(String ms) {
        if (colorCache.containsKey(ms)) return colorCache.get(ms);
        int hash = ms.hashCode();
        int r = 180 + (Math.abs((hash & 0xFF0000) >> 16) % 70);
        int g = 180 + (Math.abs((hash & 0x00FF00) >> 8)  % 70);
        int b = 180 + (Math.abs(hash & 0x0000FF)          % 70);
        String color = String.format("#%02x%02x%02x", r, g, b);
        colorCache.put(ms, color);
        return color;
    }

    public void exportDot(String outputPath) throws IOException {
        StringBuilder sb = new StringBuilder();

        sb.append("digraph Microservices {\n");
        sb.append("    rankdir=LR;\n");
        sb.append("    graph [fontname=\"Arial\", nodesep=0.5, ranksep=1.2, bgcolor=white, compound=true];\n");
        sb.append("    node [fontname=\"Arial\", fontsize=10, shape=box, style=\"filled,rounded\"];\n");
        sb.append("    edge [fontname=\"Arial\", arrowsize=0.8];\n\n");

        // Regrouper les tâches par microservice (en utilisant la map sémantique)
        Map<String, List<TaskIdentifier.Task>> byMs = new LinkedHashMap<>();
        for (TaskIdentifier.Task t : tasks) {
            String ms = getMicroservice(t.className);
            byMs.computeIfAbsent(ms, k -> new ArrayList<>()).add(t);
        }

        // Générer un cluster (subgraph) par microservice cible réel
        int idx = 0;
        for (Map.Entry<String, List<TaskIdentifier.Task>> entry : byMs.entrySet()) {
            String ms = entry.getKey();
            sb.append("    subgraph cluster_").append(idx++).append(" {\n");
            sb.append("        label=\"").append(ms).append("\";\n");
            sb.append("        style=\"filled,rounded\";\n");
            sb.append("        fillcolor=\"").append(getDynamicColor(ms)).append("\";\n");
            sb.append("        fontname=\"Arial Bold\";\n");
            sb.append("        fontsize=12;\n");

            for (TaskIdentifier.Task t : entry.getValue()) {
                sb.append("        \"").append(sanitize(t.signature)).append("\"")
                        .append(" [label=\"").append(t.className).append("\\n.").append(t.methodName).append("()\", fillcolor=white];\n");
            }
            sb.append("    }\n\n");
        }

        // Générer les arêtes depuis le graphe d'appels
        for (CallGraph.Edge e : callGraph.getEdges()) {
            String fromClass = e.from.split("\\.")[0];
            String toClass   = e.to.split("\\.")[0];

            String fromMs = getMicroservice(fromClass);
            String toMs   = getMicroservice(toClass);

            if (!fromMs.equals(toMs)) {
                // Coupure réseau inter-services : rouge pointillé
                sb.append("    \"").append(sanitize(e.from)).append("\" -> \"")
                        .append(sanitize(e.to)).append("\" [color=\"#E74C3C\", style=dashed, weight=1];\n");
            } else {
                // Flux interne au même service : gris continu
                sb.append("    \"").append(sanitize(e.from)).append("\" -> \"")
                        .append(sanitize(e.to)).append("\" [color=\"#95A5A6\", weight=2];\n");
            }
        }

        sb.append("}\n");
        Files.write(Paths.get(outputPath), sb.toString().getBytes());
    }

    private String sanitize(String s) {
        return s.replace(".", "_").replace("-", "_").replace(":", "_");
    }

    public void exportAndRender(String basePath) throws Exception {
        String dotFile = basePath + ".dot";
        String pngFile = basePath + ".png";
        exportDot(dotFile);
        new ProcessBuilder("dot", "-Tpng", dotFile, "-o", pngFile).start().waitFor();
        System.out.println("Graphe sémantique DDD généré : " + pngFile);
    }
}