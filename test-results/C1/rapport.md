# Rapport C1 — Hôte temporaire : deux Minis
Statut : ÉCHEC pour le critère « aucune traversée de coque » (cause non élucidée) ; formation, fusion et scission de groupe RÉUSSIES ; [MANUEL-CLIENT] (deux joueurs) et [MANUEL-VISUEL] NON EXÉCUTÉS
Versions : Minecraft 1.21.1, NeoForge 21.1.262, Sable 2.0.6, Madnessverse 1.0.0 ; JVM 21 ; `CGroup.java` phase C1, `./gradlew runServerDevMods -Pc=1`. Commit : base 61bf394 + modifications non commitées.
Valeurs de test choisies par l'agent (à définir en production) : plafond de vitesse relative entre deux Minis imposé par le mass lock = 50 bloc/s ; coque = plaque de 10×1×10 blocs (1 bloc d'épaisseur) ; gravité nulle.

**1. Passages à vitesse croissante (100 par vitesse : 10 paires × 10 rondes).** Mini B, positions horizontales décalées au hasard de ±3 blocs, vole droit vers la plaque A (libre, de même masse). « Traversée » = le centre de B passe de l'autre côté du centre de A et au-delà de la face opposée (écart vertical < −1, contact = +1).
| Vitesse relative | Traversées complètes sur 100 | Enfoncement moyen minimal (contact = 1,0) |
| --- | --- | --- |
| 5 bloc/s | 70 | −2,2 |
| 20 | 95 | −10,9 |
| 50 (plafond) | 95 | −27,8 |
| 100 | 100 | −56 |
| 200 | 100 | −732 |
| 400 | 100 | −1 452 |
Variantes de diagnostic (5 et 50 bloc/s seulement) : A fixée au monde (mur immobile) : 48 / 100 puis 100 / 100 traversées ; plaques de 3 blocs d'épaisseur : 26 / 100 puis 98 / 100. La trace tick par tick à 5 bloc/s montre un vrai contact (la vitesse relative tombe de 5 à ~0,5 bloc/s quand l'écart atteint 1,0) puis un enfoncement lent jusqu'à l'autre côté. Les collisions entre deux sub-levels existent donc dans Sable 2.0.6 mais sont trop molles dans cette configuration (blocs de pierre, pas de détection continue visible), même à 5 bloc/s et contre un mur fixe : le critère « aucune traversée de coque à la vitesse maximale plafonnée » n'est pas atteint, y compris à 50 bloc/s et avec une coque de 3 blocs.
Réserves : cause non identifiée (réglage de Sable pour les collisions entre sub-levels, raideur du contact, absence de détection continue ou artefact de ma construction par `assemble` : à confirmer avec des vaisseaux réels de Sable avant de conclure sur l'architecture). Il faut probablement soit limiter la vitesse relative bien en dessous de 5 bloc/s pour des coques d'un bloc, soit épaissir les coques, soit empêcher la superposition par le mod (exclusion dans la couche stratégique avant le contact).

**2. Formation, ajout depuis les données, scission (20 cycles).**
- Formation d'un groupe de deux Minis (2 transferts de la Tampon vers l'hôte temporaire + reproduction de la position et de l'orientation relatives) : p50 6,9 ms, p95 9,3 ms.
- Vitesse relative cible écrêtée au plafond de 50 bloc/s (un cycle sur deux demande 80) : dépassement résiduel 0 ; vitesse relative restituée conforme à 1e-3 près.
- Erreur de pose relative 5 ticks après la formation : 2,5 blocs au pire à 50 bloc/s, soit un seul tick de mouvement (2,5 bloc) : le décalage vient de la granularité de la mesure, pas d'une dérive.
- Une Mini en données rejoint le groupe (désérialisation) : p50 3,1 ms, p95 3,5 ms. Scission (une sérialisée en données, une retournée à la Tampon) : p50 3,9 ms, p95 4,3 ms. Plots occupés = 1 après chaque scission (aucun plot fuyant). Tick moyen du serveur pendant les passages : 1,4 à 2,8 ms.
Découvertes pour le mod : (1) la sérialisation conserve l'indice de plot : un transfert vers une dimension où ce plot est déjà pris échoue (« Plot already exists », `fullyLoad` renvoie null) ; le mod doit allouer les plots de façon unique sur toutes les dimensions, ou transférer par recopie de blocs (mécanisme 1 d'A9) ; (2) la vitesse donnée au tick de création est ignorée (déjà connu).
Non testé : deux joueurs réels, saut de pose visible, cohérence visuelle de la coque ([MANUEL-CLIENT], [MANUEL-VISUEL]).
Conséquence pour l'architecture : point à rouvrir sur la tenue des coques en contact (limite de vitesse relative, épaisseur, exclusion avant contact) ; allocation globale de plots.
Pièces jointes : test-results/C1/c1-run.log, c1-run-essai1/2/3.log, variantes/.


## Non testé / reste à faire (état au 20/10/2026)
- Deux joueurs réels, rencontre de deux Minis, sauts de pose visibles, cohérence visuelle de la coque [MANUEL-CLIENT] et [MANUEL-VISUEL] : NON EXÉCUTÉS (hôte temporaire et couche stratégique côté jeu non écrits).
- Cause de la traversée de coque (contact trop mou de Sable 2.0.6 ou construction des plaques ?) : non élucidée ; à retester avec de vrais vaisseaux de Sable et d'autres paramètres de collision.
- Valeurs de test (plafond de 50 bloc/s, mass lock) à remplacer par celles de production [MANUEL-DÉCISION].
- Groupes de plus de deux Minis, et Minis de taille L : non testés.
