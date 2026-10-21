# Rapport B3 — Groupes de rencontre
Statut : RÉUSSI
Prototype : `strategic-tests/B3.java` (Java 21), sortie : b3-run.log ; seuils de test : entrée 100, sortie 150
Résultats :
- 9 scénarios conformes : A rejoint B, C rejoint le groupe, B s'éloigne dans la zone d'hystérésis (le groupe est conservé), B dépasse la sortie (A quitte le groupe), fusion de deux groupes, scission, chaîne A–B–C, objets de systèmes différents aux mêmes coordonnées (jamais groupés).
- Propriétés (graine 99, 40 objets de 2 systèmes, 3000 ticks, marches aléatoires) : 0 divergence entre l'ordre naturel et un ordre mélangé ; 0 groupe contenant deux systèmes ; 1046 changements d'étiquette au total.
- Bruit autour du seuil (gaussien de 3 sur un franchissement lent de 130 à 70 et retour) : 848 changements de groupe sans hystérésis (E = X = 100), 1 seul avec hystérésis.
Observations :
- Dans le scénario de bruit, la distance ne dépasse jamais la sortie (150) : l'entrée est testée, la sortie l'est par les scénarios déterministes.
- Un groupe est une composante connexe : dans une chaîne A–B–C, A et C font partie du même groupe même s'ils sont éloignés. Si l'on veut des groupes « de proximité directe », il faut une autre définition.
- L'étiquette d'un groupe (plus petit identifiant) change quand le membre qui la portait part : à gérer côté mod.
Conséquence pour l'architecture : rien à rouvrir ; décider si la chaîne A–B–C doit former un seul groupe.
Non fait : reprise sur le code réel.


## Non testé / reste à faire (état au 20/10/2026)
- Reprise sur le code réel : non faite.
- Définition d'un groupe (composante connexe ou proximité directe) et gestion de l'étiquette qui change : à trancher [MANUEL-DÉCISION].
- Cas où la distance dépasse la sortie dans le scénario avec bruit : couvert par les scénarios déterministes seulement.
