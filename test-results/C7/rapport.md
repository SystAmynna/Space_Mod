# Rapport C7 — Effet d'inertie sur les Minis d'un hôte mobile
Statut : RÉUSSI pour la partie automatique (serveur dédié, sans client) ; [MANUEL-VISUEL] facultatif NON EXÉCUTÉ
Versions : Minecraft 1.21.1, NeoForge 21.1.262, Sable 2.0.6, Madnessverse 1.0.0 ; JVM 21 ; mod de test `spacemod_test` (`CRunner.java`, phase C7), lancé par `./gradlew runServerDevMods -Pc=7`. Commit : base 61bf394 + modifications non commitées.
Valeurs de test choisies par l'agent (en production : à définir) : croiseur R = 300 blocs, M = 15 (rayon de la Mini L, 20×5×20), 1 g = 9,81 bloc/s², vitesse initiale de l'hôte 5 bloc/s. Gravité de Sable nulle dans les dimensions de test. 3 Minis S couplées (contrainte fixe au monde) et 4 Minis S non couplées (dont une couplée en cours d'essai), un armor stand à bord de chacune des non couplées.

Méthode : à chaque tick, variation de vitesse de l'hôte Δv (référentiel stratégique) convertie dans le référentiel de l'hôte (rotation inverse de son orientation), impulsion opposée −Δv·masse appliquée aux Minis non couplées ; la variation mesurée au tick suivant est comparée à −Δv.

| Cas | Mesures | Écart relatif max à la variation attendue (critère 1 %) |
| --- | --- | --- |
| Accélération douce 0,5 g, orientation identité | 156 | 1,0e-6 |
| Accélération forte 5 g, hôte tourné de 0,7 rad (Y) | 156 | 1,2e-6 |
| Freinage 5 g, hôte tourné de 1,1 rad (Z) | 156 | 1,3e-6 |
| Changement de direction (+y, −z), orientations quelconques | 76 + 76 | 6,6e-7 ; 1,5e-6 |
| 10 accélérations répétées ±2,5 g sans sortie | 760 | 6,0e-6 |
| Couplage d'une Mini en cours de variation | 93 | 6,0e-6 ; la Mini couplée (vitesse remise à zéro avant) ne dérive pas (0,00000 bloc) |

- **Minis couplées :** dérive maximale 0,00001 bloc sur tout le test (critère 0,01), y compris pendant les accélérations de 5 g.
- **Éjection en cascade** (accélération 5 g le long de +x jusqu'à R + M = 315) : 3 Minis éjectées aux ticks 66, 71 et 79 ; vitesse stratégique (hôte + locale) avant éjection égale à la vitesse initiale à 3,8e-7 près ; vitesse mesurée dans la Tampon Spatiale après le transfert : écart relatif 2e-8 (critère 1e-6). Durée des transferts 4 à 15 ms.
- **Dimension déchargée pendant la variation :** l'effet est un no-op (renvoie faux, aucune exception) ; testé sur une dimension vide car A1 a montré qu'une dimension contenant un sub-level plante au déchargement.
- **TPS :** temps de tick moyen 3 à 6 ms pendant l'effet (11 ms au premier segment, démarrage), très inférieur au budget de 50 ms.

Anomalies et observations : (1) l'impulsion de vitesse donnée au tick de création d'un sub-level est ignorée (déjà noté en A9) ; mon premier essai a donc mesuré une vitesse nulle dans la Tampon (faute de l'essai, corrigée en donnant la vitesse 2 ticks plus tard). Le mod réel devra faire de même. (2) Après 10 000 ticks d'accélérations, une Mini non couplée peut se trouver à plusieurs milliers de blocs (pose (−1893, −1498, −737) constatée) : en production, l'éjection à R + M l'aurait retirée bien avant. (3) Les écarts de 1e-6 viennent de la précision flottante de la vitesse lue (float).
Non testé : joueur à bord ([MANUEL-VISUEL] facultatif) ; Mini qui se couple avec une vitesse non nulle (couplage testé à vitesse remise à zéro).
Conséquence pour l'architecture : rien à rouvrir. Le principe de la section 10.8 tient avec Sable ; l'effet doit sauter les Minis couplées et ne rien faire si la dimension n'est pas chargée.
Pièces jointes : test-results/C7/c7-run.log, c7-run-essai1.log (premier essai, avec la faute de l'harnais).


## Non testé / reste à faire (état au 20/10/2026)
- Joueur à bord de la Mini pendant l'effet d'inertie [MANUEL-VISUEL] (facultatif) : non testé.
- Couplage d'une Mini avec une vitesse non nulle : testé à vitesse remise à zéro seulement.
- Valeurs de test (accélération, 1 g = 9,81 bloc/s²) à redéfinir en production.
