# Architecture du rendu spatial

*Mod Minecraft de voyage spatial seamless*

*Version 2 · 22 octobre 2026 · précise les sections 4 (couche rendu), 8 (rendu) et 9 (navigation) de `architecture_v6.md`*

Les points marqués **(proposé)** sont des choix de conception non validés par un test. Les points marqués **(à trancher)** attendent une décision.

**Changements depuis la v1 :** ciel galactique refait (§8) : résumé de système conservé par le `Frame`, nuage galactique local, galaxies externes et nuage décrits par les mêmes paramètres, étoiles de la galaxie limitées aux N plus proches, étoiles rendues en points lumineux ; ajout de la proposition des lumières spatiales (§19).

## 1. Objectif et périmètre

Dans une dimension hôte (croiseur, station, planète), le joueur voit l'espace : le système stellaire, les étoiles de la galaxie, les autres galaxies, les autres objets (y compris pilotés). Rien de cela n'est construit en blocs : c'est un rendu client, calculé à partir de données répliquées par le serveur.

**Inclus :** annulation du ciel vanilla, météo, ciel de l'hôte, fond d'étoiles, rails, pilotage, modèles spatiaux et leur LOD, réseau et horloge.

**Hors périmètre :** Minis, Micros, batailles, bouclier, passage seamless (section 11 de la v6), effet de saut détaillé.

## 2. Principes

- **Le client est une fonction pure** : (temps spatial, paramètres des objets, pose d'observation) → image. Aucun état de jeu n'est déduit du rendu.
- **Le serveur reste la source de vérité** (`StrategicLayer`). Le client ne simule ni ne décide.
- **Pas de position globale.** Tout est calculé relativement à l'observateur, en `double`, par l'ancêtre commun (`RailMath.relativePosition`).
- **Le `float` n'apparaît qu'à la fin** : direction normalisée, rayon angulaire, matrices de vue.
- **Distance et direction, pas coordonnées** : un objet lointain est une direction, un angle et une teinte ; sa distance ne sert qu'à trier et à calculer l'angle.
- **Le mouvement est une donnée** : rails = fonction du temps ; pilotage = instantanés interpolés.
- **Les transitions sont masquées** (fondus, hystérésis), jamais supprimées par un saut visible.

## 3. Vue d'ensemble

```
Serveur (StrategicLayer, SpaceClock)
   │  réseau : horloge · snapshot · deltas pilotés · modèles spatiaux
   ▼
Client
  1. Réplique        copie des objets utiles, horloge, modèles reçus,
                     résumés de systèmes voisins, paramètres galactiques
  2. État de vue     pose d'observation (hôte ∘ décalage du joueur), temps affiché
  3. Calculateur     pur, sans Minecraft : liste de « sprites célestes »
                     (direction, angle, couleur, LOD, modèle)
  4. Passes          ciel : fond → autres galaxies → motif lointain → nuage
                     galactique → étoiles de la galaxie → système → modèles
```

L'étage 3 ne touche ni OpenGL ni Minecraft : il est testable seul (section 16).

## 4. Annulation du ciel vanilla

Dans toute dimension hôte, le ciel, les nuages et le soleil/lune vanilla sont supprimés ; le fond est noir (brouillard compris).

- **Mécanisme (proposé)** : un `DimensionSpecialEffects` enregistré pour les types de dimension du mod, plutôt que le remplacement par réflexion de `ClientLevel.effects` employé par le PoC. Il fournit un type de ciel « aucun », pas de nuages, une couleur de brouillard noire, et appelle notre passe de ciel.
- **Dessin** : après le rendu du monde, avant la translucidité, avec une projection sans translation et un plan lointain (idée du PoC).
- **À vérifier** : comportement avec Sodium et Iris (shaders), qui remplacent une partie du pipeline de ciel.

## 5. Pluie et météo

- Annulée par défaut dans les dimensions hôtes **(proposé)**.
- Optionnelle par dimension : un hôte peut avoir de la pluie (planète). Configuration portée par le type de dimension ou par un composant de l'hôte **(à trancher)**.
- Pluie personnalisable : intensité, couleur, texture, son. Paramètres répliqués avec la dimension.
- La pluie d'une planète vue de l'espace n'existe pas : c'est une propriété de l'intérieur, pas du ciel.

## 6. Référentiel de vue

**Pose d'observation** = transform monde de l'hôte ∘ décalage local du joueur, en double. Le décalage local est la position du joueur dans la dimension, convertie dans l'unité de l'hôte (sans facteur de conversion, v6 §9).

- Calcul relatif par l'ancêtre commun : on ne passe jamais par une position globale entre échelles.
- Orientation : celle de l'hôte (croiseur : orientation stratégique ; planète : rotation propre). Le joueur ne la change pas, c'est le ciel qui tourne.
- **Changement de référentiel masqué** : quand l'objet de référence change (par exemple on s'éloigne d'une planète vers une autre), le candidat doit rester stable quelques ticks, puis un fondu lisse (courbe de lissage) évite tout saut. Idée reprise du PoC, valeurs à calibrer.

## 7. Rendu du système courant

Le système de l'hôte (`StrategicLayer.systemOf`) : astre principal (`StellarSystem.mainStar`), planètes, lunes, stations, autres objets. Les autres systèmes ne sont jamais chargés pour être dessinés : ils passent par leur résumé (§8).

Pour chaque objet visible, à chaque image :

1. Position au temps affiché (rails : `Controller.resolve`, section 9 ; pilotés : section 10).
2. Vecteur relatif à l'observateur, en double.
3. Direction (float), distance (double), rayon angulaire `asin(rayon / distance)`.
4. **Visibilité** : rendu si l'angle dépasse un seuil (v6 §8), le rayon vient de `Render.apparentRadius`. Sous un second seuil, l'objet n'est qu'un point lumineux.
5. **Ordre** : du plus loin au plus proche, sur des sphères de rayon décroissant (le rayon dessiné est fictif, seuls la direction et l'angle comptent).
6. **Représentation** : proxy (sphère texturée pour les astres, anneau) ou modèle spatial (section 11) selon `Render.model`. Une étoile reste un point lumineux avec halo tant qu'elle est lointaine (règle de la section 8).
7. **Éclairage** : l'astre principal éclaire les corps (phase visible selon l'angle étoile–corps–observateur).
8. **LOD du proxy** : niveaux de maillage choisis par la taille à l'écran, avec hystérésis (montée et descente à des seuils différents) pour éviter le va-et-vient.

## 8. Fond spatial et ciel galactique

### 8.1 Couches

Cinq couches de fond, plus le système courant (section 7), dessinées du plus lointain au plus proche.

| **#** | **Couche** | **Contenu** | **Données** |
| --- | --- | --- | --- |
| 1 | **Fond noir** | Sphère englobante | Aucune |
| 2 | **Autres galaxies** | Une par galaxie externe : point ou sprite plus gros qu'une étoile, ellipse si sa taille apparente le justifie ; orientée par ses paramètres, taille minimale garantie | Composant galactique du `Frame` de chaque galaxie, position relative |
| 3 | **Motif lointain** | Étoiles très nombreuses, fixes, pseudo-aléatoires par graine, insensibles à la position de l'observateur | Graine de la galaxie |
| 4 | **Nuage galactique local** | Anneau (bande) dans le ciel, positionné relativement à la place du système local dans la galaxie | Composant galactique du `Frame` de la galaxie courante |
| 5 | **Étoiles de la galaxie** | Les N systèmes les plus proches du système courant | Résumés de systèmes (8.2) |
| 6 | **Système courant** | Section 7 | Arbre du système chargé |

Le motif lointain est plus loin que les étoiles de la galaxie : il ne bouge pas quand l'observateur change de système, alors que les étoiles de la couche 5 ont une parallaxe.

### 8.2 Résumé de système

Pour dessiner un système depuis un autre, on n'a pas besoin de son arbre d'objets. Le `Frame` du `StellarSystem` est interrogé : s'il contient des **astres majeurs** (étoiles de plusieurs types, étoile à neutrons, trou noir…), chacun est converti en un **petit render** (un point de la bonne couleur, ou davantage) que le `Frame` conserve.

- **Composant** *(nom proposé : `SystemSkySummary`)*, porté par le `Frame` du système. Par astre majeur : type, couleur, luminosité, rayon (pour l'angle apparent), position dans le référentiel du système, et paramètres de rail si l'astre orbite (étoile double : deux points).
- **Aucun rechargement** : lire le ciel d'un autre système ne charge aucun `SpaceObject` du système. `StrategicLayer.systemOf` reste l'unité de chargement.
- **Dérivé, jamais source de vérité** *(proposé)* : recalculé quand un astre majeur est ajouté, retiré ou modifié ; en cas de désaccord, les objets gagnent. Sauvegardé avec le `Frame`.
- **Répliqué au client** (section 12). Il sert aux étoiles de la galaxie (couche 5), au rendu d'un système vu depuis un autre, et à la luminosité totale du système.
- **Système sans astre majeur** : résumé vide. Rien n'est visible, ou un point neutre *(à trancher)*.
- **Critère d'« astre majeur »** *(à trancher)* : étoiles, étoiles à neutrons, trous noirs, éventuellement naines brunes ; planètes exclues.

### 8.3 Étoiles de la galaxie

Les **N systèmes les plus proches** du système courant (N paramétrable, configuration de la galaxie ou du serveur, défaut à calibrer).

- Distance calculée en double dans le référentiel de la galaxie.
- Départage stable (identifiant) en cas d'égalité et hystérésis quand un rang change, pour éviter le clignotement.
- Chaque système est tracé depuis son résumé : direction, luminosité apparente (luminosité divisée par le carré de la distance), couleur. La parallaxe entre deux systèmes vient du calcul relatif.
- Au-delà des N, la masse stellaire lointaine est donnée par le motif lointain (couche 3) et le nuage (couche 4).

### 8.4 Les étoiles sont des points lumineux

Une étoile est très lumineuse : à moins d'en être proche, on ne distingue pas sa texture.

- À distance, une étoile est un **point lumineux avec halo**. La texture du disque n'est ni chargée ni visible.
- Le disque texturé n'apparaît que sous un seuil élevé de taille angulaire, avec un fondu entre halo et disque ; le halo domine tant que la luminosité sature l'écran.
- Conséquence : aucune texture par étoile du fond ; des textures seulement pour les astres proches, chargées à l'approche.

### 8.5 Nuage galactique local et galaxies externes

Les deux couches (4 et 2) se lisent dans **le même composant**, porté par le `Frame` d'une galaxie *(nom proposé : `GalacticAppearance`)* : plan et orientation du disque, rayon, épaisseur, densité ou opacité, couleurs (cœur, bras), bulbe et bras éventuels, graine de bruit.

- **Galaxie courante, vue de l'intérieur** : l'observateur est dans le disque. Le disque est rendu comme un **anneau** dans le ciel. Sa position et sa forme dépendent de la place du système local dans la galaxie : près du centre, une bande épaisse et lumineuse avec un bulbe d'un côté ; en périphérie, une bande plus fine et un centre d'un seul côté. C'est un calcul d'angles à partir de la position relative, pas un maillage à l'échelle.
- **Galaxies externes, vues de l'extérieur** : mêmes paramètres, observateur hors du disque. La description est projetée vue de l'extérieur : point ou sprite plus gros, ellipse si la galaxie est grande, orientée par son inclinaison.
- Une seule description à maintenir, deux projections.
- Rendu *(proposé)* : quad ou anneau avec une texture procédurale (bruit calculé à partir de la graine), pas de texture dédiée par galaxie.

## 9. Contrôleur de rail : rendu fluide

`RailFollower` est une fonction pure du temps spatial : `floorMod(ticks + phase, période)`, `StrictMath`. Il n'y a donc rien à interpoler ; la fluidité vient de l'horloge.

- **Vue extérieure** : un objet sur rail, vu depuis un hôte, est calculé au temps `T + partialTick` fractionnaire (`RailMath.fraction(double, …)`), pas au tick entier.
- **Vue depuis un hôte sur rail** (station en orbite, planète qui tourne) : la pose d'observation suit le rail ; le ciel tourne avec lui. Une planète en `Spin` fait tourner tout le ciel autour de l'axe Y de la planète.
- **Rail sur rail** : un objet qui orbite un objet qui orbite : composition des transforms par le parent (un corps ne transmet que sa translation, un `Frame` tout).
- **Condition** : une horloge client lisse et identique au serveur (section 13).

## 10. Contrôleur de pilotage : rendu fluide

`Pilotable` : la position vient du `Transform` stocké, modifié par le jeu, avec une vitesse (`vx, vy, vz` par tick) pour extrapoler.

Deux cas :

- **Vue de l'intérieur** (le joueur est dans l'hôte piloté) : le ciel bouge et tourne selon l'hôte. Aucun à-coup à la fréquence des paquets.
- **Vue d'un autre objet piloté** (un autre vaisseau, son modèle spatial se déplace ou tourne).

**Technique (proposé) :**

1. Le serveur envoie des instantanés horodatés en temps spatial : position, orientation, vitesse, vitesse angulaire.
2. Le client garde un historique court (quelques instantanés).
3. Il affiche volontairement **en retard** d'un délai d'interpolation (par exemple 2 intervalles d'envoi) : toujours deux instantanés autour du temps affiché.
4. Position : interpolation linéaire en double. Orientation : slerp avec renormalisation.
5. Instantané manquant : extrapolation bornée avec la vitesse de `Pilotable` (durée maximale à calibrer), puis gel.
6. Instantané qui diverge de la prédiction : correction douce (fondu de quelques ticks) ; au-delà d'un seuil (téléportation, saut), saut direct.
7. Pour l'hôte du joueur, le décalage d'affichage est réduit au minimum, car l'inertie ressentie doit rester proche du réel.

**Limite connue** : un objet très rapide fait ressentir le retard d'affichage ; à mesurer.

## 11. Modèle spatial

Les blocs de la structure de l'hôte sont convertis en un modèle 3D utilisable dans le ciel d'un autre observateur.

**Chaîne de production (proposé) :**

1. **Voxels** : lecture des blocs de la structure (dimension de l'hôte, volume logique).
2. **Surface** : seules les faces exposées à l'extérieur sont conservées. Les ouvertures importantes (hangar) restent ouvertes.
3. **Simplification** : fusion de faces coplanaires de même couleur ; couleur moyenne par bloc (pas de texture complète à distance).
4. **LOD** : plusieurs niveaux par regroupement de voxels (1, 2, 4, 8 blocs…), chacun avec un budget de sommets. Choix du niveau par la taille à l'écran, avec hystérésis.
5. **Versionnage** : chaque modèle porte un numéro de version ; une modification des blocs déclenche une régénération **différée** (délai de stabilisation), pas à chaque bloc posé.
6. **Fondu** à l'approche : modèle spatial et rendu réel se superposent brièvement pour masquer le remplacement.

**Où générer ? (à trancher)** Le serveur lit les blocs, le client non (pour une dimension non chargée côté client). Proposition : génération serveur, envoi en paquets fragmentés (section 12), cache disque côté client indexé par version.

**Budget** : nombre maximal d'objets en modèle détaillé, sommets par niveau, mémoire GPU totale. Valeurs à mesurer.

## 12. Réseau

Le réseau doit rendre le mouvement fluide sans coûter cher : on n'envoie que ce qui ne se déduit pas du temps.

| **Message** | **Contenu** | **Cadence** |
| --- | --- | --- |
| **Horloge** | Ancre et rythme de `SpaceClock` (pas le temps lui-même) | À la connexion, au changement de rythme, correction périodique |
| **Snapshot** | Objets utiles du système de l'hôte : composants, rails ; résumés des N systèmes les plus proches ; composants galactiques (galaxie courante et galaxies externes) | À la connexion, au changement de dimension ou de système |
| **Delta statique** | Objet créé, supprimé, ou composant modifié ; résumé de système modifié | Sur événement |
| **Pilotés** | Instantané horodaté (position, orientation, vitesses) | Environ 4 à 10 Hz, **(proposé)** |
| **Modèle spatial** | Voxels ou maillage fragmentés, avec version | Sur demande, hors chemin critique |

- **Rails : zéro trafic continu.** Seuls les paramètres sont envoyés, une fois.
- **Portée par intérêt** : un joueur ne reçoit que les objets de son système, plus les résumés des systèmes voisins (jamais leurs objets) et les paramètres galactiques utiles au fond.
- **Robustesse** : perte (extrapolation), doublon et désordre (horodatage), reconnexion et changement de dimension (nouveau snapshot complet), version de protocole.
- **Précision** : tout en `double` ; quaternions vérifiés (norme 1 à 1e-6) et valeurs non finies rejetées à la lecture. Un paquet invalide ne doit jamais planter le client.
- **Multijoueur** : l'état est partagé par tous ; seule la pose d'observation est propre au joueur.
- Un tableau de budget de bande passante (octets par objet piloté et par seconde) est à remplir après mesure.

## 13. Horloge client

Aujourd'hui `SpaceClock` n'existe que côté serveur. Le client en tient une copie : `espace = ancre + (tempsDeJeu − tempsDeJeuAncre) × rythme`.

- Temps affiché = temps spatial + `partialTick × rythme`, sans à-coup.
- Corrections serveur absorbées en douceur : petit écart rattrapé progressivement, grand écart recalé d'un coup.
- Rythme changé : l'ancre est déplacée, pas de saut (déjà vrai côté serveur).
- Pause ou serveur lent : le temps affiché ne recule jamais.

## 14. Précision numérique

- Données et calculs de position en `double`. Aucun `float` stocké ou envoyé.
- Distances en unité de l'ancêtre commun ; le calcul relatif évite que de grandes coordonnées absolues ne détruisent la précision d'un détail proche.
- Passage au `float` seulement pour la direction normalisée et le rayon angulaire.
- Objet à angle négligeable : point lumineux, pas de géométrie.
- Distance nulle ou valeur non finie : objet ignoré cette image, jamais d'exception.

## 15. Performance

- Culling angulaire avant tout calcul coûteux ; tri par distance sur un petit tableau réutilisé.
- Aucune allocation par image dans la boucle de rendu (tableaux et vecteurs réutilisés).
- Étoiles du fond : tampon de sommets statique, régénéré seulement si l'observateur change de système ou de galaxie ; recalcul des directions par lots si la parallaxe l'exige **(à mesurer)**.
- Le paramètre N borne le coût des étoiles de la galaxie ; les résumés se lisent sans charger l'arbre des objets d'un système.
- Modèles spatiaux : tampons GPU par niveau de détail, libérés hors de portée.
- Compatibilité Sodium, Iris et shaders : à tester dès la première passe de ciel.
- Cibles de FPS et nombre d'objets visibles à fixer avec un scénario de référence.

## 16. Tests

- **Calculateur de directions (JUnit, sans Minecraft)** : hôte tourné, distances très grandes, conversion double→float, objet derrière l'observateur, changement de référentiel.
- **Rails** : positions identiques entre serveur et client pour un temps donné ; temps très grands (10^12 ticks).
- **Interpolation** : instantanés perdus, doublons, désordre, retard ; continuité de la position affichée.
- **LOD** : déterminisme de la génération ; choix du niveau avec hystérésis.
- **Visuel** : dimension de test de `src/spacetest` (planète en rail, vaisseau piloté, autre galaxie) ; captures pour comparer.
- **Réseau** : paquets invalides rejetés sans plantage.
- **Étoiles de la galaxie** : sélection des N plus proches déterministe, départage des égalités, hystérésis de rang.
- **Résumé de système** : invalidé et recalculé quand un astre majeur change ; système sans astre majeur.
- **Nuage galactique** : anneau cohérent avec la position du système local (centre, périphérie) ; mêmes paramètres, projection extérieure pour une galaxie externe.
- **Étoiles** : seuil à partir duquel le disque texturé apparaît ; aucune texture chargée au-delà.

## 17. Risques et points ouverts

| **Risque / point ouvert** | **Piste** |
| --- | --- |
| **Sodium, Iris, shaders** | Tester tôt ; repli : passe de ciel dans un tampon séparé composé ensuite |
| **Génération des modèles spatiaux** (client ou serveur) | Proposition serveur + paquets versionnés ; mesurer le coût |
| **Coût réseau des modèles** | Fragmentation, cache disque, LOD envoyé à la demande |
| **Config de la pluie** (où la stocker) | Type de dimension ou composant de l'hôte |
| **Objets pilotés rapides et délai d'affichage** | Délai ajustable selon la vitesse ; mesurer |
| **Échelle galaxie / système** | Position relative à l'ancêtre commun |
| **Critère d'« astre majeur »** | À fixer (étoiles, étoiles à neutrons, trous noirs, naines brunes ?) |
| **Synchronisation du résumé de système avec les objets** | Résumé dérivé, recalculé à chaque changement d'astre majeur ; les objets gagnent |
| **Valeur par défaut de N** (étoiles de la galaxie) | Calibrer en jeu ; borne le coût |
| **Paramètres du composant galactique** | Jeu minimal (plan, rayon, épaisseur, couleurs, graine) ou plus artistique |
| **Cohérence entre l'anneau et la densité réelle d'étoiles** | Même source de paramètres ; à juger en jeu |
| **Seuil de lisibilité de la texture d'étoile** | Seuil angulaire élevé avec fondu ; à calibrer |
| **Coût du nuage procédural avec shaders** | Mesurer avec Iris |
| **Résumé d'un système vide** | Rien ou point neutre |
| **Fondu modèle spatial ↔ rendu réel** | Zone de recouvrement ; à juger en jeu |
| **Lien avec le passage seamless** | Un niveau client par dimension (v6 §11) ; la vue d'un autre niveau du PoC n'a pas été analysée |
| **Valeurs de calibrage** (seuils d'angle, hystérésis, délai, cadence) | Valeurs de départ à ajuster en jeu |

## 18. Feuille de route du rendu

Alignée sur les phases 1 à 6 de la v6.

| **Étape** | **Contenu** | **Critère de réussite** |
| --- | --- | --- |
| **R1** | Réplication de l'horloge et des objets d'un système ; calculateur de directions pur et testé | Les positions client égalent celles du serveur |
| **R2** | Ciel vanilla et nuages annulés ; météo selon la dimension | Fond noir sans effet vanilla, avec Sodium et Iris |
| **R3** | Système courant : astre principal, planètes en proxies, LOD | Planète bien placée depuis un croiseur |
| **R4a** | Résumés de systèmes (composant du `Frame`), étoiles de la galaxie (N plus proches), étoiles en points lumineux | Parallaxe cohérente entre deux systèmes, aucun objet d'un autre système chargé |
| **R4b** | Nuage galactique local, autres galaxies (mêmes paramètres), motif lointain | Anneau cohérent avec la position du système local |
| **R5** | Rails depuis un hôte fixe puis depuis un hôte en mouvement | Rotation du ciel fluide, sans saut |
| **R6** | Pilotage : interpolation, extrapolation, correction | Vaisseau tiers fluide à 20 TPS et paquets perdus |
| **R7** | Modèles spatiaux : génération, LOD, régénération différée, fondu | Modèle mis à jour sans saccade |
| **R8** | Optimisation, multijoueur, mesures | FPS et TPS stables avec plusieurs vaisseaux |
| **R9** *(optionnel)* | Lumières spatiales (section 19) | Ombres cohérentes avec l'astre visible |

## 19. Lumières spatiales *(proposition, non prioritaire)*

Cette section est une **proposition**, hors des étapes R1 à R8.

- **Idée** : les sources de lumière spatiales (étoiles, astre principal du système) deviennent des **lumières statiques dans le ciel** de la dimension hôte, avec des **ombres portées** (shadow cast) sur la structure, via Veil (embarqué par Sable, v6 §3).
- **À étudier** : direction de la lumière recalculée quand l'astre se déplace dans le ciel (statique à l'échelle d'un instant, orientée selon l'astre) ; coût des ombres ; compatibilité Iris et shaders ; effet jour/nuit dans l'hôte ; lumières multiples (étoile double).
- Non vérifié : l'API de Veil et le coût réel des ombres n'ont pas été consultés.

## Annexe : idées retenues du proof of concept

Le PoC `hololib-0.2.14` n'est pas recopié. Idées conservées ou écartées :

| **Idée du PoC** | **Décision** |
| --- | --- |
| Direction et distance en double, float à la fin | Retenue (§2, §14) |
| Sphères de rayon décroissant, tri lointain→proche | Retenue (§7) |
| LOD par taille à l'écran avec hystérésis | Retenue (§7, §11) |
| Fondu au changement de référentiel | Retenue, à calibrer (§6) |
| Horloge d'affichage qui rattrape l'état serveur | Retenue (§13) |
| Historique de poses interpolé | Retenue, avec délai explicite (§10) |
| Remplacement des effets de dimension par réflexion | Écartée : `DimensionSpecialEffects` enregistré (§4) |
| Position unique du joueur pour un seul système | Écartée : objets à composants et référentiels imbriqués |
| Interface holographique, éditeurs, radar | Hors sujet |
| Effet de saut (convergence des étoiles, traînées) | Différé |
| Un seul système, catalogue d'étoiles de fond, pas de nuage galactique ni de résumé de système | Étendue : résumés conservés par le `Frame`, N étoiles proches, nuage et galaxies externes (§8) |
