# Rapport A6 — Sérialisation, libération et réallocation des plots
Statut : PARTIEL (un cas de conflit à gérer côté mod)
Versions : Minecraft 1.21.1, NeoForge 21.1.262, Sable 2.0.6, Madnessverse 1.0.0

| Mesure | S (100 blocs, 5 coffres) | L (2000 blocs, 50 coffres) |
| --- | --- | --- |
| Taille des données | 1 023 o compressés (30 872 non compressés) | 1 314 o compressés (76 769 non compressés) |
| Sérialisation | 1,7 ms (1er appel) | 0,7 ms |
| Désérialisation | 20,8 ms (1er appel) | 8,2 ms |
| Contenu restitué (blocs, coffres, items, user_data) | identique | identique |
| Plot libéré au retrait (`REMOVED`) | oui (1 → 0) | oui |
| Vitesse restituée | 2,0 → 1,8 (facteur 0,9 confirmé) | idem |

- 1000 cycles sérialiser/retirer/désérialiser (S) : plots occupés 1 au début et 1 à la fin, aucune fuite. Durées p50/p95 : sérialisation 0,21/0,32 ms, désérialisation 2,04/2,54 ms.
- Conflit : si un autre sub-level a pris l'ancien plot, `SubLevelSerializer.fullyLoad` renvoie `null` sans exception (il ne réalloue pas de plot). Les données restent chez l'appelant donc rien n'est perdu, mais le mod doit tester `null` et réessayer ou choisir un autre plot. Le test l'enregistre comme échec du critère « conflit géré ».
- Pièges constatés : (1) une impulsion appliquée au tick même de l'assemblage est ignorée ; (2) un sub-level désérialisé n'a plus son ticket de chargement : sans joueur à proximité Sable le décharge en quelques ticks (« Body has been removed ») ; le test réapplique `COMMAND_FORCED`.
Pièces jointes : a6-run.log, server-run.log

## Correction ultérieure (constatée en A9)
Le rapport indiquait l'armor stand comme « restitué » : c'est faux. `SubLevelData` ne contient aucune entité (arbre NBT : plot/chunks avec sections, block_entities, heightmaps ; pose ; world_bounds ; user_data ; uuid). Dans ce test l'armor stand restait simplement dans le niveau après le retrait du sub-level, d'où « entités=1 » avant et après. Les entités d'une Mini doivent être sauvegardées et recréées par le mod (voir A9, mécanisme 3 corrigé). Le résultat PARTIEL d'A6 est inchangé ; le critère « entités restituées » n'est pas atteint par Sable seul.


## Non testé / reste à faire (état au 20/10/2026)
- Gestion côté mod du plot déjà pris (retour `null` de `fullyLoad`) : comportement établi, solution (allocation de plots globale ou recopie) testée seulement en C1.
- Persistance des entités : `SubLevelData` n'en contient pas ; le rangement dans `user_data` est testé en A9 seulement.
- Pas de joueur à proximité du sub-level désérialisé : cas de déchargement testé avec ticket forcé uniquement.
