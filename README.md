# Amynna's Space Mod

Mod NeoForge 1.21.1 (Java 21).

Ce mod a vocation à proposer les features suivantes :
- Espace explorable
- Univers hierachisé (Univers -> Galaxie -> Système stellaires -> étoiles -> planètes -> ...)
- Pas de chargements (passage seamless entre les dimensions)
- Chargement dynamiques des dimensions
- Deux grand type de vaisseau :
  - Croiseur : énorme structure (avec sa propre dimension)
  - Navettes : petits vaisseau simulés grâce à Sable

## Données par défaut (provider `DEFAULT`)

`src/main/resources/data/amynnaspacemod/default/*.json` (importées par `/spaceprovider refresh`). `scale` d'un objet = nombre de km que vaut 1.0 dans son transform (`scale 10` : `x = 2` → 20 km ; défaut 1.0 = km) ; ici année-lumière (univers, galaxies, systèmes), UA (étoiles, planètes, exoplanètes), km (lunes). Les rayons de rendu sont en km ; périodes en temps réel (1 s = 20 ticks), à accélérer avec `/spaceclock multiplier`.

Les planètes, lunes, étoiles et exoplanètes portent un composant `render` (sphère texturée, rayon en km) ; les textures `assets/amynnaspacemod/textures/render/*.png` sont des placeholders générés, remplaçables par un resource pack. Les rayons des étoiles proches et des exoplanètes sont des valeurs de mémoire (souvent estimées), à recouper.

Sources (valeurs recopiées/arrondies, à recouper avant tout usage scientifique) :
- NASA/JPL Solar System Dynamics : éléments orbitaux moyens des planètes (J2000), paramètres physiques et orbitaux des satellites.
- NASA Exoplanet Archive et publications associées : périodes des exoplanètes (demi-grands axes recalculés par la 3ᵉ loi de Kepler), masses stellaires.
- SIMBAD / Gaia (ESA) / RECONS : positions et distances des étoiles proches ; Andromède : distance usuelle de 2,54 Mal.
- NED / SIMBAD : galaxies proches (coordonnées galactiques, distances, diamètres, inclinaisons), valeurs de mémoire arrondies à recouper. Leur position se déduit de `(l, b, d)` : `x = d·cos b·cos l − 26 673`, `y = d·sin b + 67,8`, `z = −d·cos b·sin l` (al, centre galactique à l'origine). L'inclinaison du disque est réelle, l'angle de position est choisi arbitrairement.

## Éléments de rendu

Un composant `render` porte une liste d'éléments, dessinés par le client. Ajout par `/spacecomponent edit render add <type> <arguments>` (voir l'usage affiché par la commande). Les paramètres d'un élément existant ne se modifient pas par commande : supprimer puis recréer l'élément, ou éditer le JSON et relancer `/spaceprovider refresh`.

| type | paramètre | rôle |
|---|---|---|
| `planet` | `texture`, `radius` | texture (`namespace:chemin`, ou asset du serveur) et rayon en km |
| `galaxy_cloud` | `radius` | rayon en km de la sphère englobante, donc taille de la galaxie |
| | `arms` | nombre de bras spiraux, entier de 1 à 8 (défaut 2) |
| | `thickness` | demi-épaisseur du disque, en fraction du rayon, ]0, 1] (défaut 0,04) ; petit = disque mince |
| | `core`, `edge` | couleurs RGBA du centre (bulbe) et du bord (bras) ; le nuage passe de l'une à l'autre. R, G, B dans [0, 1] ; A est un multiplicateur d'intensité dans [0, 4] (1 = référence). Défauts : `core` = (1, 0.72, 0.42, 1), `edge` = (0.5, 0.62, 1, 1). En commande : hexadécimal `RRGGBB` ou `RRGGBBAA` (alpha de 0 à 1) ; dans le JSON : `{"r": 1.0, "g": 0.72, "b": 0.42, "a": 1.0}` |
| `galaxy_stars` | `radius`, `arms`, `thickness` | comme le nuage : la densité d'étoiles suit le même disque spiral |
| | `density` | multiplicateur du nombre d'étoiles aléatoires, ]0, 20], défaut 1 |
| tous | `transform` | position, rotation (orientation du disque) et échelle dans le référentiel de l'objet porteur |

- **Augmenter la proportion d'étoiles aléatoires** : `density` (sur `galaxy_stars`). Il multiplie la probabilité qu'une cellule de la grille porte une étoile ; cette probabilité est plafonnée à 0,5 par cellule, donc au-delà d'un certain seuil les zones déjà denses saturent et seules les zones clairsemées gagnent des étoiles. `thickness` et `arms` agissent aussi, mais indirectement : un disque plus épais contient plus d'étoiles, plus haut au-dessus du plan, et les bras concentrent les étoiles le long de la spirale.
- `galaxy_cloud` et `galaxy_stars` sont indépendants (l'un peut exister sans l'autre). Leur ordre dans la liste fixe l'ordre de dessin : mettre le nuage avant les étoiles, avec la même `transform`.
- La graine du nuage et des étoiles vient de l'UUID du SpaceObject porteur : elle est stable dans un monde, mais change si le provider recrée l'objet dans un autre monde.
