# Rapport A1 — DynamicDimensions avec Sable 2.0.6
Statut : ÉCHEC (crash serveur reproductible au déchargement et à la suppression d'une dimension contenant un sub-level)
Date : 2026-10-20, agent Claude (Sonnet 5.5)
Versions : Minecraft 1.21.1, NeoForge 21.1.262, Sable 2.0.6, DynamicDimensions 0.9.1+75e1622e-dirty (le plan visait 0.10.0), Bad Packets 0.8.2 ; commit du mod : 61bf394 + modifications non commitées

## Ce qui a été fait
- Démarrage serveur avec les 3 mods : aucune erreur de mixin (server-boot.log).
- `/spacetest a1` lancé avec 10 cycles prévus ; le serveur plante au cycle 1, donc les cycles 2 à 10 et la mesure de fuite (étape 8) n'ont pas été exécutés.

## Mesures (cycle 1)
| Mesure | Valeur |
| --- | --- |
| Création des 2 dimensions | 13 à 18 ms |
| Conteneur et physique Sable dans chaque niveau | présents |
| Déplacement en 200 ticks, `void` (gravité 0) | 3,8e-7 bloc |
| Déplacement en 200 ticks, `gravity` | 420 blocs (chute) |
| `unloadDynamicDimension(void)` | renvoie true, puis plantage |

## Critères
- Aucune erreur de mixin : OK au démarrage.
- Sub-level immobile dans `void`, en chute dans `gravity` : OK (propriétés de physique appliquées via `DimensionPhysicsData.putPhysics`).
- Sub-level retrouvé après rechargement : NON TESTÉ (plantage avant).
- Aucune fuite après dix cycles : NON TESTÉ.

## Anomalies
1. Plantage serveur reproductible (2 essais, crash-reports dans `crash/`) : NullPointerException `RapierPhysicsPipeline ... "this.scene" is null`, dans `handleChunkSectionRemoval` (via `PhysicsChunkTicketManager.expirePhysicsChunkTickets`) ou dans `prePhysicsTicks`. La scène physique Rapier est libérée à la fermeture du niveau alors que Sable continue de le traiter.
2. Sans ticket de chargement, le sub-level d'une dimension sans joueur disparaissait du conteneur en 200 ticks (essai 1). Le test ajoute un ticket `COMMAND_FORCED`.
3. `createDynamicDimension` renvoie null si l'instance de `DimensionType` est déjà enregistrée : il faut une instance neuve par dimension.
4. API de DynamicDimensions 0.9.1 différente du plan : pas de `DynamicDimensionProperties`.

## Conséquence pour l'architecture
Les dimensions dynamiques ne peuvent pas être déchargées ni supprimées tant qu'elles contiennent des sub-levels, avec ces versions. Pistes (non implémentées, décision à prendre) : retirer les sub-levels et vider la physique avant le déchargement ; tester DynamicDimensions 0.10.0 ou la variante « AR Edition » ; corriger la compatibilité.

## Pièces jointes
a1-run.log, server-boot.log, server-run.log, crash/*.txt

---
# Complément : A1 avec Madnessverse 1.0.0 (remplace DynamicDimensions)
Statut : RÉUSSI (10 cycles, voir a1-run-mv.log, server-run-mv.log)
Versions : Madnessverse 1.0.0 (port d'Infiniverse, MIT), Architectury API 13.0.11 ; DynamicDimensions désactivé (`.jar.disabled`), Bad Packets inutile.

| Critère | Résultat |
| --- | --- |
| Erreur de mixin ou crash | aucun |
| Déplacement du sub-level dans `void` (200 ticks) | 3,8e-7 bloc à chaque cycle (seuil 0,01) |
| Déplacement dans `gravity` | 420 blocs (chute) à chaque cycle |
| Sub-level retrouvé après désenregistrement puis recréation | 10 cycles sur 10 |
| Niveaux chargés après chaque cycle | 3 (base 3) à chaque cycle |
| Durée de création (2 dimensions) / de rechargement | 6 à 21 ms / 8 à 25 ms |

Réserves :
- Madnessverse ne supprime pas les fichiers d'une dimension : le test les supprime lui-même (sinon le sub-level du cycle précédent revient et pousse le nouveau : constaté, 0,14 à 1,4 bloc de déplacement). Le mod devra gérer ce nettoyage pour la Tampon Spatiale.
- Pas d'équivalent de `DynamicDimensionProperties` ni de `unload` distinct de `delete` ; il faut le rappel de chargement pour recréer les dimensions persistantes (à valider en A2).
- Projet jeune (63 téléchargements, 1.0.0 d'août 2026) mais basé sur Infiniverse.
- Le plan nomme DynamicDimensions dans A1 et A2 ; l'écart est à valider par l'utilisateur.


## Non testé / reste à faire (état au 20/10/2026)
- Avec DynamicDimensions 0.9.1 : cycles 2 à 10 et mesure de fuite non exécutés (plantage au cycle 1) ; le plan visait la version 0.10.0, non essayée.
- Avec Madnessverse : le point de rappel de chargement des dimensions persistantes est couvert par A2 ; nettoyage des fichiers d'une dimension supprimée (Tampon) à concevoir dans le mod, non testé.
- Écart du plan (Madnessverse à la place de DynamicDimensions) : décision de l'utilisateur à confirmer [MANUEL-DÉCISION].
