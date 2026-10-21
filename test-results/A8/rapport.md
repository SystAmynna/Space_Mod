# Rapport A8 — Changement de dimension sans Immersive Portals
Statut : PARTIEL (changement sans écran de chargement obtenu avec la piste 1 ; 1 à 5 images vides subsistent, aucune solution trouvée pour l'instant)
Versions : Minecraft 1.21.1, NeoForge 21.1.262, Sable 2.0.6, Madnessverse 1.0.0 ; intervention manuelle : l'utilisateur (client, serveur dédié local)
Dispositif : deux dimensions identiques a8a et a8b ; une Mini qui voyage avec le joueur (transfert A9 mécanisme 3 corrigé : sérialisation, chargement de la copie, retrait de la source), puis changement de dimension du joueur. Journaux : client-a8.log (écrans, niveaux, chunks, images longues, sub-levels clients), server-a8.log.

| Mode | Résultat |
| --- | --- |
| `vanilla` (référence : `changeDimension`) | écran « Réception du niveau » à chaque changement ; image longue de 77 à 93 ms (et jusqu'à 415 ms au premier) ; 14 à 80 chunks chargés à l'arrivée, 453 après ~10 ticks ; le joueur est replacé sur la Mini de destination |
| `noscreen` (vanilla + écran supprimé côté client) | non rapporté par l'utilisateur ; non évalué |
| `nopacket` (piste 1 : retrait puis ajout du joueur dans l'autre niveau, sans paquet de réapparition) | FONCTIONNEL : aucun écran de chargement, aucun plantage, le joueur reste sur la Mini ; durée serveur 0,3 à 1 ms ; défauts : le F3 n'est pas actualisé (dimension affichée inchangée) et la Mini disparaît 2 à 4 images (17 à 36 ms) |
| `nopacket2` (la source n'est retirée qu'après l'arrivée du joueur) | idem `nopacket` : « AUCUN sub-level client » pendant 1 à 5 images (9 à 43 ms) à chaque changement ; pas d'amélioration |

Mesure du trou (journal client, un événement par changement) : « sub-levels clients = AUCUN » puis, 2 à 4 images plus tard, « 1 sub-level, finalisé ». La nouvelle Mini n'apparaît jamais « non finalisée » : elle est dessinable dès qu'elle arrive. Le trou vient donc de l'ordre des paquets de suivi de Sable (arrêt du suivi de l'ancienne Mini avant le début du suivi de la nouvelle, deux messages `ClientboundStopTrackingSubLevelPacket` puis `ClientboundStartTrackingSubLevelPacket`), pas d'un chargement de chunks. Retarder le retrait de la source côté serveur n'y change rien : le client reçoit quand même l'arrêt de suivi, probablement parce que le suivi de Sable est recalculé par niveau et par position du joueur.

Critères du plan : « aucun écran de chargement » atteint avec la piste 1 ; « image figée de moins de 200 ms » atteint (aucune image longue en `nopacket`) ; « le joueur reste sur la Mini » atteint ; l'image vide de 17 à 43 ms est le défaut résiduel (PARTIEL au sens du plan : bascule très brève à masquer). Non exécutés : piste 2 complète (monde client par dimension), piste 3 (préchargement de chunks de destination vers le client), 10 bascules chronométrées dans chaque sens avec avis visuel détaillé, 50 bascules automatiques avec mesure de mémoire, repli Immersive Portals.
Pistes restantes pour supprimer l'image vide (non implémentées) :
1. mixin côté client qui retarde la suppression de la Mini à la réception de l'arrêt de suivi, jusqu'à l'arrivée de la suivante (nécessite deux plots différents côté client : actuellement les deux Minis occupent le même plot [10000, 10000] ; il faudrait occuper le premier plot de chaque dimension par une Mini factice) ;
2. fondu bref au moment du changement (masque le défaut sans le supprimer) ;
3. faire suivre la Mini de destination par le client avant l'arrivée du joueur (invasif, dépend du suivi par position de Sable) ;
4. repli prévu par l'architecture : Immersive Portals, ou un fork limité aux fonctions de changement de monde client.
Anomalies : F3 non actualisé en `nopacket` (le niveau client n'est pas remplacé : la dimension affichée et d'autres données restent celles de l'ancienne dimension ; à surveiller pour le ciel, les shaders et la physique côté client). Un défaut de mon code (donnée utilisateur de la Mini nulle) a fait échouer les premiers essais de transfert ; corrigé.
Conséquence pour l'architecture : le changement de dimension sans écran de chargement est faisable avec une dépendance nulle à Immersive Portals ; le coût résiduel est une image vide de moins de 50 ms à masquer ; A10 (shaders) peut être préparé avec le mode `nopacket`.
Pièces jointes : client-a8.log, server-a8.log


## Non testé / reste à faire (état au 20/10/2026)
- Mode `noscreen` : non rapporté par l'utilisateur, non évalué.
- Image vide de 1 à 5 images (17 à 43 ms) : non résolue ; pistes 1 à 4 du rapport non implémentées (retarder la suppression côté client, fondu, suivi anticipé, Immersive Portals).
- Piste 3 (préchargement des chunks de destination) et test automatique à 50 changements avec mesure mémoire : non codés.
- Ciel, ombres et physique côté client après `nopacket` (F3 non actualisé) : non vérifiés.
- Cas à plusieurs joueurs, et changement avec shaders hors des scénarios d'A10 : non testés.
