# Rapport A4 — Position verrouillée, rotation libre
Statut : RÉUSSI (partie automatique ; avec joueur à bord non testé : [MANUEL-VISUEL] facultatif)
Versions : Minecraft 1.21.1, NeoForge 21.1.262, Sable 2.0.6, Madnessverse 1.0.0 ; commit 61bf394 + modifications non commitées
Méthode : 6 scénarios en parallèle dans `spacemod_test:a4` (gravité 0), 6000 ticks chacun, contrainte `GenericConstraintConfiguration(null, sub)` avec LINEAR_X/Y/Z verrouillés, ancre = point de rotation de la pose.

| Scénario | Dérive max | Écart-type | Rotation |
| --- | --- | --- | --- |
| S, impulsion linéaire (10 m/s) | 1,1e-5 | 0 | aucune (normal) |
| S, couple | 3,2e-6 | 0 | libre, pic ω 2,71 rad/s |
| S, impulsion + couple | 1,8e-5 | 0 | libre |
| S gelée (contrainte fixe), impulsion + couple | 3,1e-5 | 0 | bloquée (angle 0) |
| L (2000 blocs), impulsion + couple | 1,9e-5 | 1,7e-12 | libre, pic ω 0,46 |
| S + 20 armor stands, impulsion + couple | 3,1e-5 | 0 | libre |

Seuils : dérive < 0,01, écart-type < 0,001 : respectés (marge d'au moins 300). Temps de tick serveur moyen 0,78 ms.
Observations : la rotation n'est jamais amortie (aucune traînée, ω reste constant sur 5 min) : il faudra un amortissement si on le souhaite. La contrainte fixe rend aussi le test A5 (gel) : aucun tremblement. L'ancre n'a pas été placée explicitement hors de la grille de plots : la validation de Sable ne l'a pas refusée.
Non fait : joueur qui marche dessus (manuel) ; mesure Spark (non installé, remplacée par le temps de tick serveur).
Pièces jointes : a4-run.log, server-run.log


## Non testé / reste à faire (état au 20/10/2026)
- Joueur marchant sur la Mini pendant la rotation libre [MANUEL-VISUEL] : non testé (facultatif).
- Mesure Spark : non faite (Spark absent du banc d'essai au moment du test), remplacée par le temps de tick serveur.
- Amortissement de la rotation : non implémenté (aucune traînée).
