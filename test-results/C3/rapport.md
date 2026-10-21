# Rapport C3 — Frontière d'un hôte avec la marge M
Statut : RÉUSSI (serveur dédié, sans client)
Versions : Minecraft 1.21.1, NeoForge 21.1.262, Sable 2.0.6, Madnessverse 1.0.0 ; JVM 21 ; `CRunner.java` phase C3, `./gradlew runServerDevMods -Pc=3`. Commit : base 61bf394 + modifications non commitées.
Valeurs de test choisies par l'agent (à remplacer en production) : R = 300 blocs (croiseur, sphère) ; tore : tube de rayon 300, rayon majeur 1200 ; M = 15 = rayon de la Mini L (20×5×20 blocs) arrondi, soit la formule du plan (« M égale au rayon de la Mini »), appliquée à la plus grande Mini admise ; hôte à 5 bloc/s ; période d'oscillation 30 ticks. Une Mini S (avec un armor stand à bord) sert de passager ; les décisions d'entrée/sortie viennent d'une simulation stratégique sinusoïdale, les transferts sont réels (sérialisation et rechargement de l'A9, mécanisme 3) entre une dimension hôte et la Tampon, de même plage verticale.

| Série | Transferts | Seuils violés | Passager perdu | Contrôles de vitesse stratégique | Durée de transfert p50 / p95 / max |
| --- | --- | --- | --- | --- | --- |
| amplitude M/2 (sphère) | 0 | 0 | 0 | 0 | |
| amplitude 0,99 M | 0 | 0 | 0 | 0 | |
| amplitude 1,1 M | 19 | 0 | 0 | 19 | 5,1 / 8,5 / 62 ms |
| amplitude 2M | 99 | 0 | 0 | 99 | 3,7 / 4,7 / 5,3 ms |
| amplitude 4M (1 000 franchissements) | 999 | 0 | 0 | 999 | 3,1 / 4,1 / 8,7 ms |
| tore, amplitude 4M | 199 | 0 | 0 | 199 | 3,0 / 3,9 / 6,9 ms |

- Aucun transfert sous l'amplitude M ; entrée à d ≤ R − M et sortie à d ≥ R + M vérifiées à chaque transfert, pour la sphère et le tore (distance au tube).
- Vitesse stratégique (vitesse de l'hôte + vitesse locale) : écart nul (au sens du flottant) avant/après chacun des 1 315 franchissements, car le mod réapplique la vitesse 2 ticks après le rechargement (contre le facteur 0,9 de Sable). Critère 1e-6 atteint.
- Fuites : plots occupés identiques (hôte 0, Tampon 1) à la fin de chaque série ; mémoire (après GC) 286 → 314 Mo sur ~23 000 ticks et 1 300 transferts, avec un plateau (essai précédent : 297 → 326 Mo) : pas de croissance continue constatée, mais la durée est courte.
Limites : (1) la position de la Mini est celle de la simulation, la Mini physique est replacée à chaque transfert, donc la continuité de position n'est pas mesurée sur le corps physique ; (2) l'oscillation à exactement M (amplitude 1,0·M) n'est pas échantillonnée à la valeur exacte, d'où les séries 0,99 M et 1,1 M ; (3) pas de joueur à bord : le rendu client des franchissements relève d'A8 (PARTIEL) ; (4) la formule M = rayon de la Mini convient pour une sphère mais, pour une Mini qui tourne ou une forme à courbure forte (tore de tube étroit), la marge doit porter sur le rayon circonscrit de la plus grande Mini admise, ce que fait ce test.
Conséquence pour l'architecture : rien à rouvrir ; la mécanique d'hystérésis tient avec Sable ; durée de transfert (≈ 3–5 ms) compatible avec le budget d'un tick.
Pièces jointes : test-results/C3/c3-run.log, c3-run-essai1-sans-compteur.log.


## Non testé / reste à faire (état au 20/10/2026)
- Frontière avec un joueur réel à bord et mesure côté client [MANUEL-CLIENT] : non exécutées.
- Hôtes aux formes autres que la sphère et le tore : non testés.
- Valeurs de test (R, M) à redéfinir en production [MANUEL-DÉCISION].
