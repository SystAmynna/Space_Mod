# Plan de tests

*Mod Minecraft de voyage spatial seamless : tests délégables à des agents*

*Version 1 · 20 octobre 2026 · associé au plan d’architecture version 5 (`architecture_v5.md`)*

## 1. Comment utiliser ce document

Ce document s’adresse à des agents IA chargés d’exécuter les tests de l’architecture, et à l’utilisateur qui supervise. Chaque test est autonome : il donne son objectif, ses prérequis, sa procédure, ce qu’il faut mesurer et les critères de réussite. Quand un test demande un humain, l’agent s’arrête et fait appel à l’utilisateur.

### 1.1 Règles pour l’agent

1.  **Ne jamais modifier l’architecture ni les seuils.** Les valeurs de ce document sont des propositions de test. Si un seuil paraît inadapté, l’agent le dit dans son rapport et demande, sans le changer pour faire passer un test.
2.  **Ne jamais déclarer un test réussi sans mesure.** Chaque critère s’appuie sur une valeur relevée, un log ou une observation humaine.
3.  **Travailler sur un monde de test jetable,** jamais sur le monde de l’utilisateur. Garder les fichiers de résultats par test dans `test-results/<N°>/` (logs, mesures brutes, captures, rapport).
4.  **Épingler les versions** (Minecraft, NeoForge, Sable, DynamicDimensions, Iris, Sodium, Immersive Portals) et les consigner dans chaque rapport, avec l’identifiant du commit du mod testé.
5.  **Vérifier les noms d’API dans les jars réellement utilisés.** Les noms de la section 2.3 ont été relevés dans le code source ; ils peuvent différer d’une version à l’autre.
6.  **S’arrêter et signaler** (statut BLOQUÉ) dès qu’un prérequis manque, qu’un test bloquant échoue (A1 ou A8), ou qu’un résultat est ambigu. Ne pas contourner par une solution non prévue.
7.  **Licence de Sable (Polyform Shield 1.0.0) :** utiliser l’API de Sable ; ne pas copier son code dans le mod sans accord de l’utilisateur.
8.  **Ne pas lancer de mise en production, de publication ni de push** sans demande explicite de l’utilisateur.
9.  **Écrire les rapports en français,** courts, avec les données brutes en pièce jointe.

### 1.2 Types d’intervention manuelle

| **Étiquette** | **Signification** |
| --- | --- |
| **[MANUEL-CLIENT]** | Un humain doit lancer un client Minecraft, se connecter, agir (marcher, monter à bord, se déconnecter) à un moment précis. |
| **[MANUEL-VISUEL]** | Un humain doit regarder l’écran et juger le rendu (saut visible, écran de chargement, défaut de shader). |
| **[MANUEL-DÉCISION]** | L’agent a besoin d’une valeur ou d’un choix de l’utilisateur. |
| **[MANUEL-OUTILLAGE]** | Un outil manque dans l’environnement de l’agent (par exemple Docker pour compiler Sable depuis une branche). |

### 1.3 Format d’une demande d’intervention manuelle

L’agent regroupe les interventions d’une même session pour limiter les interruptions, puis envoie :

```
DEMANDE D’INTERVENTION MANUELLE
Test : <N°> — étape <n>
Pourquoi : <ce que l’agent ne peut pas faire seul>
À faire : <actions numérotées, exactes, avec commandes et positions>
À observer : <ce qu’il faut regarder>
Réponse attendue : RÉUSSI / ÉCHEC / PARTIEL, un commentaire, et une capture ou une vidéo si possible
L’agent reprend le test dès réception de la réponse.
```

### 1.4 Statuts de résultat

**RÉUSSI** (tous les critères de réussite sont atteints) · **ÉCHEC** (au moins un critère d’échec) · **PARTIEL** (résultat acceptable avec réserve, à expliquer) · **BLOQUÉ** (impossible de poursuivre) · **NON EXÉCUTÉ**.

### 1.5 Gabarit de rapport de test

```
# Rapport <N°> — <titre>
Statut : <RÉUSSI | ÉCHEC | PARTIEL | BLOQUÉ>
Date, agent, durée
Versions : Minecraft, NeoForge, Sable, DynamicDimensions, autres ; commit du mod
Ce qui a été fait : <étapes réalisées, écarts par rapport à la procédure>
Mesures : <tableau des valeurs relevées>
Critères : <chaque critère, valeur obtenue, verdict>
Interventions manuelles : <qui, quand, réponse>
Anomalies et observations : <tout ce qui est inattendu>
Conséquence pour l’architecture : <repli à activer, point à rouvrir, rien>
Pièces jointes : <logs, mesures brutes, captures>
```

## 2. Environnement commun

### 2.1 Versions et dépendances

| **Élément** | **Valeur** |
| --- | --- |
| **Java** | 21 |
| **Minecraft / NeoForge** | 1.21.1 / 21.1.x (la version utilisée par DynamicDimensions est 21.1.148) |
| **Sable** | 2.0.6 (dépôt Maven `https://maven.ryanhcode.dev/releases`, artefact `dev.ryanhcode.sable:sable-common-1.21.1`, et son équivalent NeoForge) |
| **DynamicDimensions** | 0.10.0, artefact `dev.galacticraft.dynamicdimensions-neoforge` ; dépôt Maven `https://repo.terradevelopment.net/repository/maven-releases` (celui de Galacticraft est signalé hors ligne) |
| **Outils** | Spark (profilage et TPS), Gradle (`runServer`, `runClient`), GameTests NeoForge (Sable en utilise lui-même) |
| **Mod de test** | Un mod NeoForge jetable, `spacemod_test`, qui expose des commandes de test (`/spacetest …`) et journalise les mesures |

### 2.2 Valeurs de test proposées (à valider par l’utilisateur)

| **Paramètre** | **Valeur** |
| --- | --- |
| **Budget d’un tick** | 50 ms |
| **Mini petite (S)** | environ 100 blocs, avec 5 coffres remplis |
| **Mini grande (L)** | environ 2 000 blocs, avec 50 coffres remplis |
| **Délai de sérialisation (test)** | 30 s |
| **Délai de déchargement d’un système (test)** | 60 s |
| **Dérive de position tolérée (verrouillage)** | moins de 0,01 bloc sur 5 minutes |
| **Tremblement toléré (écart-type de la position)** | moins de 0,001 bloc |
| **Continuité de vitesse stratégique** | écart relatif inférieur à 1e-6 |
| **Poses de la Tampon Spatiale** | groupées à moins de 5 000 blocs de l’origine |

### 2.3 Points d’entrée d’API relevés dans le code

**DynamicDimensions :** `DynamicDimensionRegistry.from(server)`, `createDynamicDimension(id, generator, type[, properties])`, `loadDynamicDimension(id, generator, type[, properties])`, `unloadDynamicDimension(id, remover)`, `deleteDynamicDimension(id, remover)`, `dynamicDimensionExists(id)`, `DynamicDimensionLoadCallback.register(…)`, `DynamicDimensionProperties` (classe abstraite : priorité, gravité de base, pression de base, traînée universelle, nord magnétique).

**Sable :** `SubLevelContainer.getContainer(level)` (conteneur serveur : `getOccupancy()`, `removeSubLevel(sousNiveau, raison)`), `SubLevelRemovalReason` (déchargé, retiré), `ServerSubLevel.logicalPose()`, `PhysicsPipeline.teleport(corps, position, orientation)`, `resetVelocity(sousNiveau)`, `RigidBodyHandle.teleport(position, orientation)`, `addConstraint(corpsA, corpsB, configuration)` (un corps nul ancre l’autre au monde), `GenericConstraintConfiguration(pos1, pos2, orientation1, orientation2, axesVerrouillés)`, `ConstraintJointAxis` (`LINEAR_X/Y/Z`, `ANGULAR_X/Y/Z`), `FixedConstraintConfiguration`, `SubLevelSerializer` (sérialisation et rechargement d’un sub-level), `Sable.HELPER.getTrackingSubLevel(entité)`, `Sable.HELPER.projectOutOfSubLevel(niveau, position)`, commandes `/sable teleport …`.

**Demande de modification n° 1474 de Sable (non fusionnée) :** `SubLevelTransferService.transfer(racine, niveauDestination, poseDestination)`, disponible sur la référence `refs/pull/1474/head` du dépôt de Sable.

**Immersive Portals (repli, à vérifier) :** `PortalAPI.addChunkLoaderForPlayer`, `ChunkLoader`, `teleportEntityGeneral`, `forceTeleportPlayer`.

## 3. Ordre d’exécution et dépendances

| **Test** | **Dépend de** | **Remarque** |
| --- | --- | --- |
| **A1** | rien | Premier test, bloquant. |
| **A2** | A1 | |
| **A3** | A1 | |
| **A4** | A1, A3 | |
| **A5** | A1 | |
| **A6** | A1 | |
| **A7** | A6 | |
| **A9** | A1, A6 | |
| **A8** | A1, A3 | Bloquant ; l’essai avec un sub-level utilise aussi A9. |
| **A10** | A3, A8 | |
| **B1 à B4** | rien | Se font hors du jeu, sur des prototypes de la logique stratégique, puis sur le code réel quand la phase 1 est livrée. |
| **C1** | A3, A4, A5, B3 | À exécuter quand l’hôte temporaire existe (phase 6), ou sur un prototype minimal. |
| **C2** | A1 | Demande des valeurs de l’utilisateur. |
| **C3** | A8, A9 | |
| **C4** | B1 | |
| **C5** | A6, A7 | |
| **C6** | C1, A8 | |
| **C7** | A1, A4, A5, C3 | |

## 4. Phase 0 : prototypes de risques

### A1 — DynamicDimensions avec Sable 2.0.6

| **Champ** | **Contenu** |
| --- | --- |
| **Dépendances** | Aucune. |
| **Intervention manuelle** | Non. |
| **Pourquoi** | La compatibilité de DynamicDimensions avec Sable est compilée contre Sable 1.2.2, alors que Sable est en 2.0.6. Tout le reste en dépend. |

**Procédure**

1.  Créer le projet du mod de test avec Sable 2.0.6 et DynamicDimensions 0.10.0 en dépendances ; démarrer un serveur dédié. Relever dans le log toute erreur ou avertissement de mixin, en particulier ceux du plugin de compatibilité Sable de DynamicDimensions.
2.  Déclarer en datapack un type de dimension « vide ». Créer, à l’exécution, la dimension `spacemod_test:void` avec `createDynamicDimension`, un générateur vide et des propriétés de physique : gravité (0, 0, 0), pression 0, traînée 0.
3.  Créer de la même façon `spacemod_test:gravity` avec la gravité par défaut de Sable, pour comparaison.
4.  Vérifier que le conteneur de sub-levels et le système de physique existent pour chaque niveau créé (`SubLevelContainer.getContainer(niveau)` non nul).
5.  Dans chaque niveau, assembler un sub-level de la taille S et le laisser 200 ticks sans contrainte. Relever la variation de sa position.
6.  Décharger la dimension `void` (`unloadDynamicDimension`), la recharger (`loadDynamicDimension`) et vérifier que le sub-level est retrouvé.
7.  Supprimer la dimension `void` (`deleteDynamicDimension`) et vérifier l’absence d’exception, de fuite de niveau chargé et de fichiers restants.
8.  Répéter les étapes 2 à 7 dix fois de suite.

**Mesures :** exceptions et avertissements, déplacement du sub-level dans chaque dimension, nombre de niveaux chargés avant et après, durée de création et de chargement.

**Critères.** Réussite : aucune erreur de mixin ; sub-level immobile (déplacement inférieur à 0,01 bloc) dans la dimension sans gravité et en chute dans l’autre ; sub-level retrouvé après rechargement ; aucune fuite après dix cycles. Échec : erreur de mixin ou de compatibilité, ou propriétés de physique ignorées.

**Si échec.** Rapporter précisément l’échec. Pistes à explorer, sans les implémenter sans accord : appliquer la physique de la dimension depuis le mod, corriger la compatibilité, ou utiliser une autre bibliothèque (fork « AR Edition », Madnessverse).

### A2 — Persistance des dimensions et recréation de la Tampon Spatiale

| **Champ** | **Contenu** |
| --- | --- |
| **Dépendances** | A1. |
| **Intervention manuelle** | Non. |
| **Pourquoi** | Les dimensions d’objets sont persistantes ; la Tampon Spatiale est recréée à chaque démarrage. DynamicDimensions ne retient pas les dimensions créées. |

**Procédure**

1.  Créer une dimension persistante `spacemod_test:persist`, y poser des blocs et y assembler un sub-level de taille S, noter sa pose.
2.  Créer la dimension `spacemod_test:tampon` avec `createDynamicDimension`, y assembler un sub-level, noter sa pose et la valeur de l’occupation des plots.
3.  Arrêter proprement le serveur. Le redémarrer. Au démarrage, recharger `persist` avec `loadDynamicDimension` (via `DynamicDimensionLoadCallback` ou au premier besoin) et recréer `tampon` avec `createDynamicDimension`.
4.  Comparer : blocs, sub-level et pose de `persist` ; contenu, sub-levels, fichiers de région et occupation des plots de `tampon`.
5.  Répéter avec un arrêt brutal (processus tué) après une sauvegarde automatique.

**Mesures :** différences de blocs et de pose, nombre de sub-levels, contenu du dossier de la dimension `tampon`, bits d’occupation des plots.

**Critères.** Réussite : `persist` identique après redémarrage ; `tampon` vide, sans sub-level ni occupation de plot résiduelle. Échec : données de `tampon` rechargées, ou perte de données de `persist` après arrêt propre.

**Si échec.** Rapporter ; envisager le chargement systématique par `loadDynamicDimension` puis un nettoyage explicite de la Tampon Spatiale.

### A3 — Téléportation de pose d’un sub-level portant un joueur

| **Champ** | **Contenu** |
| --- | --- |
| **Dépendances** | A1. |
| **Intervention manuelle** | Oui : [MANUEL-CLIENT] et [MANUEL-VISUEL]. |
| **Pourquoi** | Sable ne gère pas un saut de pose dans son interpolation client. Le déplacement d’une Mini dans la Tampon Spatiale en dépend. |

**Procédure**

1.  Dans la dimension `void`, assembler un sub-level de taille S avec un sol, des murs et un coffre. Ajouter une commande de test `/spacetest pose_teleport <dx> <dy> <dz> [reset_vitesse]` qui appelle `PhysicsPipeline.teleport` avec une nouvelle position et la même orientation.
2.  Ajouter au client de test un journal qui écrit, à chaque image, la pose affichée du sub-level (position et orientation) et la position du joueur.
3.  [MANUEL-CLIENT] Demander à l’utilisateur de se connecter, de monter dans la Mini et de marcher dessus.
4.  Exécuter la commande avec des déplacements de 10, 1 000 et 5 000 blocs, avec et sans remise à zéro de la vitesse, sur un sub-level immobile puis en mouvement (vitesse 5 blocs par seconde).
5.  [MANUEL-VISUEL] L’utilisateur note : le joueur reste-t-il sur le sol ? Voit-il un balayage, un clignotement, une chute ?
6.  Répéter la téléportation 20 fois de suite, à intervalle de 1 seconde, puis 10 fois à intervalle de 1 tick.

**Mesures :** pour chaque téléportation, nombre d’images intermédiaires entre l’ancienne et la nouvelle pose (journal du client), position relative du joueur sur le sub-level avant et après, vitesse du sub-level avant et après, avis de l’utilisateur.

**Critères.** Réussite : le joueur reste à la même position relative (écart inférieur à 0,05 bloc) ; au plus une image intermédiaire entre les deux poses ; aucune éjection du sub-level (le joueur n’est pas expulsé) ; aucune erreur. PARTIEL : un balayage visible d’un ou deux ticks. Échec : joueur éjecté ou tombé, erreurs, désynchronisation durable.

**Si échec ou PARTIEL.** Noter précisément le comportement ; pistes à documenter (sans les implémenter) : fondu visuel, choisir comme origine du groupe la Mini qui porte le joueur et ne déplacer que les autres.

### A4 — Position verrouillée, rotation libre

| **Champ** | **Contenu** |
| --- | --- |
| **Dépendances** | A1 (A3 recommandé). |
| **Intervention manuelle** | Selon les cas : [MANUEL-VISUEL] facultatif pour juger le confort de la rotation. |
| **Pourquoi** | Une Mini en vol libre doit rester immobile dans la Tampon Spatiale tout en pouvant tourner. |

**Procédure**

1.  Dans `void`, assembler un sub-level de taille S, puis de taille L. Choisir un bloc du sub-level comme point d’ancrage et une position d’ancrage dans le monde, **hors de la grille de plots** de Sable.
2.  Ajouter une contrainte avec `addConstraint(null, corps, new GenericConstraintConfiguration(posMonde, posPlot, identité, identité, {LINEAR_X, LINEAR_Y, LINEAR_Z}))`. Vérifier que la validation des ancres n’échoue pas.
3.  Appliquer : (a) une impulsion linéaire importante ; (b) un couple (rotation autour de chaque axe) ; (c) les deux ensemble.
4.  Laisser 5 minutes (6 000 ticks) avec : le sub-level seul ; un joueur qui marche dessus ; 20 entités posées sur le sol.
5.  Enregistrer la position et l’orientation du sub-level à chaque tick.
6.  Répéter avec une contrainte fixe (tous les axes) pour comparaison, et avec le sub-level L.

**Mesures :** dérive de position (distance maximale à la position d’origine), écart-type de la position, vitesse angulaire après le couple, durée d’amortissement, coût de la physique par tick (Spark).

**Critères.** Réussite : la position ne dérive pas (moins de 0,01 bloc) et ne tremble pas (écart-type inférieur à 0,001) malgré l’impulsion linéaire ; la rotation répond au couple et reste libre ; le comportement est stable avec le joueur et les entités. Échec : la position dérive, la contrainte est rejetée, ou la rotation est bloquée.

**Si échec.** Rapporter ; le repli décidé est de déléguer tout au rendu de la couche stratégique et aux transactions de plots. Ne pas implémenter d’autre solution sans accord.

### A5 — Gel par contrainte fixe et endormissement

| **Champ** | **Contenu** |
| --- | --- |
| **Dépendances** | A1. |
| **Intervention manuelle** | Non. |
| **Pourquoi** | Les Minis dormantes sont gelées (contrainte fixe au monde) ou endormies (posées sous gravité). |

**Procédure**

1.  Dans `void`, geler un sub-level S par une contrainte fixe au monde (`FixedConstraintConfiguration`, corps nul). Mesurer 5 minutes de position avec un joueur qui marche dessus.
2.  Dans `gravity`, poser un sub-level S sur un sol de blocs (un pont). Après 10 secondes, vérifier son état de sommeil s’il est exposé par Sable ; sinon comparer le coût d’un pas de physique avec 1 puis 200 sub-levels posés (Spark).
3.  Réveiller le sub-level : poser un bloc dessus, le toucher, appliquer une impulsion. Vérifier qu’il se réveille puis se rendort.
4.  Vérifier que le gel survit à un déchargement et à un rechargement de la dimension.

**Mesures :** dérive et tremblement (mêmes seuils que A4), état de sommeil ou coût de physique avec 1 et 200 sub-levels, temps de réveil et de rendormissement.

**Critères.** Réussite : pas de tremblement ; coût de physique quasi constant avec 200 sub-levels au repos (moins de 10 % de variation par rapport à 1) ; réveil sur sollicitation. Échec : tremblement persistant, ou coût proportionnel au nombre de sub-levels au repos.

**Si échec.** Rapporter ; la variante à origine flottante reste en réserve.

### A6 — Sérialisation en données, libération et réallocation des plots

| **Champ** | **Contenu** |
| --- | --- |
| **Dépendances** | A1. |
| **Intervention manuelle** | Non. |
| **Pourquoi** | Une Mini inoccupée en vol libre est sérialisée en données et ne doit occuper aucun plot. |

**Procédure**

1.  Dans `void`, assembler un sub-level S (5 coffres remplis, un armor stand marqué pour rester dans le sub-level, une valeur dans le tag `user_data` propre au mod) et un sub-level L. Leur donner une vitesse.
2.  Sérialiser avec `SubLevelSerializer`, puis retirer le sub-level avec la raison « retiré ». Relever la taille des données et la durée.
3.  Vérifier que le bit d’occupation du plot est libéré (`getOccupancy()`) et que le sub-level n’est plus dans le conteneur.
4.  Désérialiser dans la même dimension. Vérifier : identifiant, blocs, contenu des coffres, entités, valeur de `user_data`, pose, vitesse (le document d’architecture indique une multiplication par 0,9 par la configuration de Sable : vérifier la valeur réelle), plot attribué.
5.  Conflit : avant la désérialisation, faire occuper l’ancien plot par un autre sub-level ; vérifier ce qui se passe (nouveau plot, erreur, perte).
6.  Fuite : répéter 1 000 cycles sérialiser, retirer, désérialiser ; comparer le nombre de plots occupés au début et à la fin.

**Mesures :** durée de sérialisation et de désérialisation (S et L), taille en octets, nombre de plots occupés après 1 000 cycles, écarts de contenu, facteur de vitesse.

**Critères.** Réussite : plot libéré à la sérialisation ; contenu intégralement restitué ; aucun plot perdu après 1 000 cycles ; conflit géré sans perte de données. Échec : plot non libéré, contenu altéré, ou fuite d’occupation.

**Si échec.** Rapporter ; indiquer si la raison de retrait « déchargé » (qui garde l’occupation) a été confondue avec « retiré ».

### A7 — Déconnexion d’un joueur et délai de sérialisation

| **Champ** | **Contenu** |
| --- | --- |
| **Dépendances** | A6. |
| **Intervention manuelle** | Oui : [MANUEL-CLIENT]. |
| **Pourquoi** | Une Mini devenue vide attend un délai avant d’être sérialisée, au cas où le joueur se reconnecte. La Tampon Spatiale étant recréée à chaque démarrage, les points de suivi de Sable ne suffisent peut-être pas. |

**Procédure**

1.  Implémenter dans le mod de test : à la déconnexion d’un joueur qui est seul dans une Mini en vol libre, démarrer un minuteur (délai de test : 30 s) ; à la fin, sérialiser et retirer la Mini ; à la reconnexion, restaurer le joueur dans la Mini (la désérialiser si nécessaire) à sa position relative.
2.  [MANUEL-CLIENT] L’utilisateur se connecte, monte dans la Mini, marche vers un point repéré (coordonnées relatives notées par l’agent), puis se déconnecte à la demande de l’agent.
3.  Cas a : reconnexion 10 s après la déconnexion (avant le délai). Cas b : reconnexion 45 s après (après le délai). Cas c : arrêt du serveur pendant le délai. Cas d : arrêt brutal du serveur pendant le délai.
4.  Dans chaque cas, relever : état de la Mini (sub-level ou données), position du joueur relativement à la Mini, intégrité du contenu.

**Mesures :** état de la Mini avant et après, position relative avant la déconnexion et après la reconnexion, présence d’une trace des points de suivi de Sable pour la Tampon Spatiale.

**Critères.** Réussite : cas a, la Mini n’est pas sérialisée et le joueur est restauré (écart inférieur à 0,1 bloc) ; cas b, la Mini est restaurée depuis les données et le joueur retrouvé à sa position relative ; cas c, sérialisation immédiate à l’arrêt ; cas d, perte limitée à la dernière sérialisation. Échec : joueur hors de la Mini, Mini dupliquée ou perdue.

**Si échec.** Rapporter ; indiquer si le mod doit enregistrer lui-même la position relative des joueurs.

### A8 — Changement de dimension sans Immersive Portals

| **Champ** | **Contenu** |
| --- | --- |
| **Dépendances** | A1, A3. |
| **Intervention manuelle** | Oui : [MANUEL-CLIENT] et [MANUEL-VISUEL]. |
| **Pourquoi** | Test le plus risqué : le mod doit changer un joueur de dimension sans écran de chargement et sans dépendance à Immersive Portals. |

**Procédure**

1.  **Référence.** Changer un joueur de dimension avec le mécanisme vanilla. Mesurer la durée et noter l’écran affiché. Ajouter un journal client qui enregistre chaque ouverture d’écran (en particulier l’écran « Réception du niveau »).
2.  **Piste 1, serveur.** Déplacer le joueur de `void_a` vers `void_b` en l’enlevant de l’ancien niveau et en l’ajoutant au nouveau sans envoyer le paquet de respawn. Observer ce que fait le client.
3.  **Piste 2, client.** Mettre en place un mixin ou un paquet maison qui conserve un monde client par dimension et bascule le monde du joueur à la réception d’un ordre du serveur.
4.  **Piste 3, préchargement.** Envoyer au client, avant la bascule, les chunks de la destination, adressés au monde client de la destination (paquet maison portant la dimension). Vérifier qu’ils sont présents au moment de la bascule.
5.  Essayer ensuite avec un joueur à bord d’une Mini (sub-level) : bascule du sub-level et du joueur avec le mécanisme de transfert de A9, et suivi de Sable côté client après la bascule.
6.  [MANUEL-CLIENT] [MANUEL-VISUEL] L’utilisateur répète 10 bascules dans chaque sens et note : écran de chargement, image figée, clignotement, ciel, entités visibles.
7.  Répéter 50 bascules automatiques et mesurer la mémoire du client (nombre de mondes clients conservés, fuites).
8.  **Repli.** Si les pistes 1 à 3 échouent, installer Immersive Portals pour NeoForge 1.21.1 (version stock) et refaire l’essai avec son API de bascule et de préchargement.

**Mesures :** présence d’un écran de chargement (journal client), durée de la bascule, temps de l’image la plus longue autour de la bascule (proposition : inférieure à 200 ms), chunks présents à l’arrivée, mémoire et nombre de mondes clients après 50 bascules, avis de l’utilisateur.

**Critères.** Réussite : aucun écran de chargement, une image figée de moins de 200 ms au plus, aucune fuite, le joueur reste sur la Mini (si présent). PARTIEL : bascule avec un fondu ou un chargement très bref à masquer. Échec : écran de chargement systématique, plantage, ou sub-level non suivi après la bascule.

**Si échec.** Rapporter précisément la piste qui bloque ; l’architecture prévoit le repli sur Immersive Portals, ou sur un fork limité aux fonctions de changement de monde client.

### A9 — Transfert de sub-level entre dimensions

| **Champ** | **Contenu** |
| --- | --- |
| **Dépendances** | A1, A6. |
| **Intervention manuelle** | Selon les cas : [MANUEL-OUTILLAGE] si la compilation de la demande n° 1474 exige un outil absent (Docker pour les bibliothèques natives de Sable). |
| **Pourquoi** | Le transfert d’une Mini entre hôtes doit tenir dans un tick (ou être étalé). Trois mécanismes restent à comparer. |

**Procédure**

1.  Préparer des Minis S et L (avec coffres, entités à bord, un armor stand, un joueur simulé ou réel), dans `void_a`, et une destination `void_b` de même hauteur, puis une destination de hauteur différente.
2.  **Mécanisme 1 : copie de blocs** (trois passes : blocs sans effets de bord, block entities, notification des voisins).
3.  **Mécanisme 2 : demande n° 1474** (`SubLevelTransferService.transfer`). Récupérer `refs/pull/1474/head` et l’intégrer à une version de Sable compilable. Si impossible, noter BLOQUÉ pour ce mécanisme.
4.  **Mécanisme 3 : sérialisation et rechargement maison** (sérialiser, retirer avec la raison « retiré », désérialiser dans la destination avec la pose voulue).
5.  Pour chaque mécanisme et chaque taille, mesurer 20 transferts : durée sur le fil serveur (p50 et p95), coût hors tick si étalé, mémoire.
6.  Vérifier après chaque transfert : blocs, contenu des coffres, entités du plot, pose, vitesse appliquée, identifiant, points de suivi.
7.  Hauteur : transférer vers une dimension de plage verticale plus petite ; relever le comportement (erreur, perte de blocs).
8.  Échec provoqué : faire échouer le transfert à mi-parcours (par exemple dimension de destination absente) ; vérifier que la source n’est pas perdue.
9.  Cinq transferts aller-retour entre deux dimensions ; vérifier l’absence de boucle à la frontière quand la vitesse est transférée, et de plantage du rendu client si un client est connecté.

**Mesures :** durées p50/p95 par mécanisme et taille, intégrité, comportement à la limite de hauteur, comportement en cas d’échec.

**Critères (valeurs proposées).** Réussite : p95 inférieur à 25 ms pour S et inférieur à 50 ms pour L ; intégrité complète ; échec propre sans perte de la source. PARTIEL : p95 de L supérieur à 50 ms, ce qui exige d’étaler le transfert. Échec : perte de données, blocs hors hauteur perdus sans erreur, ou source perdue après un échec.

**Si échec.** Rapporter le mécanisme le moins coûteux et fiable ; le choix du mécanisme revient à l’utilisateur ([MANUEL-DÉCISION]).

### A10 — Shaders avec Sable

| **Champ** | **Contenu** |
| --- | --- |
| **Dépendances** | A3, A8. |
| **Intervention manuelle** | Oui : [MANUEL-CLIENT] et [MANUEL-VISUEL]. |
| **Pourquoi** | Sable est intrusif et Iris/Sodium modifient le rendu ; le changement de dimension et les sub-levels en mouvement doivent s’afficher correctement. |

**Procédure**

1.  Installer Sodium et Iris (ou Oculus) compatibles avec NeoForge 1.21.1, et un pack de shaders courant. Noter les versions.
2.  [MANUEL-CLIENT] [MANUEL-VISUEL] Rejouer les scénarios A3 (téléportation de pose) et A8 (changement de dimension), plus un vol de 2 minutes dans la Tampon Spatiale, avec et sans shaders.
3.  Relever : écran noir, ombres du sub-level manquantes, texture ou ciel corrompus, plantage, chute de performances (images par seconde avec et sans shaders).

**Critères.** Réussite : aucun défaut de rendu spécifique à Sable ou à la bascule ; performances dans un écart de 30 % avec le même scénario sans mod de voyage. Échec : plantage ou défaut persistant.

**Si échec.** Rapporter la combinaison défectueuse ; l’architecture n’exige pas la compatibilité avec tous les packs, mais l’utilisateur doit décider de la liste prise en charge.

## 5. Couche stratégique (hors du jeu)

Ces tests s’exécutent avec des prototypes autonomes (JUnit ou équivalent) de la logique stratégique, sans Minecraft. Quand la phase 1 est livrée, ils sont rejoués sur le code réel. Toutes les valeurs de position utilisent la double précision.

### B1 — Rails et temps commun : déterminisme

| **Champ** | **Contenu** |
| --- | --- |
| **Dépendances** | Aucune. |
| **Intervention manuelle** | Non. |

**Procédure**

1.  Implémenter le calcul d’une orbite à partir d’un entier long de ticks et des paramètres (période, rayon, phase, inclinaison), avec la phase calculée par modulo de la période.
2.  Comparer avec une version qui accumule des flottants à chaque tick, sur 10⁶, 10⁹ et 10¹² ticks.
3.  Évaluer des positions imbriquées (lune autour d’une planète autour d’une étoile) dans le référentiel du système.
4.  Calculer les mêmes positions dans deux machines virtuelles Java distinctes et en parallèle sur plusieurs fils.
5.  Dans le jeu (quand disponible), comparer serveur et client après 1 heure avec `/time set`, `/tick freeze` et `/tick sprint` utilisés.

**Mesures :** écart entre calcul par modulo et par accumulation, écart entre machines virtuelles, écart serveur/client.

**Critères.** Réussite : écart serveur/client inférieur à 1e-9 en valeur relative ; calcul par modulo stable aux grandes valeurs ; résultats identiques d’une exécution à l’autre. Échec : dérive croissante ou résultat dépendant de l’ordre ou du fil.

### B2 — Rattrapage de simulation et états détruits

| **Champ** | **Contenu** |
| --- | --- |
| **Dépendances** | Aucune. |
| **Intervention manuelle** | Non, sauf [MANUEL-DÉCISION] sur le modèle de mouvement. |

**Hypothèse de test :** une Mini sans personne dedans suit une trajectoire rectiligne uniforme (pas de simulation à n corps) ; les astres sont sur rails ; les Micros ont des événements prédits. L’agent signale à l’utilisateur toute hypothèse qui lui semble fausse.

**Procédure**

1.  Construire des scénarios : (a) Mini contre astre ; (b) Mini contre Mini ; (c) Mini contre Micro ; (d) trois objets avec plusieurs collisions possibles ; (e) aucune collision.
2.  Pour chaque scénario, comparer le rattrapage (un seul pas sur une longue durée) avec une simulation par petits pas (1 tick) sur de courtes durées, pour valider l’ordre et le résultat des événements.
3.  Résultats attendus : astre plus important, Mini marquée détruite ; Mini contre Mini, les deux détruites ; Mini contre Micro, Mini détruite et Micro oubliée.
4.  Vérifier que les objets détruits sont conservés (non oubliés) et restaurables par une commande d’opérateur, à l’état antérieur à la destruction.
5.  Durées de rattrapage : 1 heure, 1 jour et 1 mois de jeu. Performance : 10 000 Minis et 1 000 Micros.

**Mesures :** exactitude des événements, ordre chronologique, durée de calcul.

**Critères.** Réussite : événements identiques à la simulation par petits pas ; collisions résolues dans l’ordre chronologique ; durée acceptable (proposition : moins de 1 s pour 10 000 Minis sur un mois de jeu) ; restauration correcte. Échec : événement manqué ou dans le mauvais ordre.

### B3 — Groupes de rencontre

| **Champ** | **Contenu** |
| --- | --- |
| **Dépendances** | Aucune. |
| **Intervention manuelle** | Non. |

**Procédure**

1.  Implémenter la formation de groupes par proximité avec hystérésis (seuil d’entrée plus petit que seuil de sortie) pour des Minis dans la Tampon Spatiale d’un même système.
2.  Scénarios : A rejoint B ; C rejoint le groupe ; B s’éloigne ; deux groupes fusionnent ; un groupe se scinde ; chaîne A–B–C où A et C sont éloignés ; objets de systèmes différents à coordonnées égales (ne se rencontrent pas).
3.  Tests à propriétés : trajectoires aléatoires (graine fixée), vérifier que le résultat ne dépend pas de l’ordre d’itération.
4.  Bruit : ajouter un petit bruit autour du seuil et compter les changements de groupe.

**Critères.** Réussite : résultat déterministe ; au plus un changement de groupe pour un franchissement de seuil ; aucune fusion entre systèmes différents. Échec : oscillations, dépendance à l’ordre.

### B4 — Chargement et déchargement d’un système stellaire

| **Champ** | **Contenu** |
| --- | --- |
| **Dépendances** | Aucune. |
| **Intervention manuelle** | Non. |

**Procédure**

1.  Implémenter la règle : un système est chargé quand au moins un joueur s’y trouve ; après le départ du dernier joueur, il attend le délai de déchargement (60 s en test) avant d’être déchargé.
2.  Scénarios : premier joueur qui arrive ; reconnexion avant le délai (annule le déchargement) ; après le délai (déchargement puis chargement) ; deux joueurs dans deux systèmes ; un saut qui déplace un joueur d’un système à un autre (le système d’arrivée est chargé avant le déchargement de l’autre) ; arrêt du serveur pendant le délai.
3.  À chaque chargement, vérifier que le rattrapage est déclenché une fois, avec la durée écoulée depuis le déchargement.

**Critères.** Réussite : chargement et déchargement conformes à la règle ; rattrapage appelé exactement une fois par chargement ; durée écoulée exacte ; aucun double chargement. Échec : rattrapage manqué ou répété.

## 6. Intégration

### C1 — Hôte temporaire : deux Minis

| **Champ** | **Contenu** |
| --- | --- |
| **Dépendances** | A3, A4, A5, B3. |
| **Intervention manuelle** | Oui : [MANUEL-CLIENT] (deux joueurs) et [MANUEL-VISUEL]. |

**Procédure**

1.  Dans la Tampon Spatiale, deux Minis occupées (une par joueur) se rapprochent dans la couche stratégique. À l’entrée dans le groupe, positionner leurs poses par téléportation, en reproduisant leur position et leur orientation relatives, et appliquer leur vitesse relative.
2.  Plafonner la vitesse relative par le mass lock (valeur de test à demander : [MANUEL-DÉCISION]). Faire s’approcher les Minis à des vitesses relatives croissantes, y compris la vitesse maximale, avec une coque d’un bloc d’épaisseur : 100 passages par vitesse.
3.  Ajouter une troisième Mini, puis en retirer une (fusion et scission). Si une Mini sans joueur (en données) rejoint le groupe, la désérialiser.
4.  [MANUEL-VISUEL] Les deux joueurs notent : saut visible des poses, coque traversée, cohérence de ce que chacun voit de l’autre.

**Mesures :** nombre de traversées de coque, saut de pose par image, durée de formation du groupe, TPS.

**Critères.** Réussite : aucune traversée de coque à la vitesse maximale plafonnée ; sauts de pose invisibles ou masqués ; TPS stable. Échec : traversées, désynchronisation des positions relatives.

### C2 — Préchargement d’une dimension hôte

| **Champ** | **Contenu** |
| --- | --- |
| **Dépendances** | A1. |
| **Intervention manuelle** | [MANUEL-DÉCISION] : valeurs de R, de M et de la vitesse imposée par le mass lock près d’une planète. |

**Procédure**

1.  Mesurer, pour une dimension de type planète (terrain plat généré, puis terrain déjà généré sur disque), le temps entre `loadDynamicDimension` et la disponibilité de chunks dans un rayon de 8, 16 et 32 chunks (serveur puis client).
2.  Calculer le temps disponible : (distance entre le volume d’exclusion et R − M) divisée par la vitesse du mass lock. Comparer au temps de chargement mesuré.
3.  Faire varier les paramètres (R, M, vitesse) pour trouver la limite.

**Mesures :** temps de chargement par rayon, avec ou sans génération ; temps disponible ; marge.

**Critères.** Réussite : temps de chargement inférieur au temps disponible avec une marge d’au moins 2 pour les valeurs fournies par l’utilisateur. Échec : le chargement n’est pas terminé à l’entrée. Dans ce cas, rapporter les valeurs de vitesse maximale ou de distance qui conviendraient.

### C3 — Frontière d’un hôte avec la marge M

| **Champ** | **Contenu** |
| --- | --- |
| **Dépendances** | A8, A9. |
| **Intervention manuelle** | Non. |

**Procédure**

1.  Un hôte de test de rayon R. Une Mini de rayon connu, M calculée par la formule de test (M égale au rayon de la Mini) : l’agent signale si la formule lui paraît inadaptée.
2.  La Mini oscille autour de R avec des amplitudes de M/2, M, 2M et 4M. Compter les transferts.
3.  Vérifier que l’entrée a lieu à R − M et la sortie à R + M, pour une forme sphérique puis non sphérique (un tore).
4.  Mille franchissements d’affilée : coût de chaque transfert, mémoire, fuite de niveaux ou de plots.
5.  Vérifier la continuité de la vitesse stratégique (vitesse de l’hôte plus vitesse locale) avant et après chaque franchissement, et la conservation des passagers.

**Critères.** Réussite : aucun transfert quand l’amplitude reste inférieure à M ; entrée à R − M et sortie à R + M ; vitesse stratégique continue (écart relatif inférieur à 1e-6) ; aucune fuite sur 1 000 franchissements. Échec : va-et-vient, perte de passager ou de vitesse.

### C4 — Voyage entre systèmes : saut et parcours

| **Champ** | **Contenu** |
| --- | --- |
| **Dépendances** | B1. |
| **Intervention manuelle** | Oui : [MANUEL-VISUEL]. |

**Procédure**

1.  **Saut :** changer l’objet de système dans la couche stratégique avec un effet de masquage. Vérifier que position, orientation et vitesse sont celles prévues dans le nouveau référentiel.
2.  **Parcours :** vol de 10⁹ unités loin de tout astre ; vérifier la continuité de position au changement de référentiel (écart inférieur à 1e-6 en valeur relative), et le comportement du mass lock (aucune limite loin de tout astre).
3.  [MANUEL-VISUEL] L’utilisateur note : le saut est-il masqué ? Le changement de référentiel pendant le parcours est-il imperceptible ?
4.  Vérifier que le ciel (planètes, étoile du nouveau système) se met à jour et que le chargement du système d’arrivée précède le déchargement de l’autre.

**Critères.** Réussite : continuité numérique ; changement imperceptible en parcours ; saut masqué. Échec : saut visible ou valeur incohérente.

### C5 — Arrêt propre et plantage

| **Champ** | **Contenu** |
| --- | --- |
| **Dépendances** | A6, A7. |
| **Intervention manuelle** | Selon les cas : [MANUEL-CLIENT] si un vrai joueur est à bord. |

**Procédure**

1.  Mettre plusieurs Minis occupées dans la Tampon Spatiale, plus d’autres en données.
2.  **Arrêt propre :** arrêter le serveur ; redémarrer ; vérifier que chaque Mini et chaque joueur est restauré.
3.  **Plantage :** tuer le processus à un instant choisi après une sauvegarde automatique ; redémarrer ; mesurer ce qui est perdu.
4.  Vérifier : aucune Mini dupliquée, aucune Mini orpheline, pas de plot occupé inutilement, données des Minis écrites de façon atomique (fichier temporaire puis renommage).
5.  Planter volontairement pendant la sérialisation, si c’est possible.

**Critères.** Réussite : arrêt propre sans perte ; plantage, perte limitée au temps écoulé depuis la dernière sérialisation ; aucune corruption. Échec : doublon, corruption ou perte supérieure à l’intervalle de sauvegarde.

### C6 — Multijoueur

| **Champ** | **Contenu** |
| --- | --- |
| **Dépendances** | C1, A8. |
| **Intervention manuelle** | Oui : [MANUEL-CLIENT] avec au moins deux joueurs réels ; les montées en charge peuvent utiliser des clients simulés. |

**Procédure**

1.  Avec 2, 4 puis 8 joueurs : chacun dans sa Mini dans la Tampon Spatiale, avec deux hôtes (un croiseur, une planète) et des rencontres.
2.  Relever le TPS (Spark), le temps de tick par dimension, la bande passante par joueur, le nombre de plots occupés.
3.  [MANUEL-VISUEL] Vérifier que chaque joueur voit les bons objets dans son ciel, que les positions répliquées sont fluides (interpolation) et qu’une bascule d’un joueur n’affecte pas les autres.
4.  Un joueur se déconnecte pendant les rencontres ; vérifier le délai de sérialisation et l’absence de blocage.

**Critères.** Réussite : TPS supérieur ou égal à 19,5 avec 8 joueurs ; plots de la Tampon Spatiale sous le plafond ; ciels cohérents. Échec : TPS dégradé ou désynchronisation.

### C7 — Effet d’inertie sur les Minis d’un hôte mobile

| **Champ** | **Contenu** |
| --- | --- |
| **Dépendances** | A1, A4, A5, C3. |
| **Intervention manuelle** | Selon les cas : [MANUEL-VISUEL] facultatif avec un joueur à bord. |

**Procédure**

1.  Un hôte de test (grand vaisseau, volume logique de rayon R) contenant : trois Minis couplées (posées sur un sol, gelées par contrainte fixe), trois Minis non couplées en suspension, dont une avec un joueur à bord.
2.  Faire varier le vecteur de déplacement de l’hôte dans la couche stratégique : accélération douce, forte (par exemple 5 g), freinage, changement de direction, avec l’hôte orienté de plusieurs façons.
3.  À chaque variation Δv de l’hôte, appliquer aux Minis non couplées la variation de vitesse locale opposée, exprimée dans le référentiel de l’hôte. Vérifier que la variation mesurée est celle attendue (écart inférieur à 1 %).
4.  Vérifier que les Minis couplées ne bougent pas (dérive inférieure à 0,01 bloc).
5.  Poursuivre l’accélération jusqu’à ce qu’une Mini non couplée atteigne R + M : elle doit passer dans la Tampon Spatiale avec une vitesse stratégique inchangée (écart relatif inférieur à 1e-6).
6.  Cas limites : hôte dont la dimension est déchargée pendant la variation (l’effet ne doit pas s’appliquer) ; plusieurs Minis éjectées en cascade ; accélérations répétées sans sortie ; Mini qui se couple pendant la variation.
7.  [MANUEL-VISUEL] Avec un joueur à bord d’une Mini non couplée, il note si la poussée vers l’arrière est cohérente et si l’éjection est fluide.

**Mesures :** variation de vitesse locale attendue et mesurée, dérive des Minis couplées, vitesse stratégique avant et après la sortie, TPS pendant l’effet.

**Critères.** Réussite : poussée dans le sens opposé à l’accélération et d’amplitude correcte ; Minis couplées immobiles ; continuité de la vitesse stratégique à la sortie ; simulation stable pour de fortes accélérations. Échec : sens ou amplitude erronés, instabilité, perte de vitesse à la sortie.

## 7. Récapitulatif des interventions manuelles

| **Test** | **Étiquettes** | **Ce qu’il faut de l’utilisateur** |
| --- | --- | --- |
| **A3** | CLIENT, VISUEL | Se connecter, marcher sur la Mini, juger les sauts de pose. |
| **A4** | VISUEL (facultatif) | Juger le confort de la rotation. |
| **A7** | CLIENT | Se connecter et se déconnecter à des moments précis. |
| **A8** | CLIENT, VISUEL | Faire 10 bascules dans chaque sens, juger l’écran de chargement. |
| **A9** | OUTILLAGE (selon les cas) ; DÉCISION | Fournir Docker si nécessaire ; choisir le mécanisme de transfert. |
| **A10** | CLIENT, VISUEL | Rejouer des scénarios avec shaders. |
| **B2** | DÉCISION (selon les cas) | Valider le modèle de mouvement d’une Mini sans joueur. |
| **C1** | CLIENT (deux joueurs), VISUEL | Rencontre de deux Minis ; valeur du mass lock. |
| **C2** | DÉCISION | Valeurs de R, M et de vitesse du mass lock. |
| **C4** | VISUEL | Juger le saut et le parcours. |
| **C5** | CLIENT (selon les cas) | Être à bord lors d’un arrêt. |
| **C6** | CLIENT (au moins deux joueurs) | Jouer à plusieurs. |
| **C7** | VISUEL (facultatif) | Juger la poussée avec un joueur à bord. |

## 8. Valeurs à demander à l’utilisateur avant de commencer

1.  Valeurs de R (volume logique) et de la formule de M pour les hôtes de test (C2, C3).
2.  Vitesse maximale imposée par le mass lock près d’une planète, d’un croiseur et d’une autre Mini (C1, C2).
3.  Délai de sérialisation et délai de déchargement d’un système (valeurs de test données en section 2.2).
4.  Modèle de mouvement d’une Mini sans joueur pendant le rattrapage (B2).
5.  Accord pour compiler Sable depuis la branche de la demande n° 1474 (A9).
6.  Packs de shaders et versions d’Iris et de Sodium à prendre en charge (A10).
