# Rapport B4 — Chargement et déchargement d'un système stellaire
Statut : RÉUSSI (après correction d'un décalage d'un tick dans le prototype)
Prototype : `strategic-tests/B4.java` (Java 21), sortie : b4-run.log ; délai de déchargement 1200 ticks (60 s)
Anomalie corrigée : première version : durée de rattrapage de 3301 ticks au lieu de 3300 (le tick de déchargement n'était pas compté comme dernier tick simulé). Corrigé dans le prototype ; le critère attendu n'a pas été modifié.
Résultats : premier joueur (chargement, rattrapage une fois, durée depuis le début du monde) ; départ puis déchargement exactement 1200 ticks plus tard (pas à 1199) ; reconnexion avant le délai (déchargement annulé, aucun deuxième chargement) ; retour après déchargement (rattrapage = durée exacte depuis le déchargement) ; deux joueurs dans deux systèmes ; saut d'un système à l'autre (arrivée chargée au moment du saut, départ encore chargé pendant le délai) ; arrêt du serveur pendant le délai (déchargé une fois, rattrapage de 0 au redémarrage car l'horloge de jeu est arrêtée) ; arrivées multiples (un seul chargement) ; 2000 séquences aléatoires de 60 actions : 0 violation des invariants.
Observation : le rattrapage d'un redémarrage suppose que le temps de jeu ne court pas pendant l'arrêt ; si l'on veut qu'il court (temps réel), il faudra fixer ce choix.
Conséquence pour l'architecture : rien à rouvrir.
Non fait : reprise sur le code réel.


## Non testé / reste à faire (état au 20/10/2026)
- Reprise sur le code réel : non faite.
- Choix « le temps court-il pendant l'arrêt du serveur ? » : à trancher [MANUEL-DÉCISION].
- Chargement réel des chunks et entités d'un système : non testé (prototype hors jeu).
