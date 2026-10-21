# Rapport B2 — Rattrapage de simulation et états détruits
Statut : RÉUSSI pour l'exactitude ; performance PARTIELLE à un seul fil (1,2 s contre un critère proposé de 1 s), RÉUSSIE en parallèle (0,16 s)
Prototype : `strategic-tests/B2.java` (Java 21, `java B2.java`), sortie : b2-run.log
Hypothèses de test (à valider, [MANUEL-DÉCISION]) : astres sur rails circulaires ; Minis et Micros en ligne droite uniforme, sans attraction des astres (une Mini passant près d'une planète devrait normalement être déviée : hypothèse peut-être trop simple) ; collision = chevauchement de deux sphères à un tick entier ; vitesses petites devant les rayons (au-delà, deux objets rapides peuvent se traverser entre deux ticks, ce que le rattrapage et la référence ignorent tous deux).

| Mesure | Résultat |
| --- | --- |
| Scénarios (a) Mini/astre, (b) Mini/Mini, (c) Mini/Micro, (d) trois objets, (e) aucune collision, sur 1 heure, 1 jour, 1 mois | 15 comparaisons : événements et ticks identiques à la simulation tick par tick |
| Résultats attendus | astre contre Mini : Mini détruite ; Mini contre Mini : les deux détruites ; Mini contre Micro : Mini détruite, Micro oubliée ; (d) : à tick égal, les deux collisions de la Mini 3 sont appliquées (ordre déterministe par identifiant) |
| Validation aléatoire (graine 2026) | 60 scénarios de 10 objets sur 1 jour, 15 événements : 0 divergence |
| Restauration | Mini détruite conservée dans un cimetière avec sa position et sa vitesse au tick de destruction ; Micro oubliée non restaurable (par règle) |
| Performance, 10 000 Minis + 1 000 Micros + 20 astres sur 1 mois | 0,16 s sur 32 cœurs ; 1,2 s avec un seul fil (algorithme en O(n²) sur les paires, aucun index spatial) |

Observations : l'ordre « à tick égal » est une convention du prototype (un objet détruit à ce tick participe encore aux autres événements de ce même tick) ; l'architecture doit la fixer. Le critère de 1 s est atteint en parallèle ; avec un index spatial (grille) le coût tomberait nettement.
Conséquence pour l'architecture : un rattrapage événementiel est exact et rapide ; rien à rouvrir. Les points à trancher : modèle de mouvement (déviation par les astres), convention à tick égal, vitesses maximales (mass lock) pour éviter la traversée.
Non fait : reprise sur le code réel (la phase 1 n'est pas livrée).


## Non testé / reste à faire (état au 20/10/2026)
- Reprise sur le code réel du mod : non faite (phase 1 non livrée).
- Modèle de mouvement avec déviation par les astres, convention à tick égal, vitesses maximales : à trancher [MANUEL-DÉCISION].
- Index spatial (grille) pour le cas à un seul fil : non essayé.
