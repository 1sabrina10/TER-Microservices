package com.microservice.analyzer;

import java.util.*;

/**
 * Graphe d'appels XTA (eXtended Type Analysis).
 * Modeliser les relations d'invocation entre methodes du projet Ruby.
 * Chaque noeud est une signature "Classe.methode", chaque arete est un appel.
 */
public class CallGraph {

    /** Represente un appel entre deux methodes : from -> to. */
    public static class Edge {
        public String from;
        public String to;

        public Edge(String from, String to) {
            this.from = from;
            this.to   = to;
        }

        @Override
        public String toString() {
            return from + " -> " + to;
        }
    }

    private Set<String> edgeSet = new HashSet<>(); // evite les doublons d'aretes
    private List<Edge>  edges   = new ArrayList<>();
    private Set<String> nodes   = new HashSet<>();  // toutes les signatures connues
    private List<RubyParser.RubyClass> classes;

    public CallGraph(List<RubyParser.RubyClass> classes) {
        this.classes = classes;
        build();
    }

    /** Ajoute une arete uniquement si elle n'existe pas deja. */
    private void addEdge(String from, String to) {
        String key = from + "->" + to;
        if (!edgeSet.contains(key)) {
            edgeSet.add(key);
            edges.add(new Edge(from, to));
        }
    }

    /**
     * Construit le graphe en deux passes :
     * 1) Indexation de toutes les signatures connues dans le projet.
     * 2) Resolution de chaque appel detecte vers une signature cible.
     */
    private void build() {

        // Passe 1 : indexer toutes les methodes connues
        Map<String, RubyParser.RubyMethod> methodIndex = new HashMap<>();
        for (RubyParser.RubyClass c : classes) {
            for (RubyParser.RubyMethod m : c.methods) {
                nodes.add(m.getSignature());
                methodIndex.put(c.name + "." + m.name, m);
            }
        }

        // Passe 2 : pour chaque appel detecte, chercher la methode cible
        for (RubyParser.RubyClass c : classes) {
            for (RubyParser.RubyMethod caller : c.methods) {
                for (String call : caller.calls) {

                    if (nodes.contains(call)) {
                        // Cible trouvee directement (signature exacte)
                        addEdge(caller.getSignature(), call);
                    } else {
                        // Tentative de resolution : chercher le nom de methode
                        // dans toutes les classes connues (XTA)
                        for (RubyParser.RubyClass targetC : classes) {
                            String targetSig = targetC.name + "."
                                    + call.substring(call.indexOf(".") + 1);
                            if (nodes.contains(targetSig)) {
                                addEdge(caller.getSignature(), targetSig);
                            }
                        }
                    }
                }
            }
        }
    }

    public List<Edge>  getEdges() { return edges; }
    public Set<String> getNodes() { return nodes; }

    /**
     * Detecte les cycles dans le graphe par DFS.
     */
    public List<List<String>> detectCycles() {
        List<List<String>> cycles  = new ArrayList<>();
        Set<String>        visited = new HashSet<>();
        List<String>       path    = new ArrayList<>();

        for (String node : nodes) {
            if (!visited.contains(node))
                detectCyclesDFS(node, path, visited, cycles);
        }
        return cycles;
    }

    /** Parcours en profondeur : si un noeud est deja dans le chemin, cycle detecte. */
    private void detectCyclesDFS(String node, List<String> path,
                                 Set<String> visited,
                                 List<List<String>> cycles) {
        if (path.contains(node)) {
            // Extraire uniquement la portion cyclique du chemin
            int idx = path.indexOf(node);
            List<String> cycle = new ArrayList<>(path.subList(idx, path.size()));
            if (!cycles.contains(cycle)) cycles.add(cycle);
            return;
        }
        if (visited.contains(node)) return;

        path.add(node);
        for (Edge e : edges)
            if (e.from.equals(node))
                detectCyclesDFS(e.to, path, visited, cycles);
        path.remove(path.size() - 1);
        visited.add(node);
    }

    /**
     * Retourne les feuilles du graphe : methodes sans aucun appel sortant.
     */
    public Set<String> getLeaves() {
        Set<String> callers = new HashSet<>();
        for (Edge e : edges) callers.add(e.from);

        Set<String> leaves = new HashSet<>(nodes);
        leaves.removeAll(callers);
        return leaves;
    }

    public void print() {
        System.out.println("\n=== GRAPHE D'APPELS (XTA) ===");
        System.out.println("Noeuds : " + nodes.size());
        System.out.println("Aretes : " + edges.size());
        System.out.println("\nRelations d'appels :");
        for (Edge e : edges)
            System.out.println("  " + e);
        System.out.println("\nFeuilles (taches primitives candidates) :");
        for (String leaf : getLeaves())
            System.out.println("  |_ " + leaf);
    }
}