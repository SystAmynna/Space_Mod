# Rapport A7 — Déconnexion d'un joueur et délai de sérialisation
Statut : RÉUSSI (avec une réserve sur le cas d)
Versions : Minecraft 1.21.1, NeoForge 21.1.262, Sable 2.0.6, Madnessverse 1.0.0 ; délai de test 30 s ; intervention manuelle : l'utilisateur, qui est revenu au bloc d'or à chaque cas
Source : a7.log (position brute du joueur 7,7218 / 66,0 / 7,7146 enregistrée à la déconnexion et restituée à l'identique à la reconnexion dans les 4 cas)

| Cas | Journal | Verdict |
| --- | --- | --- |
| a : reconnexion à 11 s | minuteur annulé, Mini toujours en sub-level, non sérialisée, joueur à l'identique | RÉUSSI |
| b : reconnexion à 46 s | sérialisation à 30 s, retrait du conteneur, restauration depuis `mini.nbt` à la reconnexion, joueur replacé à l'identique | RÉUSSI |
| c : arrêt propre du serveur pendant le délai | sérialisation immédiate à l'arrêt, restauration à la reconnexion | RÉUSSI |
| d : `kill -9` pendant le délai | à la reconnexion la Mini n'était pas dans le monde (perdue depuis la dernière sauvegarde de Sable), restaurée depuis `mini.nbt` | RÉUSSI avec réserve : l'état restauré est celui du dernier fichier écrit (cas c), pas du moment de l'arrêt |

Anomalies et réserves :
- `mini.nbt` n'est pas supprimé après restauration : après un arrêt brutal, une Mini ancienne peut réapparaître, et une Mini qui serait aussi présente dans la sauvegarde de Sable pourrait être dupliquée (non observé ici). Le mod devra effacer ou marquer le fichier après restauration, et vérifier l'UUID avant de restaurer.
- Les points de suivi de Sable n'ont pas été mesurés ; le mod a enregistré lui-même la position brute du joueur (en repère du plot), ce qui a suffi à le replacer.
- Plus de 3 s entre l'arrêt (15:46:04) et la sérialisation (15:46:08) dans le cas c : le délai n'était pas écoulé, la sérialisation vient bien de l'arrêt serveur.
Conséquence pour l'architecture : l'enregistrement de la position relative des joueurs par le mod est confirmé nécessaire ; prévoir le nettoyage des données sérialisées après restauration.
Pièces jointes : a7.log, mini.nbt, joueur.txt


## Non testé / reste à faire (état au 20/10/2026)
- Points de suivi de Sable (tracking) non mesurés.
- Suppression/marquage de `mini.nbt` après restauration et vérification d'UUID : non implémentées ni testées (risque de doublon après arrêt brutal, non observé).
- Délai de production (30 s de test) à confirmer par l'utilisateur [MANUEL-DÉCISION].
