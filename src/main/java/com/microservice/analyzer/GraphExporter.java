package com.microservice.analyzer;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Genere le graphe des microservices candidats au format DOT (Graphviz)
 * et le rend en PNG. Chaque cluster represente un microservice, chaque
 */
public class GraphExporter {

    private CallGraph callGraph;
    private List<TaskIdentifier.Task> tasks;
    private Map<String, String> colorCache = new HashMap<>(); // couleur par service

    public GraphExporter(CallGraph callGraph, List<TaskIdentifier.Task> tasks) {
        this.callGraph = callGraph;
        this.tasks     = tasks;
    }

    /**
     * Deduit le nom du microservice depuis le nom de classe.
     */
    private String getMicroservice(String className) {
        if (className == null || className.isEmpty()) return "UnknownService";
        return className.replace("Controller", "").replace("Test", "") + "Service";
    }

    /**
     * Genere une couleur pastel unique et reproductible pour chaque microservice,
     */
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

    /**
     * Structure generee :
     *  - Un subgraph cluster par microservice
     *  - Aretes grises : appels internes au meme service
     *  - Aretes rouges pointillees : appels entre services differents (dependances critiques)
     */
    public void exportDot(String outputPath) throws IOException {
        StringBuilder sb = new StringBuilder();

        sb.append("digraph Microservices {\n");
        sb.append("    rankdir=LR;\n"); // disposition gauche -> droite
        sb.append("    graph [fontname=\"Arial\", nodesep=0.5, ranksep=1.0, bgcolor=white];\n");
        sb.append("    node [fontname=\"Arial\", fontsize=10, shape=box, style=\"filled,rounded\"];\n");
        sb.append("    edge [fontname=\"Arial\", arrowsize=0.8];\n\n");

        // Regrouper les taches par microservice
        Map<String, List<TaskIdentifier.Task>> byMs = new LinkedHashMap<>();
        for (TaskIdentifier.Task t : tasks) {
            String ms = getMicroservice(t.className);
            byMs.computeIfAbsent(ms, k -> new ArrayList<>()).add(t);
        }

        // Generer un cluster (subgraph) par microservice
        int idx = 0;
        for (Map.Entry<String, List<TaskIdentifier.Task>> entry : byMs.entrySet()) {
            String ms = entry.getKey();
            sb.append("    subgraph cluster_").append(idx++).append(" {\n");
            sb.append("        label=\"").append(ms).append("\";\n");
            sb.append("        style=\"filled,rounded\";\n");
            sb.append("        fillcolor=\"").append(getDynamicColor(ms)).append("\";\n");
            sb.append("        fontname=\"Arial Bold\";\n");

            // Un noeud par methode dans ce service
            for (TaskIdentifier.Task t : entry.getValue()) {
                sb.append("        \"").append(sanitize(t.signature)).append("\"")
                        .append(" [label=\"").append(t.methodName).append("\", fillcolor=white];\n");
            }
            sb.append("    }\n\n");
        }

        // Generer les aretes depuis le graphe d'appels
        for (CallGraph.Edge e : callGraph.getEdges()) {
            String fromMs = getMicroservice(e.from.split("\\.")[0]);
            String toMs   = getMicroservice(e.to.split("\\.")[0]);

            if (!fromMs.equals(toMs)) {
                // Appel inter-services : rouge pointille
                sb.append("    \"").append(sanitize(e.from)).append("\" -> \"")
                        .append(sanitize(e.to)).append("\" [color=\"#E74C3C\", style=dashed];\n");
            } else {
                // Appel intra-service : gris
                sb.append("    \"").append(sanitize(e.from)).append("\" -> \"")
                        .append(sanitize(e.to)).append("\" [color=\"#95A5A6\"];\n");
            }
        }

        sb.append("}\n");
        Files.write(Paths.get(outputPath), sb.toString().getBytes());
    }

    /** Remplace les caracteres invalides pour les identifiants DOT. */
    private String sanitize(String s) {
        return s.replace(".", "_").replace("-", "_").replace(":", "_");
    }

    /**
     * Exporte le fichier DOT puis lance Graphviz pour generer le PNG.
     */
    public void exportAndRender(String basePath) throws Exception {
        String dotFile = basePath + ".dot";
        String pngFile = basePath + ".png";
        exportDot(dotFile);
        new ProcessBuilder("dot", "-Tpng", dotFile, "-o", pngFile).start().waitFor();
        System.out.println("Graphe genere : " + pngFile);
    }
}