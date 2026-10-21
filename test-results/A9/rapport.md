# Rapport A9 — Transfert de sub-level entre dimensions
Statut : RÉUSSI pour les mécanismes 1 et 3 (après correction du mécanisme 3) ; mécanisme 2 : BLOQUÉ
Versions : Minecraft 1.21.1, NeoForge 21.1.262, Sable 2.0.6, Madnessverse 1.0.0 ; serveur dédié de dev, 32 cœurs, aucun client connecté
Méthode : 20 transferts aller-retour entre deux dimensions de même hauteur (-64..320), Minis S (100 blocs, 5 coffres, 1 armor stand, `user_data`) et L (2000 blocs, 50 coffres, 1 armor stand), vitesse 2 m/s ; durées sur le fil serveur. Le mécanisme 3 est celui retenu par l'utilisateur.

## Résultat du mécanisme 3 corrigé (sérialisation et rechargement, ordre « charger puis retirer »)
| Taille | p50 | p95 | Intégrité après 20 transferts |
| --- | --- | --- | --- |
| S | 3,9 ms | 8,1 ms | blocs 100, coffres 5, items 320, armor stand 1, `user_data` identique, identifiant conservé, vitesse 2,0 restituée |
| L | 4,0 ms | 5,1 ms | blocs 2000, coffres 50, items 3200, armor stand 1, `user_data` identique, identifiant conservé, vitesse 2,0 restituée |
Critères (S p95 < 25 ms, L p95 < 50 ms, intégrité complète, échec propre) : atteints.

Corrections apportées au mécanisme 3 (premier essai : PARTIEL) :
1. **Entités** : `SubLevelData` ne contient aucune entité (diagnostic de l'arbre NBT : chunks, pose, bornes, user_data, uuid). L'armor stand n'était donc jamais transféré (et le « restitué » d'A6 était un faux positif). Le mod range désormais les entités du plot dans `user_data` (positions relatives au coin du plot, UUID régénérés à l'arrivée), les recrée dans la destination et supprime les anciennes.
2. **Vitesse** : Sable la multiplie par 0,9 au rechargement (2,0 → 0,24 après 20 transferts) ; le mod la réapplique quelques ticks plus tard (une impulsion donnée au tick de création est ignorée).
3. **Ordre** : « retirer puis charger » perd la source si le chargement échoue (constaté : destination absente, source perdue) ; « charger puis retirer » la conserve. L'ordre sûr est obligatoire.
4. **Plage verticale** : une dimension d'arrivée de hauteur différente donne soit une exception (`ArrayIndexOutOfBoundsException`, plage 0..128), soit un transfert silencieusement corrompu (plage 0..256, `min_y` différent : 100 blocs conservés mais contenu des 5 coffres perdu, items 320 → 0). Le mécanisme refuse désormais proprement le transfert (source conservée) si `min_y`/hauteur diffèrent de la source. **Contrainte d'architecture : toutes les dimensions qui accueillent des Minis doivent avoir la même plage verticale (-64..320 ici).**

## Mécanisme 1 (copie de blocs) pour comparaison
S : p50 3,5 ms, p95 7,3 ms ; L : p50 15,6 ms, p95 21,3 ms ; contenu identique ; nouvel identifiant à chaque transfert ; accepte les plages verticales différentes (les blocs sont reconstruits) ; gère les armor stands copiés à la main.

## Autres essais
- Échec provoqué (destination absente) : ordre sûr, source conservée ; ordre initial, source perdue.
- 20 aller-retours par cas : aucune boucle, aucun plantage serveur ; le rendu client avec un joueur à bord n'est pas testé ici (voir A8 étape 5).
- Mécanisme 2 (demande n° 1474 de Sable) : non exécuté, accord de l'utilisateur et Docker requis.
Pièces jointes : a9-run.log et a9-run-final-meca3.log (essai final), a9-run-essai1 à 4.log (essais précédents, mécanisme 3 avant corrections), server-run*.log


## Non testé / reste à faire (état au 20/10/2026)
- Mécanisme 2 (demande n° 1474 de Sable) : BLOQUÉ, accord de l'utilisateur et Docker requis [MANUEL-DÉCISION].
- Rendu client avec un joueur à bord pendant un transfert : non testé ici (voir A8).
- Dimensions de hauteurs différentes : refusées par le mécanisme 3 ; mécanisme 1 les accepte ; choix de conception non tranché.
- Contrainte découverte en C1 : plot déjà pris dans la destination (allocation globale unique à prévoir) ; non testée dans ce rapport.
