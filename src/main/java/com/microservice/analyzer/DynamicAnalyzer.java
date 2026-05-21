package com.microservice.analyzer;

import java.util.*;

/**
 * Analyse dynamique simulee du projet Ruby.
 * Genere des scenarios de couverture a partir du graphe d'appels
 * calcule le jeu minimal de points d'entree pour couvrir un maximum de methodes.
 */
public class DynamicAnalyzer {

    /** Un scenario represente l'execution simulee depuis un point d'entree. */
    public static class Scenario {
        public String id;
        public String entryPoint;
        public Set<String> coveredMethods; // methodes atteignables par BFS
        public int coveragePercent;

        public Scenario(String id, String entryPoint) {
            this.id             = id;
            this.entryPoint     = entryPoint;
            this.coveredMethods = new HashSet<>();
            this.coveragePercent = 0;
        }

        @Override
        public String toString() {
            return "Scenario " + id + " (entree: " + entryPoint + ")" +
                    "\n    Couverture : " + coveragePercent + "%" +
                    "\n    Methodes couvertes : " + coveredMethods;
        }
    }

    private CallGraph callGraph;
    private List<RubyParser.RubyClass> classes;

    public DynamicAnalyzer(CallGraph callGraph, List<RubyParser.RubyClass> classes) {
        this.callGraph = callGraph;
        this.classes   = classes;
    }

    /**
     * Calcule toutes les methodes atteignables depuis un point d'entree par BFS.
     */
    public Set<String> getReachableMethods(String entryPoint) {
        Set<String>   reachable = new HashSet<>();
        Queue<String> queue     = new LinkedList<>();
        queue.add(entryPoint);
        reachable.add(entryPoint);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (CallGraph.Edge e : callGraph.getEdges()) {
                if (e.from.equals(current) && !reachable.contains(e.to)) {
                    reachable.add(e.to);
                    queue.add(e.to);
                }
            }
        }
        return reachable;
    }

    /**
     * Genere un scenario par point d'entree.
     */
    public List<Scenario> generateScenarios() {
        List<Scenario> scenarios   = new ArrayList<>();
        int totalMethods = callGraph.getNodes().size();

        // Identifier les methodes qui sont cibles d'au moins un appel
        Set<String> called = new HashSet<>();
        for (CallGraph.Edge e : callGraph.getEdges()) called.add(e.to);

        // Points d'entree = methodes jamais appelees par d'autres
        Set<String> entryPoints = new HashSet<>(callGraph.getNodes());
        entryPoints.removeAll(called);

        // Fallback si tout le graphe est cyclique
        if (entryPoints.isEmpty()) entryPoints = callGraph.getNodes();

        int i = 1;
        for (String entry : entryPoints) {
            Scenario s = new Scenario("S" + i, entry);
            s.coveredMethods  = getReachableMethods(entry);
            s.coveragePercent = (int) Math.round(
                    s.coveredMethods.size() * 100.0 / totalMethods);
            scenarios.add(s);
            i++;
        }
        return scenarios;
    }

    /**
     * Algorithme glouton de couverture maximale (approximation du Set Cover).
     * A chaque iteration, choisit le point d'entree qui couvre le plus de
     * methodes encore non couvertes, jusqu'a couvrir tout le projet.
     */
    public void computeMaxCoverage() {
        Set<String>  allMethods      = new HashSet<>(callGraph.getNodes());
        Set<String>  uncovered       = new HashSet<>(allMethods);
        List<String> selectedEntries = new ArrayList<>();

        while (!uncovered.isEmpty()) {
            String      bestEntry     = null;
            Set<String> bestReachable = new HashSet<>();

            for (String node : allMethods) {
                Set<String> reachable    = getReachableMethods(node);
                Set<String> newlyCovered = new HashSet<>(reachable);
                newlyCovered.retainAll(uncovered); // intersection avec non couverts
                if (newlyCovered.size() > bestReachable.size()) {
                    bestReachable = newlyCovered;
                    bestEntry     = node;
                }
            }

            if (bestEntry == null) break;
            selectedEntries.add(bestEntry);
            uncovered.removeAll(bestReachable);
        }

        int coverage = (int) Math.round(
                (allMethods.size() - uncovered.size()) * 100.0 / allMethods.size());

        System.out.println("\n=== COUVERTURE MAXIMALE ===");
        System.out.println("Points d'entree optimaux :");
        for (String e : selectedEntries) System.out.println("  - " + e);
        System.out.println("Couverture atteinte : " + coverage + "%");
        if (!uncovered.isEmpty())
            System.out.println("Methodes non atteignables : " + uncovered);
    }

    /** Affiche pour chaque methode si elle est couverte par au moins un scenario. */
    public void printMethodCoverage(List<Scenario> scenarios) {
        System.out.println("\n=== COUVERTURE PAR METHODE ===");
        for (String method : callGraph.getNodes()) {
            boolean covered = scenarios.stream()
                    .anyMatch(s -> s.coveredMethods.contains(method));
            System.out.println("  " + (covered ? "[OK]" : "[--]") + " " + method);
        }
    }

    /** Affiche : scenarios, couverture par methode, couverture max. */
    public void print() {
        System.out.println("\n=== ANALYSE DYNAMIQUE ===");
        List<Scenario> scenarios = generateScenarios();
        System.out.println("Scenarios generes : " + scenarios.size());
        System.out.println();
        for (Scenario s : scenarios) {
            System.out.println(s);
            System.out.println();
        }
        printMethodCoverage(scenarios);
        computeMaxCoverage();
    }

    /** Version simplifiee de la couverture maximale */
    public void printMaxCoverage() {
        Set<String>  allMethods      = new HashSet<>(callGraph.getNodes());
        Set<String>  uncovered       = new HashSet<>(allMethods);
        List<String> selectedEntries = new ArrayList<>();

        while (!uncovered.isEmpty()) {
            String      bestEntry   = null;
            Set<String> bestCovered = new HashSet<>();

            for (String method : allMethods) {
                Set<String> reachable = getReachableMethods(method);
                reachable.retainAll(uncovered);
                if (reachable.size() > bestCovered.size()) {
                    bestCovered = reachable;
                    bestEntry   = method;
                }
            }

            if (bestEntry == null || bestCovered.isEmpty()) break;
            selectedEntries.add(bestEntry);
            uncovered.removeAll(bestCovered);
        }

        System.out.println("=== COUVERTURE MAXIMALE ===");
        System.out.println("Points d'entree suggeres : " + selectedEntries);
        System.out.println("Taux de couverture : "
                + ((allMethods.size() - uncovered.size()) * 100 / allMethods.size()) + "%");
    }
}