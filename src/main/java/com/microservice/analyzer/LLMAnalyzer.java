package com.microservice.analyzer;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;

/**
 * Integre un LLM (GPT-4o-mini via OpenRouter) pour analyser l'architecture
 * et resoudre les ambiguites de polymorphisme detectees par TaskIdentifier.
 * Maintient un historique de conversation pour des questions de suivi coherentes.
 */
public class LLMAnalyzer {

    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL   = "openai/gpt-4o-mini";

    // Cle lue depuis la variable d'environnement
    private static final String API_KEY = System.getenv("OPENROUTER_KEY");

    private final List<RubyParser.RubyClass> classes;
    private final CallGraph callGraph;
    private final List<TaskIdentifier.Task>  tasks;

    private final List<JSONObject>    chatHistory = new ArrayList<>(); // historique multi-tours
    private final Map<String, String> cache       = new HashMap<>();   // evite les appels redondants

    public LLMAnalyzer(List<RubyParser.RubyClass> classes,
                       CallGraph callGraph,
                       List<TaskIdentifier.Task> tasks) {
        // Bloquer le demarrage si la cle est absente
        if (API_KEY == null || API_KEY.trim().isEmpty()) {
            throw new IllegalStateException(
                    "\n================================================================\n" +
                            "ERREUR : La cle API 'OPENROUTER_KEY' est absente.\n" +
                            "Configurez-la avant execution :\n" +
                            "  export OPENROUTER_KEY=\"votre_cle_openrouter_ici\"\n" +
                            "================================================================"
            );
        }
        this.classes   = classes;
        this.callGraph = callGraph;
        this.tasks     = tasks;
    }

    /**
     * Construit le contexte envoye au LLM lors du premier message.
     * Inclut : classes, heritage, graphe d'appels et classification des taches.
     */
    private String buildContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("CONTEXTE ARCHITECTURAL (Ruby on Rails):\n");

        sb.append("Classes detectees (").append(classes.size()).append("):\n");
        for (RubyParser.RubyClass c : classes) {
            sb.append("- ").append(c.name);
            if (c.parent != null) sb.append(" < ").append(c.parent);
            sb.append(" (Methodes: ");
            for (RubyParser.RubyMethod m : c.methods)
                sb.append(m.name).append(" ");
            sb.append(")\n");
        }

        sb.append("\nGraphe d'appels (XTA):\n");
        for (CallGraph.Edge e : callGraph.getEdges())
            sb.append("  ").append(e.from).append(" -> ").append(e.to).append("\n");

        sb.append("\nTaches identifiees:\n");
        for (TaskIdentifier.Task t : tasks)
            sb.append("- ").append(t.signature)
                    .append(" [").append(t.type).append("]")
                    .append(t.inCycle ? " (En Cycle)" : "").append("\n");

        return sb.toString();
    }

    /**
     * Envoie une question au LLM et retourne sa reponse.
     *
     * Premier appel : injecte le contexte du projet + un message systeme expert.
     * Appels suivants : question seule, le LLM se souvient du contexte precedent.
     * Les reponses sont mises en cache pour eviter les appels dupliques.
     */
    public String ask(String userQuery) {

        // Retourner depuis le cache si la meme question a deja ete posee
        if (cache.containsKey(userQuery)) return cache.get(userQuery);

        try {
            // Initialisation de la conversation au premier appel
            if (chatHistory.isEmpty()) {

                // Message systeme : definit le role expert du LLM
                chatHistory.add(new JSONObject()
                        .put("role", "system")
                        .put("content",
                                "Tu es un expert en architecture logicielle specialise dans la " +
                                        "migration de monolithes Ruby on Rails vers des microservices. " +
                                        "Analyse les graphes d'appels, identifie le couplage, repere le " +
                                        "polymorphisme et suggere des decoupages en domaines (DDD)."));

                chatHistory.add(new JSONObject()
                        .put("role", "user")
                        .put("content", buildContext()));

                chatHistory.add(new JSONObject()
                        .put("role", "assistant")
                        .put("content", "Contexte recu. Pret a analyser l'architecture."));
            }

            chatHistory.add(new JSONObject()
                    .put("role", "user")
                    .put("content", userQuery));


            JSONObject body = new JSONObject();
            body.put("model", MODEL);
            body.put("messages", new JSONArray(chatHistory));
            body.put("temperature", 0.2);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200)
                return "Erreur API (Status " + response.statusCode() + "): " + response.body();

            // Extraction de la reponse textuelle
            String reply = new JSONObject(response.body())
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

            // Ajout de la reponse a l'historique et au cache
            chatHistory.add(new JSONObject().put("role", "assistant").put("content", reply));
            cache.put(userQuery, reply);
            return reply;

        } catch (Exception e) {
            return "Echec de la communication avec le LLM : " + e.getMessage();
        }
    }

    /**
     * Extrait et affiche les noms de services mentionnes dans la reponse du LLM.
     */
    private void visualizeMicroservices(String responseText) {
        System.out.println("=".repeat(40));
        System.out.println(" MICROSERVICES SUGGERES");
        System.out.println("=".repeat(40));

        Set<String> detectedServices = new TreeSet<>();
        Matcher m = Pattern.compile(
                "(?i)(?:service|microservice)\\s+([a-zA-Z]+)",
                Pattern.MULTILINE).matcher(responseText);

        while (m.find()) {
            String s = m.group(1).trim();
            // Filtrer les mots grammaticaux qui ne sont pas des noms de services
            if (s.length() > 3
                    && !s.equalsIgnoreCase("des")
                    && !s.equalsIgnoreCase("les")
                    && !s.equalsIgnoreCase("pour"))
                detectedServices.add(s);
        }

        if (detectedServices.isEmpty()) {
            // afficher les lignes a tiret contenant "service" ou "gestion"
            for (String line : responseText.split("\n"))
                if (line.trim().startsWith("-")
                        && (line.toLowerCase().contains("service")
                        || line.toLowerCase().contains("gestion")))
                    System.out.println("   " + line.trim().substring(1).trim());
        } else {
            detectedServices.forEach(s ->
                    System.out.println("  [SERVICE] " + s.toUpperCase() + "SERVICE"));
        }

        System.out.println("=".repeat(40) + "\n");
    }

    /** Lance l'analyse complete : microservices candidats puis justification du plus complexe. */
    public void print() {
        System.out.println("\n=== ANALYSE LLM ===");

        String res1 = ask("Quels microservices candidats identifies-tu ?");
        System.out.println("\n[LLM] Proposition d'Architecture :");
        System.out.println(res1);
        visualizeMicroservices(res1);

        String res2 = ask("Peux-tu justifier le decoupage du service le plus complexe ?");
        System.out.println("\n[LLM] Focus sur la complexite :");
        System.out.println(res2);
    }

    /** Soumet les ambiguites de polymorphisme au LLM pour une resolution et un decoupage global. */
    public void askLLMToResolve(List<String> ambiguities) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("ANALYSE COMPLÈTE REQUISE :\n");
        prompt.append("Nous devons résoudre les collisions de nommage suivantes détectées par le polymorphisme :\n");
        for (String a : ambiguities) {
            prompt.append("- ").append(a).append("\n");
        }

        prompt.append("\nInstructions de découpage architectural (Strictes) :\n");
        prompt.append("1. Ne te limite pas uniquement aux méthodes ambiguës. Conçois des microservices COMPLETS et AUTONOMES.\n");
        prompt.append("2. Associe CHAQUE contrôleur détecté dans le contexte au domaine métier (Bounded Context) correspondant.\n");
        prompt.append("3. Pour chaque microservice proposé, liste explicitement :\n");
        prompt.append("   - Les Contrôleurs inclus\n");
        prompt.append("   - Les Modèles inclus (avec toutes leurs méthodes)\n");
        prompt.append("4. Spécifie le sort des classes de base techniques (ex: ApplicationController, ApplicationRecord) : doivent-elles être dupliquées ou partagées en tant que dépendances d'infrastructure ?\n");
        prompt.append("\nFormate ta réponse de manière structurée avec les sections : 'Analyse des domaines', 'Découpage en Microservices' et 'Traitement de l'infrastructure'.");

        String response = ask(prompt.toString());

        System.out.println("\n" + "=".repeat(40));
        System.out.println(" RESOLUTION GLOBALE ET DECOUPAGE");
        System.out.println("=".repeat(40));
        System.out.println(response);
        System.out.println("=".repeat(40) + "\n");
    }
}