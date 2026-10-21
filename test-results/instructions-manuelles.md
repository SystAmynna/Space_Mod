# Instructions pour les tests manuels (A3, A7, A8, A10 prêts ; C1 à C7 : voir la section Phase C en fin de fichier)

Tout se passe dans le dossier du projet. Les journaux sont écrits dans `test-results/`. Les mods (Sable, Madnessverse, Architectury, Spark) sont chargés automatiquement depuis `dev-mods/`.

## Préparation (une fois)
1. Terminal 1, serveur de test : `./gradlew runServerDevMods`. Attendre « Done ». Le mode hors-ligne est actif (`online-mode=false`) et les commandes `/spacetest` ne demandent pas d'être opérateur.
2. Terminal 2, client : `./gradlew runClientDevMods`. Puis dans Minecraft : Multijoueur → Connexion directe → `localhost`.
3. Pour repartir d'un monde propre : arrêter le serveur (`stop` dans le terminal 1) puis supprimer `run-dev-server/world` et `test-results/A3`, `test-results/A7`.
4. Spark est chargé : `/spark tps` donne le TPS et les temps de tick, `/spark profiler start --timeout 60` lance un profil de 60 s (résultat : lien dans le chat).

## A3 : saut de pose d'une Mini portant un joueur (~15 min)
Objectif : vérifier que le joueur reste à sa place sur la Mini quand on téléporte la Mini, sans balayage ni chute.
1. Dans le chat : `/spacetest a3 setup`. Tu arrives sur une Mini de 10×10 blocs avec murs de verre et un coffre. Marche dessus 10 secondes.
2. Exécute, **dans cet ordre**, en laissant 5 s entre chaque, en restant immobile au centre puis (deuxième passe) en marchant en continu :
   - Mini immobile : `/spacetest a3 tp 10 0 0`, puis `/spacetest a3 tp 1000 0 0`, puis `/spacetest a3 tp 5000 0 0`, puis la même série avec `reset` à la fin (ex. `/spacetest a3 tp 10 0 0 reset`).
   - Mini en mouvement : `/spacetest a3 move 5` (5 blocs/s), puis refaire les 6 téléportations ci-dessus (avec et sans `reset`), puis `/spacetest a3 stop`.
3. Séries : `/spacetest a3 batch 20 20 10` (20 téléportations, une par seconde, aller-retour de 10 blocs), puis `/spacetest a3 batch 10 1 10` (10 téléportations, une par tick).
4. Quitte le monde. Lance l'analyse : `python3 -I scripts/analyse_a3.py test-results/A3/client-frames.csv`.
À noter pour chaque téléportation (une ligne par essai dans ta réponse) : le joueur reste-t-il sur le sol ? vois-tu un balayage, un clignotement, une chute, un retour en arrière ? Donne RÉUSSI / PARTIEL / ÉCHEC et, si possible, une capture vidéo.
Fichiers produits : `test-results/A3/server-a3.log` (poses et position relative du joueur côté serveur), `test-results/A3/client-frames.csv` (pose affichée à chaque image).
Critères : joueur à moins de 0,05 bloc de sa position relative, au plus 1 image intermédiaire, aucune éjection.

## A7 : déconnexion d'un joueur seul dans une Mini (~20 min)
Objectif : la Mini d'un joueur parti est sérialisée après 30 s ; à la reconnexion, tout est restauré.
1. Connecté, `/spacetest a7 setup`. Marche jusqu'au bloc d'or (repère). Note tes coordonnées (F3).
2. **Cas a** : déconnecte-toi (Menu → Quitter), attends 10 s environ, reconnecte-toi. Attendu : tu es à côté du bloc d'or, la Mini n'a pas été sérialisée (voir `test-results/A7/a7.log`).
3. **Cas b** : retourne au bloc d'or, déconnecte-toi, attends 45 s, reconnecte-toi. Attendu : la Mini est restaurée depuis les données (`a7.log` : « Mini sérialisée… puis restaurée ») et tu es au bloc d'or.
4. **Cas c** : retourne au bloc d'or, déconnecte-toi, puis arrête le serveur proprement (`stop` dans le terminal 1) moins de 30 s après. Relance le serveur et reconnecte-toi. Attendu : sérialisation immédiate à l'arrêt, Mini restaurée.
5. **Cas d** : retourne au bloc d'or, déconnecte-toi, puis tue le processus du serveur (Ctrl+C ne suffit pas : `kill -9` sur le processus Java) moins de 30 s après. Relance et reconnecte-toi. Attendu : perte limitée à la dernière sauvegarde, pas de Mini dupliquée.
Pour chaque cas, réponds RÉUSSI / PARTIEL / ÉCHEC avec ta position avant la déconnexion et après la reconnexion. Pour tester vite, tu peux réduire le délai : `-Pa7delay=10` au lancement du serveur.
Le serveur n'a pas de commande pour afficher la Mini : si tu tombes dans le vide, c'est un échec (note-le).

## Pas encore prêts (je te redemanderai)
- **A8** (changement de dimension sans écran de chargement) : demande d'écrire des mixins client ; je dois d'abord les construire, je ne te demande rien pour l'instant.
- **A10** (shaders) : dépend d'A8. Quand ce sera prêt : installer Sodium et Iris (versions à me donner) et un pack de shaders.
- **C1, C4, C6, C7** : dépendent d'A3/A8 et de valeurs à fixer (voir ci-dessous).

## Valeurs que j'attends de toi (section 8 du plan)
1. R (volume logique) et formule de M pour les hôtes de test.
2. Vitesse maximale du mass lock près d'une planète, d'un croiseur et d'une autre Mini.
3. Délais de test confirmés (30 s de sérialisation, 60 s de déchargement ?).
4. Modèle de mouvement d'une Mini sans joueur (trajectoire rectiligne uniforme ?) pour B2.
5. Accord (ou non) pour compiler Sable depuis la demande n° 1474, avec Docker (A9).
6. Packs de shaders et versions d'Iris et de Sodium (A10).


## A8 : changement de dimension sans écran de chargement (prêt, premier jeu de pistes)
Objectif : mesurer quel mécanisme change un joueur de dimension sans écran « Réception du niveau », sans image figée ni clignotement.
Deux dimensions identiques `a8a` et `a8b` ; une seule Mini, qui voyage avec toi à chaque changement (transfert A9 mécanisme 3, puis changement de dimension du joueur). Le journal client est `test-results/A8/client-a8.log` (écrans ouverts, changements de niveau, chunks chargés à +1, +2, +5, +10, +20 et +40 ticks, images de plus de 40 ms) ; le journal serveur est `test-results/A8/server-a8.log`.
1. Serveur propre : supprime `run-dev-server/world` si besoin, lance `./gradlew runServerDevMods`, puis le client `./gradlew runClientDevMods`, et connecte-toi à `localhost`.
2. `/spacetest a8 setup` : tu arrives sur la Mini de `a8a`. Reste debout au centre.
3. **Référence** (mode `vanilla`) : `/spacetest a8 go vanilla` (tu passes à `a8b`), puis encore pour revenir. Note : écran de chargement affiché, durée, image figée ? Fais 5 allers-retours.
4. **Piste 2 simplifiée** (mode `noscreen`) : dans le chat tape `/a8client noscreen true`, puis `/spacetest a8 go noscreen` 10 fois en alternant. Note : l'écran de chargement est-il supprimé ? Vois-tu un ciel vide, un clignotement, des chunks manquants, ta Mini qui disparaît ? Remets `/a8client noscreen false` ensuite.
5. **Piste 1** (mode `nopacket`, sans paquet de réapparition) : `/spacetest a8 go nopacket` : le client risque de ne pas suivre (monde figé, plantage, Mini absente). Note exactement ce que tu vois ; si le jeu se bloque, arrête-le et dis-moi ce qui s'affiche. Fais un seul essai à la fois.
Pour chaque mode réponds RÉUSSI / PARTIEL / ÉCHEC avec ce que tu observes (écran de chargement, durée visible de l'image vide ou figée, ta position avant/après).
Pas encore codé : la piste 3 (préchargement des chunks de destination envoyés au client avant le changement) et le test à 50 changements automatiques avec mesure mémoire ; je les écrirai selon tes résultats.

## A10 : shaders avec Sable (prêt)
Mods ajoutés côté client uniquement (dossier `dev-mods-client/`, copiés seulement par `runClientDevMods`) : **Sodium 0.8.13 (release du 28/08/2026)** et **Iris 1.8.14-beta.1** (seule version d'Iris pour Sodium 0.8 ; version bêta). Pack de shaders : **Complementary Reimagined r5.9.3** (`run-dev/shaderpacks/`).
Si le client refuse de démarrer (Iris déclare une plage Minecraft « [1.21,1.21.1) » qui peut exclure la 1.21.1), remplace les deux jars de `dev-mods-client/` par ceux de `dev-mods-client-alt/` (paire stable : Sodium 0.6.13 + Iris 1.8.12), puis relance, et dis-moi quelle paire tu as utilisée.
Mesure des images par seconde : `/a10client fps <secondes> <étiquette>` (par exemple `/a10client fps 30 mini_sans_shaders`) écrit moyenne, 1 % bas et image la plus longue dans `test-results/A10/client-a10.log`. `/a10client note <texte>` ajoute une remarque au journal. Activer ou désactiver les shaders : Options > Vidéo > Packs de shaders (Iris).
Procédure (client + serveur lancés comme pour A3/A8) :
1. **Référence sans Mini** : dans l'overworld, regarde un paysage fixe : `/a10client fps 30 ref_sans_shaders`, puis active les shaders et refais `/a10client fps 30 ref_avec_shaders`.
2. **A3 avec shaders** : `/spacetest a3 setup`. Mesure `/a10client fps 30 mini_sans_shaders`, active les shaders, `/a10client fps 30 mini_avec_shaders`. Puis, shaders actifs : `/spacetest a3 tp 10 0 0`, `tp 300 0 0`, `tp 1000 0 0`, `tps 1000 0 0`, et `/spacetest a3 move 5` pendant 2 minutes (vol), puis `/spacetest a3 stop`. Note à chaque étape : écran noir, ombres de la Mini manquantes, ciel ou textures corrompus, plantage.
3. **A8 avec shaders** : `/spacetest a8 setup`, puis 10 changements `/spacetest a8 go nopacket` (sans et avec shaders). Note les défauts à chaque bascule (écran noir, ombres, ciel) et si les shaders se rechargent.
Réponds RÉUSSI / PARTIEL / ÉCHEC par scénario, avec la paire Sodium/Iris utilisée, et recopie les 4 lignes de mesure FPS.
Critères : aucun défaut propre à Sable ou aux bascules ; FPS avec la Mini à moins de 30 % d'écart de la référence sans Mini dans les mêmes conditions de shaders.

## Phase C (état au 20 octobre 2026)
- Faits sans toi : C3, C5, C7 (partie automatique), C2 (serveur seul), C4 (partie numérique). Rapports dans `test-results/C*/rapport.md`.
- Restent à toi : C4 (juger le saut et le parcours : le saut/ciel n'existent pas encore), C7 (joueur à bord, facultatif), C5 (joueur à bord, facultatif).
- C1 et C6 : parties serveur faites (rapports). Restent à toi : deux joueurs réels pour C1 (rencontre de deux Minis, juger les sauts de pose et la coque) et au moins deux joueurs réels pour C6 (bande passante, ciel, fluidité) ; ils demandent un hôte temporaire et une couche stratégique côté jeu qui n'existent pas encore dans le mod. Mesure client de C2 aussi.
