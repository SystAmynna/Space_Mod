# Rapport C5 — Arrêt propre et plantage
Statut : RÉUSSI (serveur dédié, sans joueur réel ; [MANUEL-CLIENT] NON EXÉCUTÉ)
Versions : Minecraft 1.21.1, NeoForge 21.1.262, Sable 2.0.6, Madnessverse 1.0.0 ; JVM 21 ; `C5Runner.java` + `scripts/c5.sh`. Commit : base 61bf394 + modifications non commitées.
Méthode : 4 Minis S « occupées » (sub-levels en mouvement à 2 bloc/s dans la Tampon Spatiale, un armor stand à bord) et 3 Minis « en données » (fichier NBT compressé écrit via fichier temporaire puis renommage atomique, sub-level retiré du conteneur). Sauvegarde automatique à 200 ticks (`saveEverything` + écriture atomique de l'index). Cent ticks plus tard : (a) arrêt propre, (b) `kill -9` externe du processus Java, (c) `kill -9` pendant l'écriture d'un fichier de Mini en données (fichier temporaire coupé à mi-écriture). Puis redémarrage et vérification (contrôle fait 100 ticks après le démarrage, pendant lesquels les Minis avancent de ~9 blocs : c'est ce qui explique les « 9 blocs » des écarts bruts).

| Scénario | Minis occupées | Position restaurée (après retrait des 9 blocs de dérive du contrôle) | Minis en données | Doublons / orphelins / plots |
| --- | --- | --- | --- | --- |
| Arrêt propre au tick 300 | 4/4, passager 1/1 | = position à l'arrêt (perte 0, écart brut 9,0) | 3/3 lisibles, identifiant conservé | 0 / 0 / 4 plots (attendu 4) |
| `kill -9` au tick 300 (sauvegarde au tick 200) | 4/4, passager 1/1 | = position de la sauvegarde du tick 200 : perte de 100 ticks (5 s, 10 blocs à 2 bloc/s), soit l'intervalle depuis la dernière sauvegarde | 3/3 lisibles | 0 / 0 / 4 |
| `kill -9` en pleine écriture d'un fichier de Mini | 4/4, passager 1/1, même perte (100 ticks) | idem | 3/3 lisibles ; l'ancien fichier de la Mini visée est intact ; un `.tmp` de 547 octets reste (ignoré par le chargement) | 0 / 0 / 4 |

Critères : arrêt propre sans perte ; plantage avec perte limitée à l'intervalle de sauvegarde, aucune corruption, aucun doublon ni orphelin, plots non occupés inutilement, écriture atomique : atteints.
Anomalies et observations :
1. La vitesse restaurée est de 1,8 bloc/s au lieu de 2 (facteur 0,9 de Sable au rechargement, déjà vu en A9) dans les trois scénarios, y compris l'arrêt propre : le mod doit sauvegarder et réappliquer la vitesse lui-même.
2. Un fichier `.tmp` résiduel après un plantage doit être purgé au démarrage par le mod (non fait ici).
3. Premier essai (c5-run-essai1.log) : script mal orchestré (le `kill` ne tuait pas le serveur, qui a continué 15 min et sauvegardé tout seul) et contrôle sans délai qui comptait 0 passager : les entités du plot ne sont chargées que quelques ticks après le redémarrage (le contrôle doit attendre). Le mod doit donc ne pas conclure à la perte d'un passager immédiatement après le chargement.
4. Les Minis occupées ne sont pas « du mod » : leur persistance est celle de Sable avec la sauvegarde du niveau Madnessverse ; seul l'index et les fichiers des Minis en données sont du code de test.
Non testé : joueur réel à bord ([MANUEL-CLIENT]) ; plantage pendant la sauvegarde de Sable elle-même (non contrôlable) ; Minis en données qui rejoignent un groupe (C1).
Conséquence pour l'architecture : rien à rouvrir ; imposer écriture atomique, réapplication de la vitesse au chargement, purge des `.tmp` au démarrage, attente de quelques ticks avant de juger l'état des entités.
Pièces jointes : test-results/C5/c5-run.log, c5-run-essai1.log, truth.txt, index.txt, minis/.


## Non testé / reste à faire (état au 20/10/2026)
- Joueur réel à bord pendant l'arrêt ou le plantage [MANUEL-CLIENT] (facultatif) : non testé.
- Plantage pendant la sauvegarde de Sable elle-même : non contrôlable, non testé.
- Minis en données qui rejoignent un groupe après redémarrage : traité en C1 seulement.
- Fichier `.tmp` résiduel (547 octets) : purge au démarrage à prévoir, non implémentée.
