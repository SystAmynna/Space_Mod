# Rapport C4 — Voyage entre systèmes : saut et parcours
Statut : PARTIEL (parties numériques RÉUSSIES hors jeu ; saut masqué, parcours imperceptible et ciel en jeu NON EXÉCUTÉS)
Prototype : `strategic-tests/C4.java` (`java strategic-tests/C4.java`), sortie : `c4-numerique.txt`. JVM 21. Pas de commit du mod testé (prototype séparé du mod).
Modèle : position = cellule entière (long, 1e6 blocs) + décalage double ; référence exacte en BigDecimal.

| Mesure | Résultat |
| --- | --- |
| Saut : aller-retour de référentiel, 1e5 cas, positions jusqu'à ~1e12 | écart de position 0 |
| Saut : vitesse (norme et vitesse dans le repère du vaisseau), 1e5 cas | écart relatif max 1,1e-15 (critère 1e-6) |
| Saut : norme du quaternion d'orientation | écart max 2,2e-16 |
| Parcours de 1e9 blocs (~8 000 ticks, 1 432 changements de cellule) | écart relatif max 1,3e-16 (1,3e-7 bloc au total) |
| Même parcours avec une seule double sans changement de cellule | 9,8e-14 relatif (suffit aussi pour le critère) |
| Même parcours en float 32 bits | 3,4e-5 relatif (dépasse 1e-6 : à proscrire pour la position stratégique) |
| Mass lock (plafond proposé 0,02 × distance, aucune limite au-delà de 5e6 blocs) | croissant, continu, illimité loin de tout astre |
| Ordre de chargement (modèle logique) | B chargé avant le changement de référentiel, A déchargé après |

Critères numériques (continuité < 1e-6 relatif, aucun plafond loin des astres) : atteints.
Non exécuté : masquage du saut, imperceptibilité du changement de référentiel [MANUEL-VISUEL], mise à jour réelle du ciel et chargement réel du système d'arrivée (rien de tout cela n'existe encore dans le mod). L'ordre de chargement n'est qu'un modèle logique.
Réserves : le mass lock et le plafond de 1e9 blocs sont des valeurs de test de l'agent, à valider par l'utilisateur (section 8, valeur n° 2). Le test du parcours est trivial tant que les pas sont de l'ordre de la précision double ; il vaudra surtout sur le code réel.
Conséquence pour l'architecture : rien à rouvrir ; imposer cellule entière + décalage (ou double seule suffit jusqu'à ~1e9) pour la position stratégique, jamais float.


## Non testé / reste à faire (état au 20/10/2026)
- Masquage du saut, imperceptibilité du changement de référentiel, mise à jour du ciel, chargement réel du système d'arrivée [MANUEL-VISUEL] : NON EXÉCUTÉS (rien n'existe encore dans le mod).
- Ordre de chargement testé sur un modèle logique seulement.
- Représentation de la position stratégique : le float 32 bits est à proscrire (dérive de 3,4e-5) ; le choix définitif reste à confirmer à l'implémentation.
