package com.microservice.analyzer;

import java.io.File;
import java.util.List;


public class Main {

    public static void main(String[] args) throws Exception {

        String projectPath = "/home/user/Documents/migration-architecture-logicielle-monolithique-vers-une-architecture-microservice-taskflow-manager-baseApp";

        File projectDir = new File(projectPath);
        if (!projectDir.exists()) {
            System.err.println("Erreur : repertoire introuvable : " + projectPath);
            return;
        }

        System.out.println("============================================");
        System.out.println(" DEMARRAGE DE L'ANALYSE : " + projectPath);
        System.out.println("============================================\n");

        RubyParser parser = new RubyParser();
        List<RubyParser.RubyClass> classes = parser.parseProject(projectPath);

        System.out.println("=== CLASSES DETECTEES (" + classes.size() + ") ===");
        for (RubyParser.RubyClass c : classes) {
            String parentInfo = (c.parent != null) ? " < " + c.parent : "";
            System.out.println("\n Classe : " + c.name + parentInfo);
            for (RubyParser.RubyMethod m : c.methods) {
                double score = m.getOptionalHeuristic();
                // Afficher le score heuristique uniquement si la methode a des params optionnels
                String heuristicTag = (score > 0)
                        ? String.format(" [Score Opt: %.2f]", score) : "";
                System.out.println("  |_ " + m.toString() + heuristicTag);
            }
        }

        // Etape 2 : Graphe d'appels XTA
        System.out.println("\n Construction du graphe d'appels (XTA)...");
        CallGraph cg = new CallGraph(classes);
        cg.print();

        //  Etape 3 : Identification des taches

        System.out.println("\n Analyse du polymorphisme et des taches...");
        TaskIdentifier ti = new TaskIdentifier(cg, classes);
        List<String> ambiguities = ti.detectPolymorphism();
        ti.print();

        //  Etape 4 : Analyse dynamique

        System.out.println("\n Simulation de scenarios dynamiques...");
        DynamicAnalyzer da = new DynamicAnalyzer(cg, classes);
        da.print();
        da.printMaxCoverage();

        //  Etape 5 : Consultation du LLM
        // Si des ambiguites existent -> resolution ciblee
        // Sinon -> analyse architecturale complete
        System.out.println("\n Consultation du LLM (Architecture & Resolution)...");
        List<TaskIdentifier.Task> tasks = ti.identify();
        LLMAnalyzer llm = new LLMAnalyzer(classes, cg, tasks);

        if (!ambiguities.isEmpty()) {
            System.out.println("\n Ambiguites detectees. Envoi pour resolution LLM...");
            llm.askLLMToResolve(ambiguities);
        } else {
            llm.print();
        }

        // Etape 6 : Export du graphe
        System.out.println("\n Generation du graphe DOT et rendu PNG...");
        try {
            GraphExporter exporter = new GraphExporter(cg, tasks);
            exporter.exportAndRender("output_graph");
        } catch (Exception e) {
            System.err.println(" Erreur Graphviz (verifier si 'dot' est installe) : "
                    + e.getMessage());
        }

        System.out.println("\n============================================");
        System.out.println(" ANALYSE TERMINEE AVEC SUCCES");
        System.out.println("============================================");
    }
}