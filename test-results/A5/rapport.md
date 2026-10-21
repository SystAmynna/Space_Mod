# Rapport A5 — Gel par contrainte fixe et endormissement
Statut : PARTIEL (deux critères en échec : coût proportionnel au nombre de sub-levels, gel non persistant)
Versions : Minecraft 1.21.1, NeoForge 21.1.262, Sable 2.0.6, Madnessverse 1.0.0

| Étape du plan | Résultat |
| --- | --- |
| 1. Gel par contrainte fixe, 5 min, sans tremblement | RÉUSSI, mesuré en A4 : dérive 3e-5, écart-type 0, rotation bloquée |
| 2. Sub-levels posés sur un sol (gravité 11 m/s², défaut de Sable) | immobiles : déplacement 0 sur 400 ticks avec 1 puis 200 sub-levels |
| 2. État de sommeil | non exposé par l'API de Sable (aucune méthode trouvée) : comparaison de coût à la place |
| 2. Coût avec 0 / 1 / 200 sub-levels au repos | tick serveur moyen 0,31 / 0,35 / 1,84 ms (Spark absent : temps de tick total, pas seulement la physique) |
| 3. Réveil | impulsion verticale : pic 0,399 bloc (attendu v²/2g = 0,409), retour au repos ; avec `wakeUp` préalable : identique ; pose d'un bloc : déplacement 0,04 puis repos. Les trois réveillent et se rendorment en moins de 5 s |
| 4. Gel après rechargement de la dimension | ÉCHEC : sub-level retrouvé mais la contrainte fixe est perdue (déplacement de 53 blocs après une impulsion de 10 m/s) |

Critères :
- « Coût quasi constant (moins de 10 % de variation avec 200 contre 1) » : NON ATTEINT au sens strict. Le coût monte d'environ 0,0076 ms par sub-level au repos (+1,5 ms pour 200). C'est faible en absolu mais proportionnel ; à 1 000 Minis posées, environ 7,6 ms par tick. La mesure ne distingue pas la physique du reste (suivi réseau, plots, chargement) : à refaire avec Spark si la décision en dépend.
- Pas de tremblement, réveil sur sollicitation : atteints.

Conséquence pour l'architecture : les contraintes de gel ne sont pas persistées par Sable (ni avec `unload`/recréation de la dimension) : le mod doit les recréer à chaque chargement d'un sub-level (par exemple à la réception du sub-level chargé). Les Minis dormantes posées sous gravité coûtent un peu mais restent stables ; la variante « origine flottante » n'est pas nécessaire à ce stade.
Pièces jointes : a5-run.log, server-run.log, A4/a4-run.log


## Non testé / reste à faire (état au 20/10/2026)
- État de sommeil de Sable : non observable (API non exposée) ; remplacé par une comparaison de coût.
- Persistance du gel après rechargement : ÉCHEC constaté ; la solution (recréer la contrainte à chaque chargement) n'est pas implémentée ni testée.
- Coût à 1 000 Minis posées (extrapolé à ≈ 7,6 ms/tick) : non mesuré directement ; Spark non utilisé.
