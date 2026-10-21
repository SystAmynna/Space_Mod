# Synthèse des tests — plan_de_tests.md v1 (état au 20/10/2026)

Versions testées : Minecraft 1.21.1, NeoForge 21.1.262, Sable 2.0.6, Madnessverse 1.0.0, JVM 21. Base du code : commit 61bf394 + modifications non commitées (aucun commit ni push effectué).
Chaque rapport `X/rapport.md` contient : statut, méthode, mesures, conséquence pour l'architecture et, en fin de fichier, la section « Non testé / reste à faire ». Les journaux bruts sont dans le même dossier.

Règles appliquées (§1.1 du plan) : aucun seuil ni architecture modifié pour faire passer un test ; aucun succès déclaré sans mesure ; monde de test jetable ; aucune mise en production.

## Tableau des statuts
| Test | Sujet | Statut | Principal non testé |
| --- | --- | --- | --- |
| A1 | Dimensions dynamiques | ÉCHEC avec DynamicDimensions (crash au déchargement d'une dimension avec sub-level) ; RÉUSSI avec Madnessverse | Écart au plan à confirmer |
| A2 | Persistance des dimensions | RÉUSSI | Remise à zéro de la Tampon (écartée) |
| A3 | Saut de pose avec joueur | PARTIEL (≤ 300 blocs) ; ÉCHEC à 1000 blocs | Cas > 300 blocs, sauts simultanés |
| A4 | Position verrouillée, rotation libre | RÉUSSI (auto) | Joueur à bord |
| A5 | Gel et endormissement | PARTIEL | Gel non persistant ; coût proportionnel |
| A6 | Sérialisation et plots | PARTIEL | Conflit de plot à gérer côté mod |
| A7 | Déconnexion | RÉUSSI avec réserve (cas d) | Nettoyage de `mini.nbt` |
| A8 | Changement de dimension sans écran | PARTIEL (1 à 5 images vides) | Pistes de suppression de l'image vide |
| A9 | Transfert entre dimensions | RÉUSSI (méc. 1 et 3) ; méc. 2 BLOQUÉ | Accord et Docker requis |
| A10 | Shaders | RÉUSSI sur déclaration, mesures incomplètes | FPS avec Mini |
| B1 | Temps commun, déterminisme | RÉUSSI (hors jeu) | Étape 5 en jeu |
| B2 | Rattrapage de simulation | RÉUSSI (perf. PARTIELLE à un fil) | Code réel |
| B3 | Groupes de rencontre | RÉUSSI | Code réel |
| B4 | Chargement/déchargement d'un système | RÉUSSI | Code réel |
| C1 | Hôte temporaire, deux Minis | ÉCHEC (traversée de coque) ; groupe RÉUSSI | Deux joueurs réels |
| C2 | Préchargement d'une dimension hôte | PARTIEL (serveur seul) | Mesure client ; marge non atteinte à 32 chunks et pour R = 300 |
| C3 | Frontière avec marge M | RÉUSSI | Joueur réel |
| C4 | Saut et parcours entre systèmes | PARTIEL (numérique OK) | Saut masqué, ciel, [MANUEL-VISUEL] |
| C5 | Arrêt propre et plantage | RÉUSSI (sans joueur réel) | Joueur à bord |
| C6 | Multijoueur | PARTIEL (charge simulée) | Clients réels, bande passante |
| C7 | Effet d'inertie | RÉUSSI (auto) | Joueur à bord (facultatif) |

## Constats à retenir pour l'architecture
1. Dimensions : DynamicDimensions plante avec un sub-level présent ; Madnessverse fonctionne mais ne supprime pas les fichiers (A1).
2. Les contraintes de gel ne sont pas persistées par Sable : à recréer à chaque chargement (A5).
3. Sable multiplie la vitesse par 0,9 au rechargement ; une impulsion donnée au tick de création est ignorée (A6, A9, C5).
4. `SubLevelData` ne contient pas les entités ; la plage verticale des dimensions doit être identique (A6, A9).
5. L'indice de plot est conservé à la sérialisation : allocation globale unique ou recopie de blocs (A6, C1).
6. Sauts de pose > 300 blocs : limite de Sable, image vide visible (A3). Changement de dimension : image vide de 17 à 43 ms (A8).
7. Position stratégique : entier long et modulo exact, jamais de flottant accumulé ; `StrictMath` entre machines (B1, C4).
8. Préchargement d'un hôte : marge insuffisante pour le croiseur et au rayon de 32 chunks (C2).
9. Collision entre sub-levels : traversée de coque observée, cause non élucidée (C1).

## Valeurs de test (à redéfinir en production)
R(croiseur) = 300, R(planète) = 2000, R(méga) = 6000 ; M = 15 ; mass lock v_max(d) = 10 + 0,1·d bloc/s (plafond de 50 bloc/s entre Minis en C1) ; 1 g = 9,81 bloc/s² ; vitesse de l'hôte 5 bloc/s ; gravité de Sable nulle dans les dimensions de test.

## Reste à faire par l'utilisateur
- [MANUEL-CLIENT] : deux joueurs réels pour C1 et C6 (nécessitent un hôte temporaire et une couche stratégique côté jeu, non écrits) ; joueur à bord pour C5 et C7 ; mesure côté client de C2.
- [MANUEL-VISUEL] : C4 (saut, parcours, ciel).
- [MANUEL-DÉCISION] : valeurs de production de R, M et du mass lock ; rayon de préchargement / coefficient k (C2) ; accord et Docker pour A9 mécanisme 2 ; modèle de mouvement (B2) ; définition d'un groupe (B3) ; temps pendant l'arrêt (B4) ; packs de shaders (A10) ; écart Madnessverse / DynamicDimensions (A1).
- Retester C1 avec de vrais vaisseaux de Sable pour établir la cause de la traversée de coque.
