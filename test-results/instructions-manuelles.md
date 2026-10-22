# Instructions pour les tests manuels (A3, A7, A8, A10 prêts ; D1 à D7 prêts, à faire en premier ; C1 à C7 : voir la section Phase C en fin de fichier)

Tout se passe dans le dossier du projet. Les journaux sont écrits dans `test-results/`. Les mods (Sable, Architectury, Spark) sont chargés automatiquement depuis `dev-mods/`. **Depuis le 22 octobre, la bibliothèque de dimensions est DynamicDimensions, intégrée au mod** (Madnessverse est désactivé : `madnessverse-neoforge-1.0.0.jar.disabled`). Les anciens tests A3, A7, A8, A9, A10 et C tournent donc maintenant sur DynamicDimensions et n'ont pas été rejoués avec elle côté client : signale tout comportement différent de ce qui est décrit.

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

## D : cycle de vie des dimensions, DynamicDimensions intégré (prêt, à faire en premier)
Deux règles à vérifier avec un vrai client :
1. **Une dimension contenant un joueur n'est JAMAIS déchargée (ni supprimée) tant qu'il y est.**
2. **Un joueur qui se connecte dans une dimension non chargée n'a aucun problème : le serveur la charge.**
Ce que le serveur a déjà passé seul (`test-results/D1/autotest.log`, `A1`, `A2`) : refus d'unload avec un joueur factice, rechargement par le point d'entrée de login, déchargement automatique, suppression d'une temporaire avec ses fichiers, 10 cycles create/unload/load/delete avec Sable, persistance après arrêt propre et brutal. Il reste ce qui demande un client : les registres, l'écran de connexion, la position réelle du joueur, et la physique de Sable avec un joueur.

**Commandes** (tapées dans le jeu ; `/spacetest dd ...`) :
- `setup` : crée (ou recharge) `dd_a` et `dd_b` (plateforme de pierre 9×9 en y=63, Mini à (20,64,20)) et te place en (0,64,0) de `dd_a`.
- `status` : niveaux chargés, état de chaque dimension gérée (chargée / DÉCHARGÉE, joueurs, vide depuis combien de secondes, réservée).
- `go a` / `go b` / `go overworld` / `go tmp_1` : te téléporte (charge la dimension si besoin).
- `unload a|b`, `load a|b`, `delete a|b` : appel direct ; doit répondre REFUSÉ si un joueur est dedans.
- `temp` : crée une dimension temporaire `tmp_N` et t'y met.
- `auto <secondes>` : déchargement automatique d'une dimension vide après N secondes (0 = désactivé). Défaut : 30. Les temporaires vides sont supprimées au lieu d'être déchargées.
- `reset` : supprime les dimensions chargées gérées (laisse les autres).
Journal : `test-results/D1/dd-run.log` (une ligne par événement : `LOAD`, `UNLOAD OK`, `UNLOAD REFUSÉ`, `LOGIN-PRELOAD`, `LOGIN-OK`, `CHANGE-DIM`, `DELETE`, `AUTO`). Joins-le à chaque réponse, avec `test-results/` et `run-dev-server/logs/latest.log` pour les échecs.

**Préparation** : arrête le serveur, supprime `run-dev-server/world` et `run-dev-server/ddtest`, supprime `test-results/D1`. Lance `./gradlew runServerDevMods` puis `./gradlew runClientDevMods` et connecte-toi à `localhost`. Pour accélérer, `/spacetest dd auto 15` (15 s au lieu de 30 s) ; les délais ci-dessous sont à adapter.

### D1 : rien de chargé au démarrage, chargement à la demande
1. Serveur tout juste lancé : `/spacetest dd status`. Attendu : seulement `overworld`, `the_nether`, `the_end` chargés, aucune `dd_*`.
2. `/spacetest dd setup`. Attendu : tu arrives sur la plateforme de `dd_a`, sans écran d'erreur ; `status` montre `dd_a` et `dd_b` chargées.
3. `/spacetest dd go b` puis `go a` : aucun blocage, tu retrouves chaque plateforme. Noter la durée de l'écran de chargement.
Critère : RÉUSSI si tout se passe sans erreur dans `latest.log`.

### D2 : refus d'unload avec un joueur dedans (règle 1)
1. Tu es dans `dd_a`. `/spacetest dd unload a`. Attendu : « REFUSÉ : joueur présent… » ; tu ne bouges pas, tu ne tombes pas, la dimension reste chargée (`status`).
2. `/spacetest dd delete a`. Attendu : REFUSÉ aussi.
3. `/spacetest dd auto 10`, reste immobile dans `dd_a` 40 s. Attendu : `dd_a` jamais déchargée, le journal ne contient aucun `UNLOAD OK spacemod_test:dd_a`.
4. Reste en jeu sans bouger plusieurs minutes avec `auto 10` : toujours rien.
Critère : RÉUSSI si la dimension n'a jamais été déchargée ni supprimée. ÉCHEC dès que tu te retrouves expulsé vers l'Overworld sans l'avoir demandé.

### D3 : déchargement automatique d'une dimension vide
1. `/spacetest dd auto 15`. Tu es dans `dd_a`, fais `go b`.
2. Attends environ 20 s. `status` : `dd_a` DÉCHARGÉE ; `dd_b` (où tu es) reste chargée.
3. `go a` : `dd_a` est rechargée, la Mini (à (20,64,20)) est là et intacte, tu arrives sur la plateforme.
Critère : RÉUSSI si le déchargement a lieu après le délai, jamais pendant que tu es dans la dimension, et si le rechargement restitue la Mini.

### D4 : reconnexion dans une dimension déchargée (règle 2)
1. `auto 15`. Dans `dd_a`, place-toi à un endroit reconnaissable de la plateforme (`/tp @s 3 64 3`). Note la position (F3).
2. Quitte le monde (Menu → Quitter). Attends que `test-results/D1/dd-run.log` affiche `AUTO … dd_a` puis `UNLOAD OK … dd_a` (≈ 20 s).
3. Reconnecte-toi à `localhost`. Attendu : tu arrives dans `dd_a` à la même position, sans écran d'erreur, sans « Can't find id » et sans message `Unknown respawn dimension` dans `run-dev-server/logs/latest.log`. Le journal contient `LOGIN-PRELOAD … chargée=true` puis `LOGIN-OK … dans spacemod_test:dd_a`.
4. Variante : déconnecte-toi puis reconnecte-toi tout de suite (avant le délai) : la dimension ne doit pas avoir été déchargée entre-temps (`UNLOAD REFUSÉ` ou aucun `UNLOAD`).
Critère : RÉUSSI si tu es dans la bonne dimension, à la bonne position.
ÉCHEC si tu es dans l'Overworld (même brièvement), à une position étrange, ou si le client refuse de se connecter.

### D5 : reconnexion après redémarrage du serveur
1. Dans `dd_a`, quitte le monde, arrête le serveur (`stop`), puis relance `./gradlew runServerDevMods` (ne supprime rien).
2. Dès « Done » : `dd-run.log` doit montrer `DÉMARRAGE gérées={dd_a, dd_b}` et seulement 3 niveaux chargés.
3. Connecte-toi. Attendu comme en D4 : `LOGIN-PRELOAD`, puis tu es dans `dd_a`, au bon endroit, avec la Mini.
4. Même chose avec un arrêt brutal : dans `dd_a`, tue le serveur (`kill -9` du processus Java), relance, reconnecte-toi. Attendu : la dernière sauvegarde automatique est restaurée, pas de plantage.
Critère : RÉUSSI si tu es dans `dd_a` ; note ta position avant/après.

### D6 : dimension temporaire
1. `/spacetest dd temp` : tu es dans `tmp_1` (plateforme de pierre vide), `status` la montre `[temp]`.
2. `/spacetest dd unload tmp_1` doit être REFUSÉ (tu es dedans). `go overworld`, attends le délai `auto` : `tmp_1` doit disparaître (`status`, journal `DELETE OK`) et le dossier `run-dev-server/world/dimensions/spacemod_test/tmp_1` ne doit plus exister.
3. Recrée une temporaire, quitte le monde dedans, attends le délai, reconnecte-toi. Comportement attendu (à confirmer par toi) : la temporaire a été supprimée (vide), tu es replacé dans l'Overworld au spawn ; le journal indique `LOGIN-PRELOAD … est temporaire et a été supprimée`. Dis-moi si tu préfères autre chose (par exemple ne jamais supprimer une temporaire dont le dernier occupant est hors ligne).
Critère : RÉUSSI si 2 est conforme et si 3 se fait sans erreur de connexion.

### D7 : client connecté pendant la création et la suppression
1. Connecté dans l'Overworld : `/spacetest dd setup`, `go overworld`, `/spacetest dd delete a`, `/spacetest dd setup` (recrée `dd_a`), `go a`, `go overworld`. Répéter 3 fois.
Prévoir un `auto` long (`/spacetest dd auto 120`) : une dimension déjà déchargée ne peut pas être supprimée (refus attendu, dette technique).
2. Observe le client : écran figé, « Can't find id », erreur de registre, image noire prolongée. Joins `latest.log` du client (`run-dev/logs/latest.log`) et du serveur.
3. Avec Sable : dans `dd_a`, regarde la Mini (20,64,20) ; elle doit rester immobile (gravité nulle) et solide quand tu marches dessus.
Critère : RÉUSSI si aucune erreur dans les deux journaux. Noter toute erreur textuellement.

### Réponse attendue pour D
Pour chaque test : RÉUSSI / PARTIEL / ÉCHEC, ce que tu as vu (écran, position avant/après), et les fichiers `test-results/D1/dd-run.log`, `run-dev-server/logs/latest.log` et `run-dev/logs/latest.log` en cas de problème. Si tout est RÉUSSI, je passe à l'intégration dans la couche stratégique (chargement à la demande par unité occupée).

## Pas encore prêts (je te redemanderai)
- **A8** (changement de dimension sans écran de chargement) : demande d'écrire des mixins client ; je dois d'abord les construire, je ne te demande rien pour l'instant.
- **A10** (shaders) : dépend d'A8. Quand ce sera prêt : installer Sodium et Iris (versions à me donner) et un pack de shaders.
- **C1, C4, C6, C7** : dépendent d'A3/A8 et de valeurs à fixer (voir ci-dessous).

## Valeurs que j'attends de toi (section 8 du plan)
1. R (volume logique) et formule de M pour les hôtes de test.
2. Vitesse maximale du mass lock près d'une planète, d'un croiseur et d'une autre Mini.
3. Délais de test confirmés (30 s de sérialisation, 60 s de déchargement ?) ; délai d'inactivité avant le déchargement d'une dimension (30 s par défaut dans les tests D) ; sort d'un joueur déconnecté dans une dimension temporaire (aujourd'hui : temporaire supprimée, repli Overworld).
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
