# Rapport A10 — Shaders avec Sable
Statut : RÉUSSI sur déclaration de l'utilisateur (aucun défaut de rendu ni plantage), avec des mesures de performance incomplètes
Versions : Minecraft 1.21.1, NeoForge 21.1.262, Sable 2.0.6, Madnessverse 1.0.0, Sodium 0.8.13+mc1.21.1 (release), Iris 1.8.14-beta.1+mc1.21.1 (bêta), pack de shaders Complementary Reimagined r5.9.3 ; carte graphique AMD Radeon RX 9070/9070 XT (Navi 48) sous Linux ; intervention manuelle : l'utilisateur (client)
Observation au démarrage : la plage Minecraft « [1.21,1.21.1) » déclarée par Iris n'a pas empêché le chargement du mod (les deux mods figurent dans la liste des mods du client) ; la paire stable de repli (Sodium 0.6.13 + Iris 1.8.12) n'a pas été nécessaire. Sodium a signalé un contournement automatique (NO_ERROR_CONTEXT_UNSUPPORTED), sans conséquence rapportée.

## Résultats
- **Rendu avec shaders** : l'utilisateur a rejoué les scénarios d'A3 (Mini, sauts de pose, vol), d'A8 (changements de dimension en mode `nopacket`) et la référence, avec et sans shaders : « aucun changement notable, pas de problèmes, fonctionnel ». Aucun écran noir, ombre manquante, ciel ou texture corrompus, ni plantage n'a été signalé.
- **Mesures d'images par seconde** (`client-a10.log`, 30 s chacune) :

| Scène | Moyenne | 1 % bas | Image la plus longue |
| --- | --- | --- | --- |
| référence sans shaders | 119,0 FPS (8,40 ms) | 95,1 FPS | 15,5 ms |
| référence avec shaders | 119,0 FPS (8,40 ms) | 97,4 FPS | 21,0 ms |

## Réserves
- Seules les deux mesures de la scène de référence ont été enregistrées ; les mesures avec la Mini (sans et avec shaders) ne sont pas dans le journal, donc l'écart de 30 % demandé par le plan entre « scène avec Mini » et « scène de référence » n'est pas mesuré.
- Les moyennes identiques à 119,0 FPS indiquent un plafond (synchronisation verticale ou limite d'images, probablement 120 FPS) : la mesure ne permet pas de voir un coût des shaders. À refaire avec la limite d'images désactivée si le coût compte.
- L'activation réelle du pack de shaders pendant la mesure « avec shaders » n'est pas journalisée (déclaration de l'utilisateur).
- Compatibilité limitée à une paire Sodium/Iris, un pack de shaders et une carte AMD ; l'architecture n'exige pas la prise en charge de tous les packs. La liste des packs et versions à prendre en charge reste à décider par l'utilisateur ([MANUEL-DÉCISION], point 6 de la section 8 du plan).
Conséquence pour l'architecture : rien à rouvrir ; Sable fonctionne avec Sodium 0.8.13 + Iris 1.8.14-beta.1 pour les scénarios testés.
Pièces jointes : client-a10.log


## Non testé / reste à faire (état au 20/10/2026)
- Mesures de FPS avec la Mini (sans et avec shaders) : absentes du journal ; l'écart de 30 % du plan n'est pas évalué.
- Plafond de 119 FPS (synchronisation verticale ou limite d'images) : mesure à refaire sans limite.
- Activation réelle des shaders pendant la mesure : non journalisée (déclaration de l'utilisateur).
- Autres paires Sodium/Iris, autres packs de shaders, autres cartes graphiques : non testés ; liste à décider [MANUEL-DÉCISION].
