# Plan d'architecture

*Mod Minecraft de voyage spatial seamless*

*Version 6 · 21 octobre 2026 · premier jalon sur les objets hôtes, Minis et passage seamless différés*

## 1. Objectif et périmètre

Voyager dans l'espace sans écran de chargement, à la manière de Star Citizen, Elite Dangerous ou No Man's Sky. Les lieux (planètes, stations, croiseurs, mégastructures) sont des dimensions. Les petits vaisseaux (Minis) sont des objets physiques simulés seulement quand un joueur est concerné.

**Premier jalon : les objets hôtes.** Couche stratégique, dimensions persistantes des hôtes, ciel et proxies, modèles spatiaux, navigation, voyage entre systèmes. Un joueur change d'hôte par le changement de dimension vanilla (avec écran de chargement), derrière une interface.

**Différé :** Minis, Micros, batailles, passage seamless, bouclier. Leur conception est résumée aux sections 10 et 11, avec les contraintes déjà établies par les tests.

**Changements depuis la v5 :** périmètre en deux temps ; résultats de la phase 0 intégrés ; bibliothèque de dimensions décidée ; Tampon Spatiale à revoir ; passage seamless reporté.

## 2. Principes directeurs

- **Le ciel est un rendu, pas un monde.** L'espace est dessiné à partir de données, pas construit en blocs.
- **Les données d'abord.** Tout objet existe comme donnée ; il n'est simulé physiquement que là où un joueur en a besoin.
- **Un lieu = une dimension statique et persistante,** sans physique de corps rigide, créée ou chargée à l'exécution.
- **Le mouvement est une donnée.** Un croiseur se déplace en changeant son transform stratégique ; un objet sur rail a une position fonction du temps.
- **Un objet se définit par ses composants,** pas par une classe fixe.
- **Les transitions sont masquées, pas supprimées** (montée en puissance, fondu, saut).
- **Les briques risquées sont isolées derrière des interfaces** : bibliothèque de dimensions, transfert d'un joueur entre dimensions.

## 3. Plateforme et décisions

| **Sujet** | **Décision** |
| --- | --- |
| **Versions** | NeoForge, Minecraft 1.21.1, Java 21 (banc d'essai : NeoForge 21.1.262). |
| **Physique** | Sable 2.0.6 seul (il embarque Veil), sans Create Aeronautics ; propulseurs et ballons à écrire. IPSable : référence seulement. Licence de Sable (Polyform Shield) : dépendre de son API, ne pas copier son code sans l'avoir lue. |
| **Bibliothèque de dimensions** | Madnessverse 1.0.0 (MIT) derrière une interface du mod ; fork possible (section 3.1). |
| **Cycle de vie des dimensions** | Premier jalon : création et conservation. Déchargement et destruction : **dette technique assumée**, nécessaire avant les rencontres de Minis. |
| **Physique et types de dimension** | Physique fournie par Sable, enregistrée dès `ServerAboutToStartEvent` ou par datapack (sinon gravité par défaut au redémarrage). Types déclarés en datapack ; un lieu éclairé a `has_skylight=true`. |
| **Plage verticale** | Identique pour toute dimension susceptible d'accueillir des Minis. Valeur à fixer avant de créer les premières dimensions. |
| **Temps et positions** | Temps commun : entier long de ticks, indépendant de `/time set`. Phase d'orbite par `floorMod`, jamais accumulée. `StrictMath` pour les positions partagées. Position stratégique en double (cellule entière plus décalage en option), **jamais en float**. |
| **Changement de dimension** | Vanilla derrière une interface. Immersive Portals : dernier recours (section 11). |
| **Tampon Spatiale** | À revoir (section 10.5). Non créée au premier jalon. |
| **Source d'inspiration** | Mod d'ultraFLIGHT (sortie gratuite annoncée pour Halloween, basé sur Sable). Pas une dépendance. |

### 3.1 Bibliothèque de dimensions

| **Exigence** | **Niveau** | **Statut** |
| --- | --- | --- |
| Créer des dimensions à l'exécution | Obligatoire | Couverte par Madnessverse (10 cycles sur 10). |
| Donner des propriétés (physique, type) | Recommandée | Couverte par Sable et les datapacks, pas par la bibliothèque. |
| Décharger ou détruire des dimensions | Optionnelle, reportée | Dette technique. |
| Développer la brique soi-même | Possible | Préférer un fork de Madnessverse à une écriture de zéro. |

DynamicDimensions plante au déchargement d'une dimension contenant un sub-level (version 0.9.1 testée ; la 0.10.0 visée au départ n'a pas été essayée). Madnessverse n'a pas ce défaut, mais :

- il ne supprime pas les fichiers d'une dimension et n'a pas de déchargement distinct de la suppression ;
- il restaure seul toutes les dimensions enregistrées au chargement du monde : le coût avec de nombreuses planètes est à mesurer, et la liste de la couche stratégique doit être réconciliée avec les dimensions restaurées ;
- le projet est jeune (1.0.0 d'août 2026).

## 4. Architecture générale

1. **Couche stratégique (serveur).** Source de vérité des positions et mouvements de tous les objets, en double précision, découpée en systèmes stellaires.
2. **Couche dimensions (serveur).** Une dimension persistante par objet qui en possède une, créée via l'interface de bibliothèque. Sable y simule les objets physiques.
3. **Couche rendu (client).** Ciel spatial, proxies de planètes et modèles spatiaux, alimentés par les données stratégiques répliquées.

Les dimensions ne se parlent jamais directement : elles lisent et publient leur état dans la couche stratégique.

## 5. Couche stratégique

**SpaceObject.** Liste extensible de composants : Identité, Transform (position double, orientation, vitesse), Render, Controller (rail, rotation, pilotage…), HostedDimension, PortsPoints (sites d'atterrissage), Forgettable (Micros seulement), Volumes, plus des composants spécifiques ajoutés sans toucher aux autres.

**Systèmes stellaires.** Chacun a son référentiel ; deux objets de systèmes différents ne se rencontrent jamais. On passe d'un système à l'autre par un **saut** (téléportation masquée, par défaut) ou par un **parcours** (réacteur suffisant, changement de référentiel imperceptible). Numériquement validé (C4) ; le masquage visuel reste à juger en jeu.

**Système chargé.** Chargé quand au moins un joueur s'y trouve ; après le départ du dernier, il attend un délai configurable avant d'être déchargé. Un système non chargé n'est pas simulé : les objets sur rails restent exacts grâce au temps commun. Règle validée en prototype (B4).

**Rails et temps commun.** Positions calculées à partir du temps entier et de `floorMod(ticks + phase, période)`. L'accumulation de flottants dérive (0,91 bloc à 10^9 ticks, orbite fausse à 10^12). Seuls les paramètres des rails et le temps sont envoyés au client.

**Persistance.** L'état stratégique est sauvegardé avec le monde (rails : paramètres seuls). La couche stratégique tient la liste des dimensions hébergées et la réconcilie au démarrage avec celles que la bibliothèque a restaurées. Un objet détruit est conservé, marqué, et restaurable par un opérateur.

## 6. Archétypes et volumes

| **Archétype** | **Exemples** | **Composants typiques** |
| --- | --- | --- |
| **Astre** | Étoile, trou noir | Transform, Render, Controller (rail ou rotation) |
| **Planète** | Planète, lune | Astre + HostedDimension + PortsPoints multiples |
| **Mégastructure** | Anneau, sphère de Dyson | HostedDimension, rail, PortsPoints avec capacité d'approche |
| **Superstructure** | Station | HostedDimension, rail orbital, un PortsPoint à 360° |
| **Structure** | Croiseur | HostedDimension, pilotage, un PortsPoint à 360° ; capacités selon son noyau |
| **Mini** *(différé)* | Navette, chasseur, joueur perdu | Controller ; objet physique dans un hôte, ou donnée seule |
| **Micro** *(différé)* | Projectile, débris, missile | Controller, Forgettable ; ne crée jamais d'hôte |

**Volume logique.** Zone de rayon R autour d'un objet qui a une dimension ; c'est aussi la taille de cette dimension. Pas forcément une sphère (un Halo praticable ne l'est pas).

**Volume d'exclusion.** Volume logique élargi (2R pour une sphère). Il agit comme une collision : un objet massif (croiseur) n'y entre pas librement.

**Marge M** *(Minis, différé)*. Un objet entre dans une dimension hôte à R − M et en sort à R + M, ce qui évite les va-et-vient. M couvre le rayon circonscrit de la **plus grande Mini admise** (C3) ; la dimension s'étend donc jusqu'à R + M.

**Autres règles.** Si deux rails se recouvrent, le plus petit et le plus proche l'emporte. Une approche hors d'un port génère un nouveau port ou est refusée (alerte, dégâts, destruction). Pas d'amarrage entre objets massifs. Les objets hors ligne gardent leur place et leur volume d'exclusion.

Au premier jalon, seuls l'exclusion, le rendu et le préchargement s'appliquent : aucun objet n'entre seul dans un hôte.

## 7. Planètes et structures

**Planètes.** Une dimension persistante par planète, chargée à la demande. Ciel : le système stellaire vu depuis la planète. Carte plate avec bouclage par site d'atterrissage ; **sites pré-générés**, car un terrain réaliste généré prend 10,9 s à 16 chunks et 33,9 s à 32 (C2). Un seul ciel par dimension, justifié par la position (x et z = longitude et latitude) ; la limite de carte se justifie par une rotation synchrone, une zone praticable bornée ou plusieurs sites.

**Structures et stations.** Une dimension statique et persistante, intérieur normal avec gravité artificielle. Elle contient la coque et du vide jusqu'au rayon R (plus M quand les Minis existeront). Ciel : planètes en proxies, autres objets en modèles spatiaux. Une station suit un rail orbital ; sa planète tourne dans le ciel. Les croiseurs sont pilotés, restent dans l'espace et ne rejoignent jamais une planète. La « hauteur Y augmentée » des stations entre en conflit avec la plage verticale commune : à trancher.

**Sorties extravéhiculaires.** Le joueur dehors est une Mini (différé) ; limiter la rotation du croiseur en manœuvre, ou équiper le joueur de bottes magnétiques ou d'un câble.

## 8. Rendu

- **Ciel :** dessiné par le moteur du proof of concept (étoiles, étoile du système, proxies, modèles spatiaux), piloté par l'objet associé à la dimension du joueur et par sa position dedans.
- **Visibilité :** un objet est rendu quand sa taille apparente dépasse un seuil (à calibrer) ; la même règle donne un petit objet proche et un gros objet lointain.
- **Modèles spatiaux :** générés depuis les blocs du vaisseau, plusieurs niveaux de détail, régénération différée après modification, ouvertures importantes incluses (hangar), fondu entre modèle et rendu réel.

## 9. Navigation et distances

Problème : depuis un croiseur, la planète ne grossit pas tant que le croiseur reste dans sa dimension.

- **Continuité de position :** position d'un objet = celle de son hôte plus son décalage local, sans facteur de conversion.
- **Deux régimes :** propulseurs de manœuvre dans le volume logique, moteur de croisière au-delà, avec une montée en puissance de 2 à 3 s qui masque le changement.
- **Mass lock :** la vitesse maximale dépend de la distance au corps massif le plus proche ; aucune limite loin de tout astre.
- **Préchargement :** de l'entrée du volume d'exclusion jusqu'au transfert. Le temps disponible (distance divisée par la vitesse du mass lock) doit dépasser le temps de chargement. Au premier jalon, il prépare la dimension avant un transfert vanilla.
- **Mesure (C2, serveur seul, valeurs de test) :** marge de 2,8 pour une planète à 16 chunks, 0,9 à 32 ; 1,3 pour un croiseur, mais mesuré avec un terrain d'Overworld peu représentatif. Pistes : rayon d'environ 16 chunks, pré-génération, coefficient du mass lock entre 0,02 et 0,1. Le client n'a pas été mesuré.

## 10. Minis (différé)

Une Mini n'a pas de dimension propre et n'est jamais oubliée. Elle est un sub-level Sable dans un hôte, ou une donnée sérialisée quand elle est en vol libre sans occupant.

### 10.1 États

| **État** | **Détail** |
| --- | --- |
| **Données** | Sérialisée dans la couche stratégique, aucun plot occupé ; réservée au vol libre. Une Mini qui est dans l'hôte d'un objet supérieur n'est jamais sérialisée. |
| **Dormante** | Gelée dans un hôte (contrainte fixe au monde) ; reste dans l'hôte même inoccupée. |
| **Active** | Simulée physiquement ; dans un hôte d'objet supérieur, elle l'est même sans occupant. |
| **Détruite** | Injoignable, restaurable par un opérateur. |

### 10.2 Validé par les tests

- **Transfert entre dimensions** par sérialisation puis rechargement : 4 ms en médiane pour les tailles S et L, intégrité complète (A9). C'est le mécanisme retenu.
- **Frontière avec marge M :** 1 315 franchissements sans va-et-vient, sans passager perdu, sans fuite de plot, vitesse stratégique continue (C3).
- **Effet d'inertie :** une impulsion opposée à la variation de vitesse de l'hôte est appliquée à chaque Mini non couplée ; exact à 1e-6, éjection en cascade correcte (C7). Rotation de l'hôte non traitée.
- **Position verrouillée, rotation libre :** dérive inférieure à 4e-5 bloc sur 6 000 ticks (A4).
- **Déconnexion, arrêt propre, plantage :** Mini et joueur restaurés ; perte limitée à l'intervalle de sauvegarde (A7, C5). Le mod enregistre lui-même la position relative du joueur, écrit les fichiers de façon atomique, purge les `.tmp` et vérifie l'UUID après restauration.
- **Rattrapage de simulation** (système non chargé) : événements identiques à la simulation tick par tick (B2). Astre plus gros : Mini détruite ; Mini contre Mini : les deux ; Micro : Mini détruite, Micro oubliée.
- **Groupes de rencontre** avec hystérésis, déterministes (B3).

### 10.3 Contraintes établies sur Sable

- Un sub-level sans joueur à proximité est déchargé en quelques ticks : ticket de chargement forcé nécessaire.
- Coût d'environ 0,0076 ms par tick par sub-level au repos, proportionnel (mesure sans Spark) ; le plafond de 16 384 plots est théorique.
- La contrainte de gel n'est pas persistée : à recréer à chaque chargement.
- Au rechargement, la vitesse est multipliée par 0,9 et une impulsion au tick de création est ignorée : réappliquer la vitesse 2 ticks plus tard.
- `SubLevelData` ne contient aucune entité : le mod les range dans `user_data` et les recrée.
- L'indice de plot est conservé ; en cas de conflit, `fullyLoad` renvoie `null`. Il faut une allocation de plots unique, ou la demande n° 1474 de Sable (décalage de plot et de hauteur, **non testée**, Docker requis).
- Une plage verticale différente corrompt le transfert.
- Les passagers sont téléportés séparément ; leurs entités ne sont chargées que quelques ticks après un rechargement.

### 10.4 Points non concluants

- **Saut de pose :** fiable jusqu'à 300 blocs (balayage d'environ 60 ms) ; au-delà, Sable ne porte plus le joueur et le contournement par le mod est instable (A3).
- **Collisions entre sub-levels :** une coque d'un bloc est traversée dans 70 passages sur 100 à 5 blocs/s, cause non élucidée (C1). À retester avec de vrais vaisseaux de Sable. Une exclusion avant contact gérée par le mod est probablement nécessaire.

### 10.5 Modèles de rencontre à l'étude

| **Modèle** | **Principe** | **Pour** | **Contre** |
| --- | --- | --- | --- |
| **A. Tampon + hôte temporaire (v5)** | Minis en vol libre dans une dimension partagée ; rencontre dans une zone de celle-ci, par saut de pose | Une seule dimension | Limite de 300 blocs |
| **B. Une dimension par rencontre** *(à prototyper)* | Dimension temporaire créée au regroupement, Minis transférées par sérialisation, détruite ensuite | Pas de grand saut de pose ; même mécanisme de bascule partout | Exige la destruction de dimensions ; coût de N dimensions |
| **C. Une dimension par Mini occupée** | Plus de Tampon | Même mécanisme partout | Coût de N dimensions vides |

Le modèle B est retenu pour le prototype, sans engagement ; d'autres propositions sont bienvenues. Un groupe est une composante connexe de Minis proches.

### 10.6 Batailles et bouclier (futur)

- **Batailles :** plusieurs joueurs et des vaisseaux IA ; l'échelle n'est pas fixée. Il faudra un scénario de référence (nombre de vaisseaux et de joueurs, TPS cible). Piste : un vaisseau IA n'a pas forcément besoin d'être un sub-level ; un objet cinématique avec hitbox, dont les tirs sont testés par rayon, coûterait bien moins. Le sub-level physique serait réservé aux vaisseaux qu'un joueur pilote ou parcourt.
- **Bouclier :** quand il est actif, une bulle de collision physique encapsule le vaisseau. Piste : une primitive commune de bulle de collision, gérée par le mod, qui convertit un contact en impulsions ; elle sert aussi d'exclusion avant contact. Pour un vaisseau-hôte, c'est son volume d'exclusion ; pour les tirs, un test rayon contre sphère. Pas de coque en blocs.

### 10.7 Micros

Données avec événements prédits et condition d'oubli (seul objet à en avoir une). Elles ne créent jamais d'hôte ; elles peuvent entrer dans un hôte existant ou en sortir et redevenir des données. Une collision avec une Mini détruit la Mini et fait oublier la Micro.

## 11. Passage seamless (différé)

**Décision.** Reporté. Premier jalon : changement de dimension vanilla derrière une interface de transfert de joueur. Immersive Portals reste un dernier recours (ou un fork limité au changement de monde client).

**Acquis (A8, A10).**
- Sans paquet de réapparition, plus d'écran de chargement et un changement en moins de 1 ms côté serveur, mais la Mini disparaît 1 à 5 images (17 à 43 ms) à cause de l'ordre des paquets de suivi de Sable.
- Le test utilisait deux dimensions identiques et le niveau client n'est pas remplacé. Hypothèse à vérifier : avec des dimensions différentes, les chunks se mélangeraient et le ciel ne serait pas mis à jour.
- Sodium 0.8.13 et Iris 1.8.14-beta.1 : aucun défaut signalé, mesures de FPS incomplètes.

**Chantier.** Un niveau client par dimension avec des chunks adressés à la bonne dimension ; suppression de l'image vide (retarder l'arrêt de suivi de la Mini, ou fondu bref) ; préchargement des chunks de destination ; vérification des shaders, du multijoueur et de la mémoire.

## 12. Risques

| **Risque** | **Piste** |
| --- | --- |
| **Maturité de Madnessverse** | Interface propre au mod ; fork possible (MIT). |
| **Coût de N dimensions chargées** | Mesurer le démarrage et la mémoire avec de nombreuses planètes. |
| **Déchargement et destruction avec sub-levels** (dette technique) | Retirer sub-levels et tickets avant de fermer le niveau ; supprimer les fichiers soi-même. |
| **Préchargement insuffisant** | Rayon d'environ 16 chunks, pré-génération, mass lock plus lent ; mesurer côté client. |
| **Modèles spatiaux** | Prototype précoce : génération, LOD, régénération différée. |
| **Multijoueur** | Conception réseau dès la première version ; tests à plusieurs joueurs. |
| **Résultats obtenus sur des prototypes et des valeurs de test** | Rejouer B1 à B4 sur le code réel ; remplacer les valeurs de R, M, mass lock et délais. |
| **Minis : saut de pose, collisions, coût au repos** | Section 10 ; prototype du modèle B, retest de C1, mesure avec Spark. |
| **Passage seamless** | Section 11 ; repli Immersive Portals. |

## 13. Tests

Les 21 tests de la phase 0 (A1 à A10, B1 à B4, C1 à C7) ont été exécutés le 20 octobre 2026. Les rapports sont dans `test-results/<N°>/rapport.md` et la synthèse dans `SYNTHESE.md`.

- **Validés :** A2, A4, B1 à B4, C3, C5, C7, et la bibliothèque Madnessverse (A1).
- **Partiels :** A3 (saut de pose), A5, A6, A8, A10, C2, C4, C6.
- **Échecs :** A1 avec DynamicDimensions, C1 (traversée de coque), A3 au-delà de 300 blocs.
- **Réserves :** valeurs de test choisies par les agents (R, M, mass lock) ; B1 à B4 sur prototypes hors jeu ; C3, C5, C6 et C7 sans client réel.
- **À ajouter pour le premier jalon :** coût de N dimensions chargées ; physique enregistrée à `ServerAboutToStartEvent` sur le mod réel ; B1 à B4 sur le code réel ; C2 côté client ; C4 visuel.
- **À ajouter pour la suite :** destruction de dimensions ; prototype du modèle B ; demande n° 1474 ; C1 avec de vrais vaisseaux ; A8 avec des dimensions de types différents.

Un `plan_de_tests.md` version 2 est à écrire.

## 14. Points encore ouverts

**Premier jalon**
- Fork de Madnessverse ou usage tel quel.
- Plage verticale commune (et arbitrage avec la hauteur des stations).
- Point de départ du transfert vanilla (terminal, porte, hangar).
- Valeurs de production : R, vitesse du mass lock, rayon de préchargement, délai de déchargement.
- Le temps de jeu court-il pendant l'arrêt du serveur ?
- Voyage entre systèmes (portée du saut, coût, masquage), arrivée sur une planète, mégastructures (ports, rails), outils d'opérateur.

**Minis (différé)**
- Modèle de rencontre, mécanisme de transfert, allocation de plots.
- Collisions et bulle de collision.
- Formule et plancher de M ; critère de couplage ; rotation de l'hôte ; Mini en données qui entre dans un hôte.
- Modèle de mouvement et convention à tick égal pour le rattrapage.
- Scénario de référence des batailles.
- Plus tard : saut d'un hôte emportant des Minis (éjection, dégâts, occupants agressés voire tués) ; joueur perdu dans l'espace.

## 15. Feuille de route

| **Phase** | **Contenu** | **Critère de réussite** |
| --- | --- | --- |
| **0** | Prototypes de risques (faits) ; interfaces de bibliothèque et de transfert ; coût de N dimensions | Résultats clairs |
| **1** | SpaceObject, systèmes stellaires, couche stratégique, rails, voyage entre systèmes | Positions identiques serveur et clients ; système non chargé à jour au chargement |
| **2** | Ciel piloté par l'objet de la dimension | Planètes en proxies bien placées depuis un croiseur |
| **3** | Dimensions de structures et superstructures, volumes | Deux croiseurs visibles l'un de l'autre sans coupure |
| **4** | Modèles spatiaux | Modèle mis à jour sans saccade après modification |
| **5** | Planètes, sites d'atterrissage pré-générés | Ciel cohérent, pas de saccade à l'arrivée |
| **6** | Navigation : mass lock, deux régimes, préchargement, transfert vanilla | Approche d'une planète continue ; préchargement terminé avant le transfert |
| **7** | Mégastructures, multijoueur, optimisation ; décision sur le déchargement | TPS stable avec plusieurs structures et joueurs |
| **8** *(différé)* | Minis : modèle de rencontre, états, transfert, plots, inertie, bulle de collision | Rencontre sans traversée de coque |
| **9** *(différé)* | Passage seamless | Une navette change d'hôte avec son pilote sans écran de chargement |
| **10** *(différé)* | Micros, batailles, bouclier ; plus tard, saut d'un hôte emportant des Minis | TPS stable sur le scénario de référence |
