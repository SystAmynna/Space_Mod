# Plan d’architecture

*Mod Minecraft de voyage spatial seamless*

*Version 5 · 20 octobre 2026 · marge d’entrée et de sortie des hôtes, systèmes chargés par la présence de joueurs, Minis dans un hôte et effet d’inertie, tests détaillés dans un document séparé*

## 1. Objectif et périmètre

Ce document fixe l’architecture d’un mod permettant de voyager dans l’espace sans écran de chargement, à la manière de Star Citizen, Elite Dangerous ou No Man’s Sky. Les lieux sont des dimensions (planètes, stations, croiseurs, mégastructures) ; les petits vaisseaux (navettes) sont des objets physiques simulés seulement quand un joueur est concerné.

**Nouveautés de cette version :**

- Les hôtes ont une **marge de sécurité M** : on entre dans la dimension d’un hôte à R − M et on en sort à R + M, M étant calculé à partir du rayon de la Mini (sections 7.2 et 13).
- Un **système est chargé** quand au moins un joueur s’y trouve ; après une déconnexion, il attend un délai avant d’être déchargé (section 6.2).
- Une Mini qui se trouve dans l’hôte d’un objet supérieur **y reste**, même inoccupée, et reste simulée physiquement ; la sérialisation en données ne concerne que les Minis en vol libre dans la Tampon Spatiale (section 10.1).
- Quand l’hôte change de trajectoire, les Minis **non couplées** subissent un **effet d’inertie** et peuvent être poussées hors de la dimension (section 10.8).
- Le saut d’un hôte qui emporte des Minis est noté pour plus tard (section 17).
- Les tests sont détaillés dans un document séparé, conçu pour être délégué à des agents (section 16).

**Reste à préciser :** les points listés en section 17.

## 2. Principes directeurs

- **Le ciel est un rendu, pas un monde.** L’espace n’existe pas sous forme de blocs : il est dessiné dans le ciel à partir de données.

- **Les données d’abord.** Tout objet existe d’abord comme donnée spatiale ; il n’est simulé physiquement que là où un joueur en a besoin.

- **Un lieu = une dimension statique.** Planètes, stations, croiseurs et mégastructures sont des dimensions normales, sans physique de corps rigide : pas de coût de simulation lié à leur taille. Ces dimensions sont persistantes ; elles sont créées ou chargées à l’exécution, et ne sont pas instanciées tant que personne ne s’en approche.

- **Le mouvement est une donnée.** Un croiseur se déplace en mettant à jour son transform dans la couche stratégique, pas en déplaçant des blocs. Un objet sur rail n’est pas simulé : sa position est une fonction du temps.

- **Un objet se définit par ses composants, pas par sa taille ni par une classe fixe.** Les règles (exclusion, approche, oubli, rendu) lisent les composants présents.

- **Un changement de dimension doit pouvoir se faire sans écran de chargement,** par le mod lui-même.

- **Les transitions sont masquées, pas supprimées.** Effets (montée en puissance, fondu, saut) pour cacher les changements de référentiel.

## 3. Plateforme technique et décisions actées

| **Sujet** | **Décision** |
| --- | --- |
| **Loader / version** | NeoForge, Minecraft 1.21.1. |
| **Physique** | Sable seul, sans Create Aeronautics. Le jeu démarre avec Sable sans Create ni Veil (testé par l’utilisateur ; Veil est embarqué dans Sable). Les pièces propres à Aeronautics (propulseurs, ballons) sont à écrire. |
| **Transitions sans chargement** | Exigence : le mod doit permettre un changement de dimension seamless. Immersive Portals est **retiré des dépendances pour le moment** ; il reste une solution de repli, et l’objectif est de s’en passer (section 14). |
| **Tampon Spatiale** | Dimension unique pour l’espace vide, sans terrain, partagée par tous les systèmes stellaires. Elle contient les poses des Minis occupées en vol libre et les hôtes temporaires de rencontre. **Seule dimension temporaire** : elle est recréée quand elle est nécessaire (`createDynamicDimension`) et n’est jamais rechargée depuis le disque. Les Minis qu’elle contient sont donc des copies de travail : la vérité est dans la couche stratégique, qui les sérialise à chaque sauvegarde automatique et à l’arrêt. Deux objets de systèmes stellaires différents ne se rencontrent jamais, même à coordonnées équivalentes. |
| **Persistance des autres dimensions** | Toutes les dimensions d’objets (planètes, stations, croiseurs, mégastructures) sont persistantes. La couche stratégique en tient la liste ; au besoin, une dimension est chargée par `loadDynamicDimension`, et elle reste non instanciée si personne n’en a l’usage pendant la session. |
| **Mini active** | Sub-level Sable placé dans la dimension de son hôte, ou dans la Tampon Spatiale en vol libre. |
| **Mini inoccupée** | En vol libre dans la Tampon Spatiale : sérialisée en données dans la couche stratégique, sans plot. Dans l’hôte d’un objet supérieur : elle y reste, simulée physiquement (section 10.1). |
| **Entrée et sortie d’un hôte** | Marge de sécurité M, calculée automatiquement à partir du rayon de la Mini : entrée à R − M, sortie à R + M (section 7.2). |
| **Système chargé** | Un système est chargé quand au moins un joueur s’y trouve ; délai configurable avant déchargement après une déconnexion (section 6.2). |
| **Déplacement dans la Tampon Spatiale** | Pas de copie de blocs : un sub-level est déplacé par téléportation de sa pose (position et orientation). Vérifié dans le code de Sable (section 3.1). |
| **Position verrouillée, rotation libre** | Contrainte générique de Sable avec les trois axes linéaires verrouillés, ancrée au monde. Si elle échoue aux tests, tout est délégué au rendu de la couche stratégique et aux transactions de plots. |
| **Transfert entre dimensions** | Mécanisme à choisir (copie de blocs, transfert intégré à Sable, ou solution maison) : voir sections 10.5 et 17. |
| **Gel d’une Mini dormante** | Contrainte fixe au monde (corps nul) et endormissement Rapier pour une Mini simplement posée sous gravité (D4). |
| **Temps commun** | Un entier long de ticks de jeu (`getGameTime()`), indépendant de `/time set`, lu depuis la dimension principale. |
| **Dimensions dynamiques** | DynamicDimensions (TeamGalacticraft), version 0.10.0 (section 3.2). |
| **IPSable** | Référence d’implémentation uniquement, pas une dépendance : son modèle « dimension d’hébergement » est lourd, expérimental et repose sur un fork d’Immersive Portals et des mixins profonds. |
| **Simulated-Project** | Aucun transfert inter-dimension dans cette version ; non utilisé. |

### 3.1 Ce que le code de Sable permet (vérifié)

Lecture du dépôt de Sable au commit du 15 octobre 2026 (version 2.0.6).

| **Sujet** | **Constat** | **Conséquence** |
| --- | --- | --- |
| **Téléportation de pose** | `PhysicsPipeline.teleport(corps, position, orientation)` et `RigidBodyHandle.teleport`, utilisés par la commande `/sable teleport`. Position et orientation changent, le corps est réveillé, la vitesse n’est pas modifiée. | Déplacer une Mini dans la Tampon Spatiale ne demande aucune copie. La vitesse relative se pose séparément. |
| **Verrouiller la position sans la rotation** | La contrainte générique accepte un ensemble d’axes verrouillés (`LINEAR_X/Y/Z`, `ANGULAR_X/Y/Z`), et `addConstraint` accepte un corps nul pour ancrer l’autre au monde. L’ancre côté monde doit se trouver hors de la grille de plots. | Verrouiller les trois axes linéaires et laisser les angulaires libres est prévu par l’API. Stabilité à tester. |
| **Dimensions créées à l’exécution** | Le conteneur de sub-levels, le système de physique, le suivi et les tickets sont attachés dans le constructeur de `ServerLevel` (mixin) et fermés à `close`. | Un niveau instancié à l’exécution est pris en charge sans code supplémentaire. À confirmer en jeu. |
| **Physique par dimension** | Gravité, pression, traînée et nord magnétique viennent de fichiers JSON de datapack par identifiant de dimension (défaut : 11 m/s² vers le bas, traînée 0,09). | Pour une dimension créée à l’exécution, DynamicDimensions fournit une compatibilité (section 3.2). |
| **Grille de plots** | Par défaut 128 × 128 plots de 2048 blocs de côté, origine vers 20 millions de blocs. Le plot de stockage est alloué par Sable ; la pose, elle, est libre. | Plafond de 16 384 sub-levels par dimension. |
| **Libération d’un plot** | Deux raisons de retrait existent : « déchargé » (l’occupation du plot est conservée) et « retiré » (l’occupation est libérée). | Pour sérialiser une Mini en données sans occuper de plot, la sérialiser puis la retirer avec la raison « retiré ». |
| **Transfert entre dimensions** | La demande de modification n° 1474 (ouverte le 16 août 2026, non fusionnée) ajoute `SubLevelTransferService.transfer(racine, niveauDestination, poseDestination)` : sérialisation, rechargement avec décalage de plot et de hauteur, transfert des entités du plot, des tickets et des points de suivi, retour arrière en cas d’échec. Une erreur est levée si une section sort de la hauteur de la destination. | Candidat pour remplacer la copie de blocs. Dépend d’une fusion ou d’une branche. |
| **Interpolation client** | Aucune gestion spéciale d’un saut de pose n’a été trouvée dans l’interpolation des snapshots. | Une téléportation peut se voir comme un balayage d’un ou deux ticks. À tester. |
| **Entités** | Les entités nées dans un plot sont « expulsées » vers l’espace global ; celles qui se tiennent sur un sub-level le « suivent » (réseau et interpolation relatifs). Un joueur qui suit un sub-level se déconnecte et se reconnecte à une position relative, grâce aux points de suivi. | Cohérent avec la section 10.6. |
| **Licence** | Polyform Shield 1.0.0. | À lire avant de reprendre du code de Sable ; dépendre de son API est un cas différent. |

### 3.2 DynamicDimensions

Dépôt analysé : branche `minecraft/1.21`, version 0.10.0, Minecraft 1.21.1, NeoForge 21.1.148, dernier commit du 4 juin 2026. Elle est donc à jour pour NeoForge 1.21.1.

| **Sujet** | **Constat** |
| --- | --- |
| **API** | `DynamicDimensionRegistry` : créer (`createDynamicDimension`, qui écarte les données précédentes de même identifiant), charger (`loadDynamicDimension`, qui lit ou crée les données), décharger, supprimer, et tester l’existence. |
| **Compatibilité Sable** | Ajoutée le 8 mai 2026. Quand Sable est installé, `DynamicDimensionProperties` (gravité, pression, traînée, nord magnétique) s’applique à la dimension à sa création ou à son chargement. Cela couvre la physique des dimensions créées à l’exécution. |
| **Réserve** | La compatibilité est compilée contre Sable 1.2.2 alors que Sable est en 2.0.6 : à tester (section 16). |
| **Persistance** | La bibliothèque ne retient pas les dimensions créées d’un démarrage à l’autre : le mod doit tenir la liste et recharger. Un point d’entrée (`DynamicDimensionLoadCallback`) permet de charger des dimensions après les niveaux normaux. |
| **Dépôt Maven** | Le README signale que le dépôt Galacticraft est hors ligne et indique `repo.terradevelopment.net`. |
| **Autres candidates** | Le fork « AR Edition » (cycles jour/nuit propres à chaque dimension) et Madnessverse (via Architectury) restent des alternatives ; Fantasy cible Fabric. |

Les **types** de dimension (planète, station, vide spatial) sont à déclarer en datapack ; seuls les niveaux sont créés à l’exécution, pour que le client connaisse leur type.

## 4. Vocabulaire

| **Terme** | **Définition** |
| --- | --- |
| **Modèle spatial** | L’ensemble des données spatiales et leur simulation : ce que chaque objet utilise pour connaître l’espace autour de lui. |
| **SpaceObject** | Objet du modèle spatial, défini par une liste extensible de composants (section 6.1). Il n’a pas de classe à champs fixes. |
| **Archétype** | Nom d’usage d’un ensemble courant de composants (planète, station, croiseur, navette…). Les règles lisent les composants, pas l’archétype. |
| **Système stellaire** | Subdivision du modèle spatial avec son propre référentiel (origine des transforms). Vit dans la couche stratégique. |
| **Temps commun** | Référence de temps unique (long de ticks de jeu) à partir de laquelle sont calculés les objets sur rails. |
| **Proxy** | Représentation d’un objet du modèle spatial dans le référentiel d’un hôte (par exemple la sphère d’une planète vue depuis un croiseur). |
| **Sub-level (Sable)** | Objet physique : cadre de blocs soumis à la physique. |
| **Hôte** | Dimension dans laquelle des objets physiques sont simulés : dimension propre d’un objet (planète, station, croiseur, mégastructure), ou Tampon Spatiale. |
| **Tampon Spatiale** | Dimension unique et temporaire pour l’espace vide ; elle héberge les Minis occupées en vol libre et les hôtes temporaires. |
| **Slot** | Pose réservée à une Mini dans la Tampon Spatiale (le plot de stockage correspondant est alloué par Sable). |
| **Hôte temporaire** | Zone de la Tampon Spatiale où plusieurs objets sans dimension propre sont simulés ensemble pendant une rencontre. Ce n’est pas une dimension. |
| **Volume logique** | Zone de l’espace stratégique autour d’un objet ayant une dimension propre. Un objet qui y entre (à R − M) passe, sans chargement, dans cette dimension et y est simulé par Sable ; elle en constitue aussi la taille, étendue de la marge (section 7.2). Pas forcément une sphère. |
| **Volume d’exclusion** | Volume logique élargi d’une marge ; il règle de combien deux objets massifs peuvent s’approcher et se comporte comme une collision (section 7.2). Pas forcément une sphère. |
| **Données (Mini)** | Mini sérialisée dans la couche stratégique : ni sub-level, ni plot. |
| **Détruite** | État d’un objet marqué comme détruit : injoignable mais restaurable par un opérateur. Différent de « oublié ». |
| **Saut (jump)** | Technologie qui masque une téléportation entre deux systèmes stellaires. |
| **Marge de sécurité (M)** | Écart appliqué à la frontière d’un hôte : entrée à R − M, sortie à R + M. Calculée automatiquement à partir du rayon de la Mini. |
| **Système chargé** | Système stellaire où au moins un joueur se trouve (dans la couche stratégique). |
| **Mini couplée** | Mini posée, atterrie ou amarrée dans un hôte : elle suit l’hôte et ne subit pas ses changements de trajectoire. |
| **Effet d’inertie** | Variation de la vitesse locale d’une Mini non couplée quand l’hôte change de trajectoire (section 10.8). |

## 5. Architecture générale

Trois couches, du serveur vers le client :

1.  **Couche stratégique (serveur).** Source de vérité des positions et mouvements de tous les objets spatiaux, en double précision, indépendante des dimensions. Elle est découpée en systèmes stellaires. C’est le modèle spatial.

2.  **Couche dimensions (serveur).** Les hôtes : une dimension persistante par objet qui en possède une (planète, station, croiseur, mégastructure), plus la Tampon Spatiale, temporaire. Sable y simule les objets physiques.

3.  **Couche rendu (client).** Le ciel spatial, les proxies (impostors de planètes) et les modèles spatiaux des vaisseaux, alimentés par les données stratégiques répliquées.

Les dimensions ne se parlent jamais directement : elles lisent et publient leur état dans la couche stratégique, et chaque client reçoit la partie utile à son ciel.

## 6. Couche stratégique

### 6.1 SpaceObject et composants

| **Composant** | **Rôle** |
| --- | --- |
| **Identité** | Identifiant et système stellaire d’appartenance. |
| **Transform** | Position (double précision) dans le référentiel du système, orientation, vitesse. Sert à la couche stratégique. |
| **Render** | Modèle 3D, niveaux de détail, tout ce qui concerne le rendu. |
| **Controller** | La logique de l’objet : suivre un rail, tourner sur soi-même, être piloté (vaisseau), cibler (missile)… |
| **HostedDimension** | Dimension dédiée de l’objet. Vide pour les Minis et les Micros. |
| **PortsPoints** | Lieux d’atterrissage possibles : plusieurs pour une planète, un seul à 360° pour une station ou un vaisseau massif. |
| **Forgettable** | Condition d’oubli de l’objet (distance, détonation…). Sans ce composant, l’objet n’est pas oubliable et ne peut pas disparaître. Seules les Micros en portent un. |
| **Volumes** | Volume logique et volume d’exclusion (section 7.2), pour les objets qui en ont. |
| **Composants spécifiques** | État de Mini (données, dormante, active, détruite ; couplée ou non à son hôte), événements prédits d’une Micro, capacité d’approche, etc. Ajoutés sans modifier les autres. |

La liste n’est pas fermée : un composant s’ajoute sans toucher aux autres.

### 6.2 Systèmes stellaires et voyage entre systèmes

L’espace est divisé en systèmes stellaires. Chacun a son référentiel (l’origine de translation des transforms) et vit dans la couche stratégique. Deux objets dont les coordonnées sont équivalentes mais qui appartiennent à des systèmes différents ne se rencontrent pas.

Les systèmes sont joignables les uns depuis les autres, de deux façons :

- **Par un saut (par défaut).** Une technologie qui masque en réalité une téléportation : dans la couche stratégique, l’objet change de système et de position, et l’effet de saut cache le changement.
- **Par un parcours,** avec un réacteur suffisant. À cette distance de tout astre, le changement entre deux référentiels de la couche stratégique est imperceptible, donc simple à implémenter.

**Système chargé.** Un système stellaire est chargé quand au moins un joueur s’y trouve (dans la couche stratégique). Après la déconnexion du dernier joueur, il attend un délai configurable (**délai de déchargement**) avant d’être déchargé, au cas où un joueur se reconnecte. Un système non chargé n’est pas simulé : les objets sur rails restent à jour grâce au temps commun, et les Minis en vol libre sans personne dedans sont rattrapées au chargement (section 10.7).

### 6.3 Rails et temps commun

Les objets sur rails ne sont pas simulés à chaque tick : leur position est calculée à partir du temps commun. Un système non chargé est donc parfaitement à jour à son prochain chargement, puisque tous les astres sont replacés grâce au temps.

- Le temps est un entier long de ticks de jeu, lu depuis la dimension principale (certaines bibliothèques dupliquent les données de niveau). Il est indépendant de `/time set`.
- Pour éviter toute dérive, la phase d’une orbite se calcule par modulo de la période, au lieu d’accumuler des flottants.
- Les rails sont déterministes et reproductibles côté client sans trafic réseau : seuls leurs paramètres et le temps sont envoyés.

### 6.4 Persistance

L’état stratégique est sauvegardé avec le monde. Les rails ne stockent que leurs paramètres, les autres objets leur dernier transform. Les objets dont le propriétaire est hors ligne persistent à leur place.

- La couche stratégique conserve la liste des dimensions hébergées (DynamicDimensions ne la retient pas) ; toutes sont persistantes, sauf la Tampon Spatiale.
- Les Minis de la Tampon Spatiale sont conservées sous forme de données sérialisées ; celles qui sont occupées sont sérialisées à chaque sauvegarde automatique et à l’arrêt du serveur. Les Minis qui se trouvent dans l’hôte d’un objet supérieur persistent avec la dimension de cet hôte.
- Un objet détruit est conservé, marqué comme tel, jusqu’à une éventuelle restauration par un opérateur.

## 7. Archétypes et règles

### 7.1 Archétypes

| **Archétype** | **Exemples** | **Composants typiques** |
| --- | --- | --- |
| **Astre** | Étoile, trou noir, étoile à neutrons | Transform, Render, Controller (rail ou rotation). Pas de HostedDimension ni de PortsPoints. |
| **Planète** | Planète, lune | Astre + HostedDimension + PortsPoints (plusieurs sites d’atterrissage). |
| **Mégastructure** | Anneau, sphère de Dyson | HostedDimension, Controller (rail), PortsPoints avec capacité d’approche. Hôte le plus élevé. |
| **Superstructure** | Station | HostedDimension, Controller (rail), PortsPoints (un seul, à 360°). |
| **Structure** | Croiseur | HostedDimension, Controller (pilotage), PortsPoints (un seul, à 360°) ; capacités définies par son noyau. |
| **Mini** | Navette, joueur perdu | Controller (pilotage). Pas de HostedDimension ni de Forgettable : objet physique dans un hôte, ou donnée seule (section 10). |
| **Micro** | Projectile, débris, missile | Controller (ciblage ou trajectoire), Forgettable. Pas de HostedDimension ; ne crée jamais d’hôte (section 11). |

### 7.2 Volumes logique et d’exclusion

- **Volume logique.** Pour un objet qui a une HostedDimension : quand un objet entre dans ce volume, c’est-à-dire atteint R − M (dans le référentiel de la couche stratégique), il entre sans chargement dans la dimension hôte et y est simulé par Sable.

- **Le volume logique, de rayon R, est aussi la taille de la dimension hôte.** Comme un objet n’en sort qu’à R + M, la dimension doit en réalité s’étendre jusqu’à R + M pour la plus grande Mini admise. Tout objet qui dépasse R + M est renvoyé dans la Tampon Spatiale et quitte la dimension hôte.

- **Marge de sécurité M.** Pour entrer dans la dimension d’un hôte, un objet doit se trouver à une distance de l’hôte inférieure ou égale à R − M ; pour en sortir, supérieure ou égale à R + M. M est calculée automatiquement à partir du rayon de la Mini (formule à fixer). Elle évite les va-et-vient à la frontière : si M égale le rayon de la Mini, la Mini est entièrement dans le volume à l’entrée et entièrement dehors à la sortie. Pour un volume non sphérique, l’entrée et la sortie se mesurent par rapport à la surface du volume, décalée de M vers l’intérieur ou vers l’extérieur.

- **Volume d’exclusion = volume logique élargi (2*R).** Il agit comme une collision. Pour une sphère, la marge égale le rayon, ce qui redonne le rapport de 2 entre exclusion et volume logique.

- **Forme libre.** Une sphère suffit pour une planète, un vaisseau ou une station. Pour un Halo dont l’intérieur doit rester praticable, le volume n’est pas une sphère : son passage intérieur reste dans le volume logique.

- **Entrée dans le volume d’exclusion.** Un objet massif (croiseur) ne peut pas y entrer librement. Une navette le peut, et son entrée déclenche le préchargement de la dimension.

- **Recouvrement de rails :** si deux rails se recouvrent, le plus petit et le plus proche l’emporte.

- **Approche hors d’un port :** un nouveau port est généré, loin des autres, ou l’approche est refusée avec alerte, dégâts ou destruction.

- **Pas d’amarrage entre objets massifs.** Ils ne s’amarrent pas l’un à l’autre.

- **Objets hors ligne :** ils restent en place et gardent leur volume d’exclusion.

## 8. Planètes

- **Une dimension persistante par planète,** chargée à la demande (quand un joueur s’y trouve ou s’en approche). La planète est un SpaceObject avec HostedDimension et plusieurs PortsPoints.

- **Ciel :** le système stellaire vu depuis la position de la planète, calculé à partir de la couche stratégique ; les structures proches apparaissent en petit.

- **Génération :** carte plate, avec bouclage, pour un site d’atterrissage ; pré-génération anticipée du terrain pour éviter les saccades.

- **Ciel unique par dimension,** justifié par la position : x et z correspondent à une longitude et une latitude. La limite de la carte se justifie par une rotation synchrone (bande habitable), une zone praticable bornée (tempêtes, radiations) ou plusieurs sites d’atterrissage distincts.

## 9. Structures et superstructures

- **Une dimension statique et persistante par croiseur ou station :** l’intérieur est un monde normal, sans jitter, avec une gravité artificielle.

- **Taille de la dimension :** la dimension contient la coque et une zone de vide qui correspond au volume logique de rayon R, étendu de la marge M (section 7.2). Au-delà de R + M, un objet retourne dans la Tampon Spatiale.

- **Ciel :** l’espace autour du vaisseau ; les planètes y sont des proxies, les autres objets des modèles spatiaux.

- **Stations :** même mécanisme, avec un transform qui suit un rail orbital ; le ciel est unique mais la planète y tourne. La dimension peut avoir une hauteur Y augmentée.

- **Croiseurs :** pilotés, ils restent dans l’espace et ne rejoignent jamais une planète. Leur capacité (hangar, ports, armement) vient de leur noyau.

- **Sorties extravéhiculaires dans le vide :** le joueur dehors est une Mini (section 10) ; la rotation du croiseur doit être limitée en mode manœuvre, ou le joueur équipé de bottes magnétiques ou d’un câble.

## 10. Minis (objets sans dimension propre)

Une Mini n’a pas de HostedDimension. Une Mini n’est jamais oubliée, sauf intervention d’un opérateur.

### 10.1 États

| **État** | **Représentation** | **Détail** |
| --- | --- | --- |
| **Données** | Donnée spatiale et sub-level sérialisé dans la couche stratégique ; **aucun plot occupé** | Aucune simulation tant que le système n’est pas chargé (section 10.7). Réservé aux Minis en vol libre : une Mini qui se trouve dans l’hôte d’un objet supérieur n’est jamais sérialisée en données. Sérialisation par le mécanisme natif de Sable (toData / fullyLoad), avec un tag user_data pour les champs propres au mod, puis retrait du sub-level avec la raison « retiré », qui libère le plot. À la désérialisation, un plot est alloué ; un conflit de plot reste à surveiller. La vitesse est multipliée par 0,9 (configuration de Sable). |
| **Dormante** | Sub-level gelé dans le référentiel d’un hôte | Garée, posée ou amarrée dans l’hôte d’un objet supérieur, où elle reste même inoccupée (Mini couplée, section 10.8). Gel par contrainte fixe au monde (corps nul), qui existe dans l’API ; Rapier endort aussi naturellement un corps au repos. |
| **Active** | Sub-level simulé physiquement | En vol, dans l’hôte où elle se trouve (dimension d’un objet ou Tampon Spatiale). Dans l’hôte d’un objet supérieur, elle reste simulée physiquement même si personne n’est dedans. Gravité réglable par dimension. |
| **Détruite** | Donnée marquée comme détruite | Injoignable mais restaurable par un opérateur (section 10.7). |

Une Mini qui doit être simulée alors qu’elle est en état Données, par exemple parce qu’elle entre dans un hôte temporaire, est d’abord désérialisée.

### 10.2 Vol libre dans la Tampon Spatiale

- Une Mini occupée en vol libre garde un sub-level dans la Tampon Spatiale, à une pose réservée (son slot).
- Elle **reste statique dans le référentiel de coordonnées de la dimension** et **se déplace dans le référentiel de la couche stratégique** : la poussée modifie la vitesse stratégique, pas celle du sub-level. Le rendu du ciel, piloté par la position stratégique, produit l’impression de mouvement.
- **Rotation :** si les tests le confirment, la position est verrouillée par une contrainte générique (axes linéaires verrouillés, axes angulaires libres) et l’orientation reste physique, lue sur la pose Sable. Sinon, tout est délégué au rendu de la couche stratégique et aux transactions de plots.
- L’intérieur reste simulé par Sable (joueurs qui marchent, objets).
- Les poses réservées sont groupées près de l’origine de la dimension, et les indices libérés sont réutilisés, pour préserver la précision.

### 10.3 Occupation, déconnexion et plots

- Seules les Minis occupées sont des sub-levels dans la Tampon Spatiale. Les plots utilisés sont donc bornés par le nombre de joueurs présents (et par les Minis en attente de sérialisation). Dans l’hôte d’un objet supérieur, les Minis restent des sub-levels même inoccupées : chaque hôte a son propre plafond de plots (16 384 par dimension).
- Quand un joueur se déconnecte et rend vide une Mini en vol libre, la Mini attend un délai (**délai de sérialisation**, configurable) avant d’être sérialisée en données, au cas où le joueur se reconnecte. Dans l’hôte d’un objet supérieur, il n’y a pas de sérialisation.
- Une Mini en état Données n’est pas simulée : voir le rattrapage en section 10.7.

### 10.4 Hôte temporaire

Quand deux Minis ou plus s’approchent l’une de l’autre, avec au moins un joueur concerné, elles sont simulées ensemble dans un hôte temporaire, créé pour l’occasion **dans la Tampon Spatiale**. Il disparaît quand la rencontre prend fin. Il n’est jamais créé pour une Micro, ni pour une Mini seule.

- **Pas de copie de blocs :** les Minis sont déplacées par téléportation de leur pose (section 3.1), à une position et une orientation qui reproduisent leur position relative dans la couche stratégique.
- **Pose et vitesse :** position_locale = R⁻¹(p_objet − p_origine) et vitesse_locale = R⁻¹(v_objet − v_origine) − ω × r, où l’origine est celle du groupe. La téléportation ne modifie pas la vitesse : elle est appliquée séparément (addLinearAndAngularVelocity, qui sert de « set » pour un corps remis à zéro).
- **Vitesse relative :** plafonnée par le mass lock (section 13).
- **Mini sans joueur :** une Mini en état Données qui rencontre une Mini occupée est désérialisée pour être simulée dans l’hôte temporaire.
- **Fusion de groupes :** possible, la méthode reste à définir (section 17).

### 10.5 Transfert entre dimensions

Le trajet complet d’une navette qui quitte un croiseur pour une planète est :

1.  Parcours dans la dimension hôte du croiseur ;
2.  **changement de dimension seamless** vers la Tampon Spatiale (par sortie au-delà du rayon R du croiseur) ;
3.  vol libre (éventuellement avec hôte temporaire) ;
4.  **changement de dimension seamless** vers la dimension hôte de la planète, à l’entrée du volume logique de la planète.

Mécanisme de transfert du sub-level : à choisir (section 17).

- **Copie de blocs d’un plot à l’autre (option IPSable) :** trois passes (placement sans effets de bord, restauration des block entities, notification des voisins), effets de placement suspendus ; même UUID conservé.
- **Transfert intégré à Sable (demande n° 1474, non fusionnée) :** sérialisation et rechargement atomiques avec retour arrière.
- **Pose et vitesse d’arrivée :** mêmes formules qu’en 10.4, avec l’hôte d’arrivée comme origine.
- **Pièges relevés dans IPSable :** sans transfert de vitesse, l’objet retraverse la frontière en boucle et finit par faire planter le rendu client ; les acteurs de block entities peuvent rester périmés après un rechargement de chunk et doivent être rattachés à nouveau.
- **Limite de hauteur :** les blocs hors de la plage verticale de la dimension d’arrivée sont perdus (ou provoquent une erreur) ; les dimensions concernées doivent avoir des hauteurs compatibles.

Allouer, transférer et désallouer doivent tenir dans un tick. La physique de Sable est synchrone dans le tick serveur, donc c’est plausible, mais le coût n’est pas mesuré (section 16).

### 10.6 Reconversion en blocs d’hôte et passagers

**Reconversion.** Une Mini peut être reconvertie en blocs de l’hôte pour être modifiée. Sable offre le déplacement de blocs entre niveaux (moveBlocks avec AssemblyTransform) mais seulement avec des rotations multiples de 90° et sans désassemblage natif : la Mini doit être alignée sur la grille de l’hôte avant la reconversion.

**Passagers.** Les passagers ne sont pas sérialisés avec le sub-level. Les entités qui se tiennent dessus (suivies par Sable) sont téléportées séparément, avant la suppression de la source, avec leur position convertie par la pose de la Mini. À la déconnexion puis à la reconnexion, Sable rétablit un joueur à sa position relative au sub-level grâce à ses points de suivi.

### 10.7 Mini sans joueur : rattrapage et destruction

Une Mini en vol libre sans personne dedans, dans un système stellaire non chargé, n’est pas simulée. Un système est chargé quand au moins un joueur s’y trouve (section 6.2). Au chargement du système, tout le temps écoulé depuis son déchargement est simulé d’un coup. Une Mini qui se trouve dans l’hôte d’un objet supérieur n’est pas concernée : elle reste simulée physiquement dans cet hôte, tant que cette dimension est chargée.

| **Collision pendant le rattrapage avec…** | **Résultat** |
| --- | --- |
| **Un astre plus important** | La Mini est marquée détruite. |
| **Une autre Mini** | Les deux sont marquées détruites. |
| **Une Micro** | La Mini est marquée détruite ; la Micro est oubliée. |

Un objet détruit n’est pas oublié : il est injoignable mais restaurable par un opérateur. Les collisions du rattrapage doivent être résolues dans l’ordre chronologique (à concevoir).

### 10.8 Minis dans un hôte mobile : effet d’inertie

Une Mini qui se trouve dans l’hôte d’un objet supérieur y reste, même inactive ou sans personne dedans, et reste simulée physiquement dans cet hôte.

- **Mini couplée :** posée, atterrie ou amarrée dans l’hôte. Elle suit l’hôte : elle n’est pas affectée par ses changements de trajectoire.
- **Mini non couplée :** en vol dans la dimension de l’hôte. Si l’hôte peut se déplacer dans la couche stratégique (un grand vaisseau) et modifie son vecteur de déplacement, la modification se répercute sur la Mini : si l’hôte accélère, la Mini est projetée vers l’arrière de la dimension, jusqu’à en sortir si nécessaire (à R + M, section 7.2) et entrer alors dans la Tampon Spatiale.
- **Principe :** dans la couche stratégique, la Mini conserve sa vitesse. Dans la dimension, sa vitesse locale varie donc de l’opposé de la variation de vitesse de l’hôte, exprimée dans le référentiel de l’hôte. À la sortie, la vitesse stratégique de la Mini (vitesse de l’hôte plus vitesse locale) est continue.
- **Mise en œuvre :** une impulsion ou une force appliquée par le pipeline de physique de Sable à chaque Mini non couplée, plutôt qu’une modification de la gravité de la dimension, qui s’appliquerait à tout ce qu’elle contient. L’effet ne s’applique que pendant que la dimension est chargée.
- **Plus tard :** si l’hôte saute vers un autre système, les Minis sont éjectées vers la Tampon Spatiale, endommagées voire détruites, et leurs occupants sont agressés voire tués (section 17).

## 11. Micros

- Elles existent comme données : événements prédits (trajectoires, impacts) et une condition d’oubli (composant Forgettable) après laquelle elles disparaissent. Elles sont les seuls objets instanciés avec une condition d’oubli.

- Elles ne créent jamais d’hôte. Elles peuvent entrer dans un hôte existant, où elles sont simulées par leur transform spatial, ou en sortir et ne plus exister que comme données.

- Une collision avec une Mini détruit la Mini et fait oublier la Micro (section 10.7).

## 12. Rendu

### 12.1 Ciel spatial

Le moteur du proof of concept dessine le fond d’étoiles, l’étoile du système, les proxies de planètes et les modèles spatiaux. Il est piloté par l’objet spatial associé à la dimension du joueur et par sa position dans cette dimension ; pour une Mini en vol libre dans la Tampon Spatiale, par sa position stratégique. Un hôte rend son propre modèle spatial depuis l’intérieur de son volume logique, au-delà de la distance de vue.

### 12.2 Règles de visibilité

| **Observateur** | **Voit** | **Remarques** |
| --- | --- | --- |
| **Joueur sur une planète** | Système stellaire ; structures en petit si assez proches | Visibilité déterminée par la taille apparente. |
| **Joueur dans une structure** | Planètes (proxies) ; autres objets (modèle spatial LOD) | Distance stratégique et taille. |
| **Joueur dans une station** | Idem ; sa planète tourne dans le ciel | Station visible de loin selon sa taille. |
| **Joueur dans une Mini** | Hôte réel s’il est dans son volume logique, sinon ciel spatial | Une Mini en vol libre voit l’espace par le ciel. |

Un objet est rendu quand sa taille apparente dépasse un seuil (à calibrer). La même règle donne un petit objet proche et un gros objet lointain.

### 12.3 Modèles spatiaux

- Générés à partir des blocs du vaisseau, avec plusieurs niveaux de détail, et régénérés après modification avec un délai (pas à chaque bloc posé).

- Doivent inclure les ouvertures importantes (par exemple un hangar).

- Fondu entre modèle LOD et rendu réel pour éviter l’effet de pop.

## 13. Navigation et distances

Problème : la planète vue depuis le ciel d’un croiseur ne grossit pas tant que le croiseur reste dans sa dimension, alors qu’elle devrait se rapprocher dès qu’il avance.

- **Continuité de position.** La position stratégique d’un objet dans une dimension hôte est celle de son hôte plus son décalage local, sans facteur de conversion (le facteur k est abandonné), ce qui évite tout saut lors des changements de régime.

- **Deux régimes de propulsion.** Dans le volume logique, propulseurs de manœuvre (lents). Hors de ce volume, moteur de croisière, avec une montée en puissance de 2 à 3 secondes qui masque le changement.

- **Vitesse selon la masse.** La vitesse maximale dépend de la distance au corps massif le plus proche (mass lock), ce qui rend l’approche continue. Le mass lock plafonne aussi la vitesse relative à l’approche d’un autre objet (section 10.4).

- **Préchargement.** Il démarre à l’entrée du volume d’exclusion (section 7.2) et se termine avant l’entrée dans la dimension de l’hôte (à R − M). La distance entre les deux, divisée par la vitesse imposée par le mass lock, doit dépasser le temps de chargement de la dimension.

- **Entrée et sortie d’un hôte, avec hystérésis.** L’objet entre dans la dimension de l’hôte à R − M et en sort à R + M ; M est calculée à partir du rayon de la Mini (section 7.2). L’écart de 2M entre les deux seuils évite les va-et-vient.

- **Loin de tout astre,** le mass lock ne limite plus la vitesse, et le changement de référentiel entre deux systèmes est imperceptible (section 6.2).

**Solution écartée :** rendre l’espace à grande distance dans le monde de la dimension (coût de rendu et de précision flottante).

## 14. Transitions sans écran de chargement

### 14.1 Décision

Le mod doit permettre un changement de dimension seamless. **Immersive Portals n’est pas une dépendance pour le moment.** Il reste une solution de repli, et l’objectif est de s’en passer. Le mécanisme sans Immersive Portals reste à définir et à tester (section 16).

### 14.2 Ce qu’un remplaçant doit reproduire (lu dans le code d’Immersive Portals)

- **Serveur :** déplacer le joueur de l’ancienne dimension vers la nouvelle sans envoyer le paquet de respawn de Minecraft, pour ne jamais déclencher l’écran « Réception du niveau ».

- **Client :** changer le monde du joueur à chaud, parmi des mondes client conservés un par dimension.

- **Condition :** les chunks de destination doivent déjà être chez le client. Sans Immersive Portals (et son `ChunkLoader`), l’envoi anticipé de ces chunks demandera probablement des paquets maison, à confirmer.

### 14.3 Séquence pour une Mini avec joueur

1.  Précharger les chunks de destination dès l’entrée dans le volume d’exclusion.

2.  Calculer la pose et la vitesse d’arrivée (sections 10.4 et 10.5).

3.  Transférer la Mini dans l’hôte d’arrivée (mécanisme de la section 10.5), appliquer la vitesse.

4.  Téléporter le joueur et les entités transportées, avant la suppression de la source.

5.  Supprimer la source ; Sable envoie l’arrêt de suivi aux clients de l’ancienne dimension.

### 14.4 Compatibilité avec les shaders (à valider)

Sans Immersive Portals, le risque d’incompatibilité avec Iris et Sodium vient du mécanisme de remplacement et de Sable (qui contient lui-même des correctifs de compatibilité Iris). À vérifier en jeu (section 16). Si le repli était utilisé, la réserve d’origine s’applique : son README annonce un rendu « à peu près compatible » avec certaines versions de Sodium et d’Iris, et son journal indique que la compatibilité Iris de la version 1.21.1 n’est pas complètement fonctionnelle ; ses mixins sur des classes de rendu centrales restent actifs même sans portails.

## 15. Réseau, persistance et risques

### 15.1 Réseau

- Le serveur est la source de vérité de la couche stratégique.

- Les rails ne sont pas répliqués : seuls leurs paramètres et le temps commun sont envoyés.

- Les transforms des objets pilotés et des Minis sont répliqués aux clients dont le ciel peut les voir, à fréquence réduite, avec interpolation côté client.

### 15.2 Risques

| **Risque** | **Piste** |
| --- | --- |
| **Compatibilité DynamicDimensions et Sable 2.0.6** | La compatibilité est compilée contre Sable 1.2.2 : tester dès la phase 0 ; sinon appliquer la physique de la dimension depuis le mod, ou corriger la compatibilité. |
| **Coût des dimensions chargées en parallèle** | Charger à la demande, décharger quand vide ; mesurer avec Spark. |
| **Coût d’un transfert entre dimensions** | Mesurer (section 16) ; au besoin, limiter la taille des Minis ou étaler le transfert sur quelques ticks hors vue du joueur. |
| **Changement de dimension sans Immersive Portals** | Prototype dès la phase 0 ; repli sur Immersive Portals ou sur un fork limité aux fonctions de changement de monde client. |
| **Perte de Minis occupées dans la Tampon Spatiale en cas de plantage** | Sérialiser à chaque sauvegarde automatique ; la perte est limitée au temps écoulé depuis la dernière sérialisation. |
| **Rendu client et suivi Sable lors d’un transfert ou d’un saut de pose** | Forcer le nouveau suivi côté client, comme IPSable ; éviter tout transfert répété à la frontière ; choisir l’origine d’un groupe de façon à limiter les sauts. |
| **Fusion et scission de groupes de rencontre** | Groupes de proximité déterministes, avec hystérésis, mis à jour entre deux ticks. |
| **Effet d’inertie sur les Minis d’un hôte mobile** | Appliquer la correction de vitesse à chaque tick, seulement aux Minis non couplées ; vérifier la stabilité pour de fortes accélérations et les éjections en cascade. |
| **Nombre de Minis dormantes dans un hôte** | Chaque hôte a son propre plafond de plots ; surveiller le nombre de Minis qui y restent. |
| **Coût et exactitude du rattrapage de simulation** | Résolution des collisions dans l’ordre chronologique ; rails calculables à tout instant ; mesurer sur de longues durées. |
| **Pipeline des modèles spatiaux** | Prototype précoce : génération depuis les blocs, LOD, régénération différée. |
| **Multijoueur (ciels, réplication)** | Conception réseau dès la première version ; tests à plusieurs joueurs. |
| **Précision flottante** | Double précision côté stratégique, coordonnées relatives à l’observateur côté rendu, poses de la Tampon Spatiale groupées près de l’origine. |

## 16. Tests à effectuer

Les sources répondent à toutes les questions de conception sauf celles ci-dessous, qui demandent une mesure ou un essai. Le détail de chaque test (prérequis, procédure, mesures, critères de réussite, interventions manuelles) est dans le document **`plan_de_tests.md`**, conçu pour être délégué à des agents qui font appel à l’utilisateur quand une intervention manuelle est nécessaire.

Les tests A sont les prototypes de risques de la phase 0 ; les tests B se font sur la couche stratégique, hors du jeu ; les tests C concernent l’intégration.

| **N°** | **Test** | **Intervention manuelle** |
| --- | --- | --- |
| **A1** | DynamicDimensions avec Sable 2.0.6 : création, chargement, déchargement, physique de la dimension | Non |
| **A2** | Persistance des dimensions et recréation de la Tampon Spatiale à chaque démarrage | Non |
| **A3** | Téléportation de pose d’un sub-level portant un joueur | Oui |
| **A4** | Position verrouillée, rotation libre (contrainte générique) | Selon les cas |
| **A5** | Gel par contrainte fixe et endormissement | Non |
| **A6** | Sérialisation en données, libération et réallocation des plots | Non |
| **A7** | Déconnexion d’un joueur et délai de sérialisation | Oui |
| **A8** | Changement de dimension sans Immersive Portals (repli : Immersive Portals) | Oui |
| **A9** | Transfert de sub-level entre dimensions : coût et comportement | Selon les cas |
| **A10** | Shaders (Iris, Sodium) avec Sable | Oui |
| **B1** | Rails et temps commun : déterminisme | Non |
| **B2** | Rattrapage de simulation et états détruits | Non |
| **B3** | Groupes de rencontre : formation, fusion, scission | Non |
| **B4** | Chargement et déchargement d’un système stellaire | Non |
| **C1** | Hôte temporaire : deux Minis, vitesse relative, fusion et scission | Oui |
| **C2** | Préchargement d’une dimension hôte | Selon les cas |
| **C3** | Frontière d’un hôte avec la marge M | Non |
| **C4** | Voyage entre systèmes : saut et parcours | Oui |
| **C5** | Arrêt propre et plantage | Selon les cas |
| **C6** | Multijoueur | Oui |
| **C7** | Effet d’inertie sur les Minis d’un hôte mobile | Selon les cas |

## 17. Points encore ouverts

- **Mécanisme de transfert entre dimensions :** copie de blocs, transfert intégré à Sable (demande n° 1474, ou sa branche), ou solution maison ; licence à lire si du code de Sable est repris.

- **Origine d’un groupe de rencontre et fusion de groupes :** proposition : la pose de la première Mini à y entrer ; méthode de fusion et saut de pose côté client à définir.

- **Formule de la marge M :** calcul automatique à partir du rayon de la Mini ; définition du rayon (par exemple la boîte englobante du sub-level) et valeur plancher à fixer. La taille réelle de la dimension hôte (R + M de la plus grande Mini admise) est à confirmer.

- **Délais :** valeurs par défaut du délai de sérialisation d’une Mini vide et du délai de déchargement d’un système.

- **Hôte mobile et effet d’inertie :** critère exact de couplage (posée, atterrie, amarrée) ; rotation de l’hôte (forces centrifuge et de Coriolis), non traitée ; effet d’inertie sur une dimension déchargée.

- **Mini en données qui entre dans le volume d’un hôte :** comportement non décrit (hôte chargé ou non, désérialisation).

- **Plus tard : saut d’un hôte qui emporte des Minis.** Les Minis sont éjectées vers la Tampon Spatiale, endommagées voire détruites ; leurs occupants sont agressés voire tués.

- **Outils d’opérateur :** restaurer un objet détruit, oublier une Mini, déplacer un objet.

- **Voyage entre systèmes :** le principe est décidé (saut ou parcours) ; restent la portée du saut, son coût, son effet de masquage et la conversion de position à l’arrivée.

- **Arrivée sur une planète :** entrée atmosphérique, gravité et chargement de la dimension.

- **Mégastructures :** ports, capacité d’approche et rails à détailler.

- **Gel ou origine flottante :** le gel est retenu ; la variante à origine flottante reste en réserve si les tests A4 et A5 échouent.

- **Sorties au-delà du volume :** joueur perdu dans l’espace, capture par une navette, garde-fous.

## 18. Feuille de route

| **Phase** | **Contenu** | **Critère de réussite** |
| --- | --- | --- |
| **0** | Prototype de risques : DynamicDimensions avec Sable, téléportation de pose, contrainte de position, sérialisation, changement de dimension sans Immersive Portals (repli : Immersive Portals), shaders (tests A1 à A10) | Les tests A1 à A10 donnent un résultat clair. |
| **1** | SpaceObject et composants, systèmes stellaires, couche stratégique, rails orbitaux sur le temps commun, voyage entre systèmes | Positions déterministes identiques sur serveur et clients (B1) ; un système non chargé est à jour au chargement (B4). |
| **2** | Ciel piloté par l’objet associé à la dimension (extension du PoC) | Planètes en proxies correctement placées depuis un croiseur. |
| **3** | Dimensions de structures et superstructures, volumes logique et d’exclusion | Deux croiseurs visibles l’un de l’autre sans coupure. |
| **4** | Modèles spatiaux : génération, LOD, régénération | Un croiseur modifié voit son modèle mis à jour sans saccade. |
| **5** | Planètes : dimension, ciel selon la position, sites d’atterrissage | Ciel cohérent avec la position, pas de saccade à l’arrivée. |
| **6** | Minis : états, Tampon Spatiale, sérialisation en données, hôte temporaire, transfert sans chargement, rattrapage et destruction | Une navette passe d’un hôte à un autre avec son pilote sans écran de chargement (A8, A9, C1 à C3). |
| **7** | Navigation : loi de vitesse, mass lock, deux régimes de propulsion, préchargement, effet d’inertie sur les Minis d’un hôte mobile | Approche d’une planète continue et lisible (C2) ; Minis non couplées correctement poussées et éjectées (C7). |
| **8** | Micros, mégastructures, multijoueur, optimisation ; plus tard, saut d’un hôte emportant des Minis | TPS stable avec plusieurs structures, Minis et joueurs (C5, C6). |
