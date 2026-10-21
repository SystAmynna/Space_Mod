# Rapport A2 — Persistance des dimensions (Madnessverse 1.0.0, Sable 2.0.6, NeoForge 21.1.262)
Statut : RÉUSSI (la remise à zéro de la Tampon Spatiale est écartée sur consigne de l'utilisateur)
- Arrêt propre puis redémarrage : `persist` identique (UUID, pose, 105 blocs, 5 coffres, 320 items, bloc repère), `tampon` identique aussi (donc conservée, pas recréée vide).
- Arrêt brutal (`Runtime.halt` après sauvegarde complète) : mêmes résultats.
- Point important : Madnessverse restaure seul les dimensions enregistrées au chargement du monde, avant `ServerStartedEvent`. La physique de dimension (`DimensionPhysicsData.putPhysics`, non persistée) doit donc être enregistrée dès `ServerAboutToStartEvent`, sinon les pipelines Sable se créent avec la gravité par défaut (premier essai : chute de 121 blocs en 100 ticks, y compris pour un sub-level neuf). Dans le vrai mod, utiliser un datapack `dimension_physics` ou cet événement.
- Logs : a2-propre.log, a2-brutal.log.


## Non testé / reste à faire (état au 20/10/2026)
- Remise à zéro de la Tampon Spatiale : écartée sur consigne de l'utilisateur, non testée.
- Enregistrement de la physique dès `ServerAboutToStartEvent` à reprendre dans le mod final ; non testé hors du banc d'essai.
- Sauvegarde en cours d'écriture par Sable elle-même (non contrôlable) : non testée.
