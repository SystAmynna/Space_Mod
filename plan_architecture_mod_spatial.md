# Plan d’architecture

*Mod Minecraft de voyage spatial seamless*

*Version 2 · 19 octobre 2026 · intègre la hiérarchie des structures, les Ministructures et l’analyse des sources*

## 1. Objectif et périmètre

Ce document fixe l’architecture d’un mod permettant de voyager dans l’espace sans écran de chargement, à la manière de Star Citizen, Elite Dangerous ou No Man’s Sky. Les lieux sont des dimensions (planètes, stations, croiseurs, mégastructures) ; les petits vaisseaux (navettes) sont des objets physiques simulés seulement quand un joueur est concerné.

**Nouveautés de cette version :** hiérarchie en cinq classes de structures, vocabulaire commun, machine à états des Ministructures, règles d’exclusion, et conclusions de l’analyse du code source de Sable, Immersive Portals, IPSable et Simulated-Project (sections 3, 10, 14 et 16).

**Reste à préciser :** le détail des mégastructures (anneau, sphère de Dyson, trou noir) et quelques cas limites listés en section 17.

## 2. Principes directeurs

- **Le ciel est un rendu, pas un monde.** L’espace n’existe pas sous forme de blocs : il est dessiné dans le ciel à partir de données.

- **Les données d’abord.** Tout objet existe d’abord comme donnée spatiale ; il n’est simulé physiquement que là où un joueur en a besoin.

- **Un lieu = une dimension statique.** Planètes, stations, croiseurs et mégastructures sont des dimensions normales, sans physique de corps rigide : pas de coût de simulation lié à leur taille.

- **Le mouvement est une donnée.** Un croiseur se déplace en mettant à jour son transform dans la couche stratégique, pas en déplaçant des blocs.

- **Une classe se définit par ses capacités, pas par sa taille.** Ce qui distingue une Structure d’une Ministructure est ce qu’elle peut faire (dimension propre, pilotage, noyau), pas son nombre de blocs.

- **Les transitions sont masquées, pas supprimées.** Effets (montée en puissance, fondu) pour cacher les changements de référentiel.

## 3. Plateforme technique et décisions actées

| **Sujet** | **Décision** |
| --- | --- |
| **Loader / version** | NeoForge, Minecraft 1.21.1. |
| **Physique** | Sable seul, sans Create Aeronautics. Le jeu démarre avec Sable sans Create ni Veil (testé par l’utilisateur ; Veil est embarqué dans Sable). Les pièces propres à Aeronautics (propulseurs, ballons) sont à écrire. |
| **Transitions sans chargement** | Immersive Portals pour NeoForge en dépendance obligatoire (décision D1). Validation conditionnelle : ne doit rien casser, notamment les shaders (voir section 14.3). |
| **Mini active** | Sub-level Sable placé dans la dimension de son hôte, pour collisionner avec ses vrais blocs. Transferts par copie de blocs (D2). |
| **Dimension « Ministructures »** | Dimension partagée sans terrain, qui contient les slots et les hôtes éphémères de rencontre (D3). |
| **Gel d’une Mini dormante** | Contrainte fixe au monde (corps nul) et endormissement Rapier pour une Mini simplement posée sous gravité (D4). |
| **IPSable** | Référence d’implémentation uniquement, pas une dépendance : son modèle « dimension d’hébergement » est lourd, expérimental et repose sur un fork d’Immersive Portals et des mixins profonds. |
| **Simulated-Project** | Aucun transfert inter-dimension dans cette version ; non utilisé. |
| **Dimensions dynamiques** | À vérifier : création à chaud (bibliothèque type DimLib) ou pool de dimensions prédéclarées. |

## 4. Vocabulaire

| **Terme** | **Définition** |
| --- | --- |
| **Modèle spatial** | L’ensemble des données spatiales et leur simulation : ce que chaque niveau de structure utilise pour connaître l’espace autour de lui. |
| **Donnée spatiale** | Les données d’un objet dans le modèle spatial : transform et modèle 3D, extensibles sur le principe des composants (pas de classe à champs fixes). |
| **Proxy** | Représentation d’un objet du modèle spatial dans le référentiel d’un hôte (par exemple la sphère d’une planète vue depuis un croiseur). |
| **Sub-level (Sable)** | Objet physique : cadre de blocs soumis à la physique. |
| **Hôte** | Dimension dans laquelle des objets physiques sont simulés (planète, station, croiseur, mégastructure, hôte éphémère). |
| **Slot** | Plot et pose réservés dans la dimension partagée « Ministructures ». |
| **Bulle de simulation** | Zone autour d’une structure où ce qui s’y trouve est simulé dans son hôte. |
| **Bulle d’exclusion** | Rayon égal à deux fois celui de la bulle de simulation ; elle se comporte comme une collision (section 7.2). |

## 5. Architecture générale

Trois couches, du serveur vers le client :

1.  **Couche stratégique (serveur).** Source de vérité des positions et mouvements de tous les objets spatiaux, en double précision, indépendante des dimensions. C’est le modèle spatial.

2.  **Couche dimensions (serveur).** Les hôtes : une dimension par planète, station, croiseur ou mégastructure, plus la dimension partagée des Ministructures. Sable y simule les objets physiques.

3.  **Couche rendu (client).** Le ciel spatial, les proxies (impostors de planètes) et les modèles spatiaux des vaisseaux, alimentés par les données stratégiques répliquées.

Les dimensions ne se parlent jamais directement : elles lisent et publient leur état dans la couche stratégique, et chaque client reçoit la partie utile à son ciel.

## 6. Couche stratégique

### 6.1 Donnée spatiale

| **Composant** | **Rôle** |
| --- | --- |
| **Identité** | Identifiant et classe (planète, mégastructure, superstructure, structure, mini, micro). |
| **Transform** | Position (double précision), orientation, vitesse. |
| **Modèle 3D** | Modèle utilisé pour voir l’objet de l’extérieur, avec niveaux de détail. |
| **Mouvement** | Rail orbital (planètes, mégastructures, stations) ou piloté (structures, minis). |
| **Hôte associé** | La dimension correspondante, s’il y en a une ; pour une Mini, l’hôte ou le slot qui la contient. |
| **Composants spécifiques** | Ports et capacité d’approche, état de Mini, événements prédits d’une Micro, etc. Ajoutés sans modifier les autres. |

### 6.2 Rails orbitaux

Les planètes, stations et mégastructures suivent des orbites calculées à partir du temps de jeu (pas de simulation à n corps). La position est déterministe et reproductible côté client sans trafic réseau.

### 6.3 Persistance

L’état stratégique est sauvegardé avec le monde. Les rails ne stockent que leurs paramètres, les structures leur dernier transform. Les structures dont le propriétaire est hors ligne persistent à leur place.

## 7. Hiérarchie des structures

### 7.1 Les cinq classes

| **Classe** | **Exemples** | **Caractéristiques** |
| --- | --- | --- |
| **Mégastructure** | Anneau, sphère de Dyson | Dimension propre ; hôte le plus élevé ; rail ; liste de ports avec capacité d’approche. |
| **Superstructure** | Station | Dimension propre ; un seul ancrage ; rail. |
| **Structure** | Croiseur | Dimension propre ; pilotée ; exclue des hôtes supérieurs et des autres structures ; capacités définies par son noyau. |
| **Ministructure** | Navette, joueur perdu | Pas de dimension propre ; objet physique dans un hôte, ou donnée seule (section 10). |
| **Microstructure** | Projectile, débris | Données, événements prédits et valeur d’oubli ; ne crée jamais d’hôte (section 11). |

Les planètes sont des hôtes sur rails au même titre que les stations ; elles restent traitées à part (section 8) car elles ne s’accrochent à aucune de ces classes.

### 7.2 Règles d’exclusion et d’approche

- **Bulle d’exclusion = 2 × bulle de simulation,** et elle agit comme une collision : une structure ne peut pas y entrer librement.

- **Recouvrement de rails :** si deux rails se recouvrent, le plus petit et le plus proche l’emporte.

- **Approche hors d’un port :** un nouveau port est généré, loin des autres, ou l’approche est refusée avec alerte, dégâts ou destruction.

- **Pas d’amarrage entre structures massives.** Elles ne s’amarrent pas l’une à l’autre.

- **Structures hors ligne :** elles restent en place et gardent leur bulle d’exclusion.

## 8. Planètes

- **Une dimension par planète,** chargée à la demande (quand un joueur s’y trouve).

- **Ciel :** le système stellaire vu depuis la position de la planète, calculé à partir de la couche stratégique ; les structures proches apparaissent en petit.

- **Génération :** carte plate, avec bouclage, pour un site d’atterrissage ; pré-génération anticipée du terrain pour éviter les saccades.

- **Ciel unique par dimension,** justifié par la position : x et z correspondent à une longitude et une latitude. La limite de la carte se justifie par une rotation synchrone (bande habitable), une zone praticable bornée (tempêtes, radiations) ou plusieurs sites d’atterrissage distincts.

## 9. Structures et superstructures

- **Une dimension statique par croiseur ou station :** l’intérieur est un monde normal, sans jitter, avec une gravité artificielle.

- **Bulle de vide** de rayon R autour de la coque, cohérente avec les bulles d’exclusion de la section 7.2.

- **Ciel :** l’espace autour du vaisseau ; les planètes y sont des proxies, les autres structures des modèles spatiaux.

- **Stations :** même mécanisme, avec un transform qui suit un rail orbital ; le ciel est unique mais la planète y tourne. La dimension peut avoir une hauteur Y augmentée.

- **Croiseurs :** pilotés, ils restent dans l’espace et ne rejoignent jamais une planète. Leur capacité (hangar, ports, armement) vient de leur noyau.

- **Sorties extravéhiculaires dans la bulle :** le joueur dehors est une Ministructure (section 10) ; la rotation du croiseur doit être limitée en mode manœuvre, ou le joueur équipé de bottes magnétiques ou d’un câble.

## 10. Ministructures (navettes)

Une Ministructure n’a pas de dimension propre. Tant qu’aucun joueur n’est dedans, elle n’est simulée que sous forme de données.

### 10.1 États

| **État** | **Représentation** | **Détail** |
| --- | --- | --- |
| **Données** | Donnée spatiale seule ; sub-level sérialisé | Aucune simulation. Sérialisation par le mécanisme natif de Sable (toData / fullyLoad), avec un tag user_data pour les champs propres au mod. Le plot est réalloué aux mêmes coordonnées au rechargement, donc un conflit est possible ; la vitesse est multipliée par 0,9 (configuration de Sable). |
| **Dormante** | Sub-level gelé dans le référentiel d’un hôte | Garée ou posée. Gel par contrainte fixe au monde (corps nul), qui existe dans l’API ; Rapier endort aussi naturellement un corps au repos. |
| **Active** | Sub-level simulé physiquement | En vol, dans l’hôte où elle se trouve. Gravité réglable par dimension (donnée de dimension de Sable). |

### 10.2 Hôte éphémère

Un hôte éphémère n’est créé que lorsque deux Ministructures ou plus se rencontrent avec au moins un joueur concerné. Il disparaît quand la rencontre prend fin. Il n’est jamais créé pour une Microstructure, ni pour une Mini seule.

### 10.3 Transactions de slot

Allouer un slot, téléporter, désallouer et fusionner dans un hôte éphémère doivent tenir dans un tick. Les sources montrent que la physique de Sable est synchrone dans le tick serveur, donc c’est plausible, mais le coût n’est pas mesuré (section 16).

- **Mécanique retenue :** copie de blocs d’un plot à l’autre, comme le fait IPSable : trois passes (placement sans effets de bord, restauration des block entities, notification des voisins), avec les effets de placement suspendus pendant la copie ; la même identité (UUID) est conservée.

- **Pose et vitesse :** la pose de destination est calculée par position_locale = R⁻¹(p_vaisseau − p_hôte) et vitesse_locale = R⁻¹(v_vaisseau − v_hôte) − ω × r. La vitesse est appliquée au corps neuf avec addLinearAndAngularVelocity ; elle sert de « set » car le corps part au repos.

- **Pièges relevés dans IPSable :** sans transfert de vitesse, l’objet retraverse la frontière en boucle et finit par faire planter le rendu client ; les acteurs de block entities peuvent rester périmés après un rechargement de chunk et doivent être rattachés à nouveau.

- **Limite de hauteur :** les blocs hors de la plage verticale de la dimension d’arrivée sont perdus ; les dimensions concernées doivent avoir des hauteurs compatibles.

### 10.4 Reconversion en blocs d’hôte

Une Mini peut être reconvertie en blocs de l’hôte pour être modifiée. Sable offre le déplacement de blocs entre niveaux (moveBlocks avec AssemblyTransform) mais seulement avec des rotations multiples de 90° et sans désassemblage natif : la Mini doit être alignée sur la grille de l’hôte avant la reconversion.

### 10.5 Passagers

Les passagers ne sont pas sérialisés avec le sub-level. Les entités qui se tiennent dessus (suivies par Sable) sont téléportées séparément, avant la suppression de la source, avec leur position convertie par la pose de la Mini. À la déconnexion puis à la reconnexion, Sable rétablit un joueur à sa position relative au sub-level grâce à ses points de suivi.

## 11. Microstructures

- Elles existent comme données : événements prédits (trajectoires, impacts) et une valeur d’oubli après laquelle elles disparaissent.

- Elles ne créent jamais d’hôte. Elles peuvent entrer dans un hôte existant, où elles sont simulées par leur transform spatial, ou en sortir et ne plus exister que comme données.

## 12. Rendu

### 12.1 Ciel spatial

Le moteur du proof of concept dessine le fond d’étoiles, l’étoile du système, les proxies de planètes et les modèles spatiaux. Il est piloté par l’objet spatial associé à la dimension du joueur et par sa position dans cette dimension. Un hôte rend son propre modèle spatial depuis l’intérieur de sa bulle, au-delà de la distance de vue.

### 12.2 Règles de visibilité

| **Observateur** | **Voit** | **Remarques** |
| --- | --- | --- |
| **Joueur sur une planète** | Système stellaire ; structures en petit si assez proches | Visibilité déterminée par la taille apparente. |
| **Joueur dans une structure** | Planètes (proxies) ; autres structures (modèle spatial LOD) | Distance stratégique et taille. |
| **Joueur dans une station** | Idem ; sa planète tourne dans le ciel | Station visible de loin selon sa taille. |
| **Joueur dans une Mini** | Hôte réel s’il est dans sa bulle, sinon ciel spatial | Une Mini en vol libre voit l’espace par le ciel. |

Un objet est rendu quand sa taille apparente dépasse un seuil (à calibrer). La même règle donne un petit objet proche et un gros objet lointain.

### 12.3 Modèles spatiaux

- Générés à partir des blocs du vaisseau, avec plusieurs niveaux de détail, et régénérés après modification avec un délai (pas à chaque bloc posé).

- Doivent inclure les ouvertures importantes (par exemple un hangar).

- Fondu entre modèle LOD et rendu réel pour éviter l’effet de pop.

## 13. Navigation et distances

Problème : la planète vue depuis le ciel d’un croiseur ne grossit pas tant que le croiseur reste dans sa dimension, alors qu’elle devrait se rapprocher dès qu’il avance.

- **Continuité de position.** La position stratégique d’un objet est celle de son hôte plus son décalage local converti par un facteur k, ce qui évite tout saut lors des changements de régime.

- **Deux régimes de propulsion.** Dans la bulle, propulseurs de manœuvre (lents). Hors de la bulle, moteur de croisière, avec une montée en puissance de 2 à 3 secondes qui masque le changement.

- **Vitesse selon la masse.** La vitesse maximale dépend de la distance au corps massif le plus proche (mass lock), ce qui rend l’approche continue.

- **Hystérésis.** Le rayon d’entrée dans un hôte est plus petit que son rayon de sortie, pour éviter les va-et-vient.

**Solution écartée :** rendre l’espace à grande distance dans le monde de la dimension (coût de rendu et de précision flottante).

## 14. Transitions sans écran de chargement

### 14.1 Ce que fait Immersive Portals (lu dans le code)

- **Serveur :** forceTeleportPlayer retire le joueur de l’ancienne dimension et l’ajoute dans la nouvelle sans envoyer le paquet de respawn de Minecraft. L’écran « Réception du niveau » n’est donc jamais déclenché.

- **Client :** le paquet de position porte la dimension. Le client change alors le monde du joueur à chaud, parmi les mondes client qu’il garde un par dimension.

- **Condition :** les chunks de destination doivent déjà être chez le client. L’API le permet : PortalAPI.addChunkLoaderForPlayer avec un ChunkLoader (dimension, position, rayon). Pour une Mini avec joueur, on précharge la zone du slot ou de l’hôte d’arrivée avant le transfert.

### 14.2 Séquence pour une Mini avec joueur

1.  Précharger les chunks de destination autour du joueur (ChunkLoader).

2.  Calculer la pose et la vitesse d’arrivée (section 10.3).

3.  Copier la Mini dans l’hôte d’arrivée, appliquer la vitesse.

4.  Téléporter le joueur et les entités transportées avec teleportEntityGeneral, avant la suppression de la source.

5.  Supprimer la source ; Sable envoie l’arrêt de suivi aux clients de l’ancienne dimension.

### 14.3 Compatibilité avec les shaders (à valider)

La condition posée pour D1 est que la dépendance ne casse rien d’autre. Le code ne permet pas de conclure : le README d’Immersive Portals annonce un rendu « à peu près compatible » avec certaines versions de Sodium et d’Iris, et son journal de modifications indique que la compatibilité Iris de la version 1.21.1 n’est pas complètement fonctionnelle (marquée expérimentale ensuite). Le mod modifie par mixins des classes de rendu centrales (instances de shaders, état de rendu, rendu du monde). Nous n’utilisons ni les portails ni leur rendu, mais ces mixins restent actifs. Ce point se vérifie en jeu (section 16).

## 15. Réseau, persistance et risques

### 15.1 Réseau

- Le serveur est la source de vérité de la couche stratégique.

- Les rails ne sont pas répliqués : seuls leurs paramètres et le temps de jeu sont envoyés.

- Les transforms des structures et des Minis sont répliqués aux clients dont le ciel peut les voir, à fréquence réduite, avec interpolation côté client.

### 15.2 Risques

| **Risque** | **Piste** |
| --- | --- |
| **Création de dimensions à l’exécution** | Tester la création à chaud sous NeoForge ; sinon pool de dimensions prédéclarées. |
| **Coût des dimensions chargées en parallèle** | Charger à la demande, décharger quand vide ; mesurer avec Spark. |
| **Coût d’un transfert par copie en un tick** | Mesurer (section 16) ; au besoin, limiter la taille des Minis ou étaler la copie sur quelques ticks hors vue du joueur. |
| **Compatibilité d’Immersive Portals avec les shaders et Sodium** | Tester dès le prototype (section 16) ; sinon, évaluer un fork limité aux fonctions de changement de monde client. |
| **Rendu client et suivi Sable lors d’un transfert** | Forcer le nouveau suivi côté client, comme IPSable ; éviter tout transfert répété à la frontière. |
| **Pipeline des modèles spatiaux** | Prototype précoce : génération depuis les blocs, LOD, régénération différée. |
| **Multijoueur (ciels, réplication)** | Conception réseau dès la première version ; tests à plusieurs joueurs. |
| **Précision flottante** | Double précision côté stratégique, coordonnées relatives à l’observateur côté rendu. |

## 16. Tests en jeu restants

Les sources répondent à toutes les questions de conception sauf celles ci-dessous, qui demandent une mesure ou un essai en jeu.

1.  **Coût du transfert par copie :** temps en millisecondes pour une Mini d’environ 100 blocs, puis 2 000 blocs, à comparer au budget d’un tick.

2.  **Gel et endormissement :** stabilité d’une Mini retenue par une contrainte fixe au monde (absence de tremblement), et endormissement effectif d’une Mini posée sur un pont sous gravité.

3.  **Immersive Portals en version stock :** une téléportation Mini + joueur sans écran de chargement, avec le suivi de Sable côté client, sans le fork d’IPSable.

4.  **Shaders :** Iris (ou Oculus) avec Sable et Immersive Portals installés, pendant le changement de dimension et en vol.

## 17. Points encore ouverts

- **Mini seule en vol libre avec un joueur :** la proposition est un slot dans la dimension « Ministructures » ; à confirmer, puisque la décision D3 réserve cette dimension aux hôtes éphémères.

- **Mégastructures :** ports, capacité d’approche et rails à détailler.

- **Gel ou origine flottante :** le gel est retenu ; la variante à origine flottante reste en réserve si les tests de la section 16 échouent.

- **Sorties au-delà de la bulle :** joueur perdu dans l’espace, capture par une navette, garde-fous.

## 18. Feuille de route

| **Phase** | **Contenu** | **Critère de réussite** |
| --- | --- | --- |
| **0** | Prototype de risques : Sable seul, Immersive Portals, shaders, copie de Mini (section 16) | Les quatre tests de la section 16 donnent un résultat clair. |
| **1** | Donnée spatiale, couche stratégique, rails orbitaux | Positions déterministes identiques sur serveur et clients. |
| **2** | Ciel piloté par l’objet associé à la dimension (extension du PoC) | Planètes en proxies correctement placées depuis un croiseur. |
| **3** | Dimensions de structures et superstructures, bulles et règles d’exclusion | Deux croiseurs visibles l’un de l’autre sans coupure. |
| **4** | Modèles spatiaux : génération, LOD, régénération | Un croiseur modifié voit son modèle mis à jour sans saccade. |
| **5** | Planètes : dimension, ciel selon la position, sites d’atterrissage | Ciel cohérent avec la position, pas de saccade à l’arrivée. |
| **6** | Ministructures : états, slots, transfert sans chargement, hôte éphémère | Une navette passe d’un hôte à un autre avec son pilote sans écran de chargement. |
| **7** | Navigation : loi de vitesse, mass lock, deux régimes de propulsion | Approche d’une planète continue et lisible. |
| **8** | Microstructures, mégastructures, multijoueur, optimisation | TPS stable avec plusieurs structures, Minis et joueurs. |
