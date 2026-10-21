# Rapport C2 — Préchargement d'une dimension hôte
Statut : PARTIEL (côté serveur mesuré ; côté client NON mesuré) ; critère de marge ≥ 2 : RÉUSSI pour R ≥ 2000 avec un rayon de préchargement de 16 chunks, ÉCHEC pour le rayon 32 en terrain réaliste généré, et pour R = 300 dans tous les cas réalistes
Versions : Minecraft 1.21.1, NeoForge 21.1.262, Sable 2.0.6, Madnessverse 1.0.0 ; JVM 21 ; machine de développement à 32 cœurs ; `CRunner.java` phase C2, `./gradlew runServerDevMods -Pc=2`.
Valeurs de test choisies par l'agent (à remplacer en production) : R(croiseur) = 300, R(planète) = 2000, R(méga) = 6000, M = 15 ; mass lock v_max(d) = 10 + 0,1·d bloc/s (d = distance au volume logique ; donc 10 bloc/s au contact, ~210 bloc/s au bord du volume d'exclusion pour une planète). Temps disponible = ∫ dd / v_max(d) de R + M à 0, en volant au plafond (cas défavorable).

Chargement serveur (chunks forcés par rayon croissant, temps cumulé depuis la création ou le rechargement de la dimension) :
| Cas | rayon 8 | rayon 16 | rayon 32 |
| --- | --- | --- | --- |
| Terrain plat, généré | 0,22 s | 0,63 s | 2,1 s |
| Terrain plat, déjà sur disque | 0,09 s | 0,28 s | 1,2 s |
| Terrain réaliste (générateur de l'Overworld), généré | 4,3 s | 10,9 s | 33,9 s |
| Terrain réaliste, déjà sur disque | 0,34 s | 1,3 s | 7,0 s |

Marge (temps disponible / temps de chargement du terrain réaliste généré) :
| R | temps disponible | marge rayon 16 | marge rayon 32 |
| --- | --- | --- | --- |
| 300 (croiseur) | 14,2 s | 1,3 | 0,4 |
| 2000 (planète) | 30,5 s | 2,8 | 0,9 |
| 6000 (méga) | 41,1 s | 3,8 | 1,2 |

Variation du coefficient k du mass lock (R = 2000, v = 10 + k·d), marge au rayon 32 / au rayon 16 : k = 0,02 → 2,4 / 7,4 ; 0,05 → 1,4 / 4,4 ; 0,1 → 0,9 / 2,8 ; 0,2 → 0,5 / 1,7 ; 0,5 → 0,3 / 0,8. Marge ≥ 2 : au rayon 16, k ≤ 0,1 (212 bloc/s au bord du volume d'exclusion) ; au rayon 32 en terrain généré, k ≤ ~0,02 (50 bloc/s).
Réserves : (1) le client n'a pas été lancé : la réception et le rendu des chunks côté client s'ajoutent au temps serveur ; (2) une machine à 32 cœurs génère vite : un serveur plus modeste sera plus lent ; (3) le test ne mesure pas l'impact sur les ticks pendant le chargement ; le premier essai (c2-run-essai1.log) forçait tous les chunks d'un coup et ne séparait donc pas les rayons, d'où le balayage du centre vers l'extérieur de l'essai final ; (4) le terrain réaliste utilise le générateur de l'Overworld, que le mod n'utilisera peut-être pas.
Conséquence pour l'architecture : le préchargement doit viser un rayon modeste (≈ 16 chunks) et/ou les planètes doivent pré-générer leurs sites d'atterrissage ; avec un terrain à générer, un croiseur (R = 300) n'offre pas la marge de 2 ; ralentir le mass lock (k ≈ 0,02–0,05) ou précharger plus tôt (au-delà de 2R) sont les leviers.
Pièces jointes : test-results/C2/c2-run.log, c2-run-essai1.log.


## Non testé / reste à faire (état au 20/10/2026)
- Mesure côté client (affichage, chargement des chunks reçus, gel éventuel) : NON MESURÉE [MANUEL-CLIENT].
- Marge ≥ 2 non atteinte au rayon de 32 chunks ni pour le croiseur (R = 300) : décision requise sur le rayon de préchargement, le coefficient k du mass lock ou le moment du préchargement [MANUEL-DÉCISION].
- Terrain d'autres dimensions que l'Overworld (mondes personnalisés, structures lourdes) : non mesuré.
- Valeurs de R de test à redéfinir en production.
