Extraction de Microservices par Analyse Hybride (Java & LLM)

Ce projet de recherche et développement (Travaux d’Étude et de Recherche - Master 1 Informatique) propose une approche hybride visant à automatiser la transition d’une architecture monolithique Ruby on Rails vers une architecture orientée microservices.

Le système combine la précision de l’analyse structurelle statique en Java avec la puissance d’arbitrage sémantique d’un grand modèle de langage (LLM) afin d’identifier, analyser et proposer une décomposition cohérente du système en services indépendants.

Fonctionnalités principales
Analyseur syntaxique (AST)
Extraction automatique des classes, de l’arbre d’héritage et des signatures de méthodes à partir du code source Ruby.
Détection du polymorphisme
Identification et classification des ambiguïtés de nommage (polymorphisme simple, héritage, combiné) afin de détecter les conflits structurels.
Analyse des scénarios (BFS)
Détection des points d’entrée (degré entrant nul) et exploration des flux via un parcours en largeur pour mesurer la couverture du système.
Arbitrage sémantique par LLM
Génération d’un contexte applicatif structuré et transmission à un LLM (via OpenRouter) pour déterminer les frontières métiers selon les principes du Domain-Driven Design (DDD).
Gestion d’état et optimisation
Historique de conversation multi-tours et mécanisme de cache local pour réduire la consommation de tokens et optimiser les appels API.
Export et visualisation
Génération de graphes au format DOT (Graphviz) représentant les frontières architecturales et les futurs microservices.
Prérequis
Java Development Kit (JDK 17 ou supérieur)
Apache Maven
Clé API OpenRouter
Configuration et sécurité

La clé API n’est jamais stockée en dur dans le code source. Elle est chargée via les variables d’environnement du système.

Linux / macOS
export OPENROUTER_KEY="votre_cle_openrouter_ici"
Windows (CMD)
set OPENROUTER_KEY="votre_cle_openrouter_ici"

En cas d’absence de configuration, le programme applique un principe fail-fast et lève une exception IllegalStateException lors de l’initialisation.

Utilisation
Compilation
mvn clean package
Exécution
java -jar target/mon-analyseur-1.0.jar /chemin/vers/le/code/source/rails
Comportement de l’orchestrateur

Le pipeline adapte dynamiquement son exécution selon les résultats de l’analyse structurelle :

Présence d’ambiguïtés
Le LLM est sollicité pour arbitrer chaque conflit de polymorphisme.
Absence d’ambiguïtés
Le LLM fournit une analyse architecturale globale du système.
Architecture du pipeline
1. Analyse structurelle (Java)

Extraction de l’AST du code source afin d’identifier les classes, méthodes et dépendances.
Un parcours en largeur (BFS) est ensuite appliqué à partir des points d’entrée pour construire les scénarios d’exécution et mesurer la couverture des flux.

2. Arbitrage sémantique (LLM / IA)

Les données structurées sont envoyées à un LLM avec historique conversationnel.
Le modèle applique une analyse basée sur le Domain-Driven Design (DDD) afin d’identifier les frontières métiers et de résoudre les conflits de conception.

3. Visualisation et export

Les décisions architecturales sont traduites en un graphe DOT (Graphviz) représentant :

les bounded contexts
les dépendances entre modules
la décomposition en microservices
Informations académiques
Cadre : Travaux d’Étude et de Recherche (TER)
Niveau : Master 1 Informatique
Institution : Université de Montpellier
