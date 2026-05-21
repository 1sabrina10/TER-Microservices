# Extraction de Microservices par Analyse Hybride (Java & LLM)

Ce projet de recherche et développement (Travaux d'Étude et de Recherche - Master 1 Informatique) implémente une approche hybride pour automatiser la transition d'une architecture monolithique Ruby on Rails vers une architecture en microservices. Le système combine la précision de l'analyse structurelle en Java avec la capacité d'arbitrage sémantique d'un grand modèle de langage (LLM), répondant spécifiquement aux défis posés par le polymorphisme dans les langages à typage dynamique.

## Fonctionnalités principales

* Analyseur syntaxique (AST) : Extraction automatique des classes, de l'arbre d'héritage et des déclarations de méthodes du code source Ruby.
* Détection du polymorphisme : Identification et classification des ambiguïtés de nommage (Polymorphisme Simple, Héritage, Combiné) afin d'isoler les zones de conflit structurel.
* Calcul des scénarios (BFS) : Identification automatique des points d'entrée (degré entrant nul) et exécution d'un parcours en largeur pour évaluer le taux de couverture des flux via un algorithme glouton de couverture maximale.
* Arbitrage sémantique par LLM : Génération d'un contexte applicatif sérialisé et transmission à un LLM (via OpenRouter) pour déterminer les frontières métiers selon les principes du Domain-Driven Design (DDD).
* Gestion d'état et optimisation : Implémentation d'un historique de conversation multi-tours pour les questions de suivi et d'un cache local pour limiter la consommation de jetons.
* Export et visualisation : Génération de graphes au format DOT (Graphviz) mettant en évidence l'état de couplage actuel et les directives de découpage architectural.

## Prérequis

* Java Development Kit (JDK) 17 ou supérieur
* Apache Maven (pour la gestion des dépendances et le build)
* Une clé d'accès à l'API OpenRouter

## Configuration et Sécurité

Pour respecter les standards de sécurité et éviter l'intégration de secrets en dur dans le code source, la clé d'API est lue depuis les variables d'environnement du système.

Configuration sous Linux / macOS :
export OPENROUTER_KEY="votre_cle_openrouter_ici"

Configuration sous Windows (Invite de commandes) :
set OPENROUTER_KEY="votre_cle_openrouter_ici"

Le programme applique un principe de validation immédiate (Fail-Fast) : si la variable d'environnement est absente ou vide, une exception 'IllegalStateException' est levée dès l'initialisation du constructeur.

## Utilisation

Compilation du projet :
mvn clean package

Exécution de l'analyse :
Le point d'entrée principal (Main) prend en paramètre le chemin du répertoire contenant l'application monolithique :
java -jar target/mon-analyseur-1.0.jar /chemin/vers/le/code/source/rails

Branchement conditionnel de l'exécution :
L'orchestrateur adapte son comportement selon les résultats de l'analyse structurelle :
* Si des ambiguïtés sont détectées : Le LLM est sollicité en mode résolution ciblée pour arbitrer spécifiquement chaque conflit de polymorphisme.
* Si aucune ambiguïté n'est détectée : Le LLM est appelé pour produire une évaluation architecturale globale et macroscopique.

## Architecture du Pipeline

Le pipeline de traitement s'articule autour de trois couches logiques successives :

1. Analyse Structurelle (Java) : Cette première couche est chargée d'extraire l'arbre de syntaxe abstraite (AST) du code source pour recenser les classes et les méthodes. Elle exécute ensuite un parcours en largeur (BFS) à partir des points d'entrée identifiés afin de construire les scénarios et mesurer leur taux de couverture. Les résultats de cette phase (graphe d'appels XTA et classifications) sont compilés et sérialisés.

2. Arbitrage Sémantique (LLM / IA) : Le bloc de texte structuré généré par la couche précédente est transmis à l'API du modèle de langage avec son historique de discussion. Le LLM applique un raisonnement basé sur le Domain-Driven Design (DDD) pour analyser la sémantique métier des classes. Il résout les conflits de polymorphisme en proposant des stratégies adaptées (duplication, création d'interfaces partagées ou regroupement).

3. Visualisation Cible (Microservices) : Les directives d'architecture et de refactoring validées par l'arbitrage sémantique sont envoyées au module d'exportation. Ce dernier génère le fichier final au format DOT (Graphviz) mettant en évidence les frontières d'isolation (Bounded Contexts) et la répartition idéale des futurs microservices.

## Informations Académiques

* Cadre : Travaux d'Étude et de Recherche (TER)
* Sujet : Migration d'une architecture logicielle monolithique vers une architecture à base de microservices : étude de cas des langages à typage dynamique
* Niveau : Master 1 Informatique
* Institution : Université de Montpellier
