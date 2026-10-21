# Rapport C6 — Multijoueur
Statut : PARTIEL (charge simulée côté serveur RÉUSSIE ; clients réels, bande passante, ciel et interpolation NON MESURÉS)
Versions : Minecraft 1.21.1, NeoForge 21.1.262, Sable 2.0.6, Madnessverse 1.0.0 ; JVM 21 ; machine de développement à 32 cœurs ; `CGroup.java` phase C6, `./gradlew runServerDevMods -Pc=6`.
Méthode et limites : AUCUN client, AUCUN réseau. Chaque « joueur » est une Mini S avec un armor stand, répartie entre la Tampon (rencontres : Minis qui se croisent à 15 bloc/s), un croiseur (R = 300) et une planète (R = 2000, terrain plat chargé sur 17×17 chunks) ; les Minis d'hôte franchissent la frontière (amplitude 4M, période 60 ticks, M = 15). À 300 ticks un joueur se déconnecte (Mini sérialisée en données), restauré à 900 ticks. 1 200 ticks par palier.
| Joueurs simulés | TPS | Tick moyen / pic du serveur | Transferts de frontière (p50 / p95) | Plots occupés | Sérialisation d'une Mini déconnectée / restauration |
| --- | --- | --- | --- | --- | --- |
| 2 | 20,00 | 1,2 / 4,9 ms | 21 (4,1 / 7,0 ms) | 2 (attendu 2) | 0,9 / 4,2 ms |
| 4 | 20,00 | 1,2 / 1,7 ms | 82 (3,8 / 4,4 ms) | 4 (4) | 0,8 / 3,7 ms |
| 8 | 20,00 | 1,5 / 2,7 ms | 181 (3,4 / 4,4 ms) | 8 (8) | 0,7 / 3,1 ms |
| 16 | 20,00 | 2,1 / 4,7 ms | 402 (3,2 / 3,7 ms) | 16 (16) | 0,6 / 3,4 ms |
Critères mesurables ici : TPS ≥ 19,5 avec 8 joueurs (et jusqu'à 16) : atteint ; plots occupés cohérents, aucune perte : atteint ; déconnexion sans blocage (< 1 ms de sérialisation) : atteint. Le plafond de plots de la Tampon n'est pas défini ; l'occupation relevée (16) est celle attendue.
Non mesuré : bande passante par joueur, interpolation des positions répliquées, ciel de chaque joueur, effet d'une bascule sur les autres joueurs ([MANUEL-CLIENT] avec ≥ 2 joueurs réels et [MANUEL-VISUEL]), temps de tick par dimension (seul le tick global est mesuré), coût de la génération/chargement client.
Anomalies : les essais intermédiaires (c6-run-essai1/2/3.log) montraient des transferts refusés (« Plot already exists ») : défaut de mon harnais, qui assignait deux fois le même plot, mais révélateur d'une contrainte du mod (voir C1 : les plots doivent être uniques sur toutes les dimensions, et un plot libéré est immédiatement réattribué à la Mini suivante).
Conséquence pour l'architecture : rien à rouvrir côté charge serveur ; ajouter l'allocation globale de plots.
Pièces jointes : test-results/C6/c6-run.log et essais.


## Non testé / reste à faire (état au 20/10/2026)
- Clients réels (≥ 2 joueurs), bande passante par joueur, interpolation des positions répliquées, ciel de chaque joueur, effet d'une bascule sur les autres joueurs [MANUEL-CLIENT] et [MANUEL-VISUEL] : NON MESURÉS.
- Charge au-delà de 16 joueurs simulés et avec de vraies entités/chunks par joueur : non testée.
- Les « joueurs » du test sont des Minis avec armor stand, sans réseau.
