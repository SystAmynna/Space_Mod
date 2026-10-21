# Rapport B1 — Rails et temps commun : déterminisme
Statut : RÉUSSI pour les étapes 1 à 4 (hors jeu) ; étape 5 (serveur/client en jeu) NON EXÉCUTÉE
Prototype : `strategic-tests/B1.java` (`java B1.java`), JVM 21.0.12 (Temurin) et JVM 26.0.2.1 sur la même machine ; sorties : jvm21.txt, jvm26.txt, jvm21-accumulation-1e12.txt
Modèle : la position d'une orbite est calculée à partir d'un entier long de ticks ; la phase est `floorMod(ticks + phase, période)`, jamais accumulée ; positions imbriquées étoile > planète > lune > station (périodes 365 jours, 27 jours, 1 heure de jeu).

| Mesure | Résultat |
| --- | --- |
| Accumulation par tick (double) contre modulo exact, orbite lunaire de rayon 380 000 | 10^6 ticks : 1,8e-12 rad (6,7e-7 bloc) ; 10^9 ticks : 2,4e-6 rad (0,91 bloc) |
| Accumulation par tick, 10^12 ticks (exécutée en entier) | double : 2,77 rad d'écart (1,05 million de blocs, orbite complètement fausse) ; float : 1,04 rad (397 000 blocs) |
| Accumulation par tick (float 32 bits) | 10^6 ticks : 4,8e-4 rad (182 blocs) ; 10^9 ticks : 1,28 rad (485 000 blocs, orbite fausse) |
| Stabilité du modulo aux grandes valeurs | 10^5 tirages jusqu'à 10^15 ticks : écart maximal 0 entre t et t + k × (multiple commun des périodes) |
| Déterminisme | 64 tâches sur 16 fils : résultats identiques ; empreinte identique entre la JVM 21 et la JVM 26, avec `StrictMath` (05e3acde9b5201f7) et avec `Math` (728881fb767a4b21) sur cette machine |
| Serveur contre client en jeu après 1 h avec `/time set`, `/tick freeze`, `/tick sprint` | NON EXÉCUTÉ (la couche stratégique n'existe pas encore dans le mod) |

Critères : modulo stable aux grandes valeurs et résultats identiques d'une exécution à l'autre : atteints. La dérive de l'accumulation est croissante, ce qui confirme que la phase doit rester un modulo entier.
Réserves : l'égalité des empreintes avec `Math` n'est prouvée que sur une même architecture (amd64) ; `StrictMath` est le choix sûr entre machines. L'écart serveur/client (< 1e-9 relatif) n'a pas pu être mesuré hors du jeu : les deux calculs partagent le même code ici.
Conséquence pour l'architecture : rien à rouvrir ; imposer le temps entier (long), le modulo exact et `StrictMath` pour les positions partagées.


## Non testé / reste à faire (état au 20/10/2026)
- Étape 5 (serveur contre client en jeu après 1 h avec `/time set`, `/tick freeze`, `/tick sprint`) : NON EXÉCUTÉE ; la couche stratégique n'existe pas dans le mod.
- Égalité des empreintes avec `Math` entre architectures différentes (hors amd64) : non testée.
- Écart serveur/client < 1e-9 relatif : non mesurable hors du jeu.
