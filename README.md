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

Les planètes, lunes et exoplanètes portent un composant `render` (sphère texturée, rayon en km), les étoiles (Soleil, naines blanches comprises) un `star` procédural dont les teintes suivent la classe spectrale ; les textures `assets/amynnaspacemod/textures/render/*.png` sont des placeholders générés, remplaçables par un resource pack. Les rayons des étoiles proches et des exoplanètes sont des valeurs de mémoire (souvent estimées), à recouper.

Sources (valeurs recopiées/arrondies, à recouper avant tout usage scientifique) :
- NASA/JPL Solar System Dynamics : éléments orbitaux moyens des planètes (J2000), paramètres physiques et orbitaux des satellites.
- NASA Exoplanet Archive et publications associées : périodes des exoplanètes (demi-grands axes recalculés par la 3ᵉ loi de Kepler), masses stellaires.
- Objets compacts (`compact_objects.json`, valeurs de mémoire arrondies à recouper) : Cygnus X-1 (21,2 M☉, 7 200 al), Sagittarius A* (4,3·10⁶ M☉, centre galactique), Crab Pulsar PSR B0531+21 (étoile à neutrons, 6 500 al) ; positions déduites de `(l, b, d)` comme ci-dessous. Longueur des jets, inclinaison magnétique du pulsar (45°) et sa rotation (ralentie à 2 rad/s pour rester lisible, réelle : 33,5 ms), bord intérieur du disque de Sgr A* (1,5 Rs, collé à l'ombre) et inclinaison du disque : choix artistiques.
- Anneaux de Saturne (NASA/JPL) : anneaux C 74 658–92 000 km, B 92 000–117 580 km, A 122 170–136 775 km ; la division de Cassini est l'écart entre B et A.
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
| `star` | `radius` | rayon en km ; étoile sans texture : de loin un disque éblouissant à bloom doux et fines stries (rayon angulaire < ~0,03 rad), de près (> ~0,10 rad, fondu entre les deux) un plasma animé en granules, émissif |
| | (de loin) | de loin la sphère est transparente (lueur seule, cœur blanc-chaud) ; la taille du rayonnement de l'étoile locale croît avec la distance (apparente ~constante dans le système) |
| | (flare) | le flare suit une parabole `x(2 − x)` de `x = distance à l'étoile / distance de l'objet le plus éloigné de son système` : il croît jusqu'à `x = 1` puis décroît symétriquement (0 en `x = 0` et `x ≥ 2`) |
| | (portée) | la portée angulaire du rayonnement est plafonnée à 60° (π/3) autour de l'étoile |
| | (renforcement) | l'étoile du système stellaire où se trouve l'observateur (voir `stellar_system` ci-dessous) est renforcée selon le flare : cœur plus brillant, bloom plus large, stries plus longues ; sans flare (hors système, au centre, ≥ 2× la distance de l'objet le plus éloigné) elle reste visible comme une étoile lointaine ordinaire (base de taille angulaire constante) |
| | `hot`, `cool` | couleurs RGBA des zones chaudes et froides du plasma (défaut jaune doré / orange) |
| | `speed`, `granulation`, `emissive` | vitesse d'animation (0 = figé, ≤ 10), nombre de granules (0 = lisse, ≤ 10), défaut 1 ; intensité émissive du plasma vu de près (0 à 10, défaut 1,5) |
| `black_hole` | `radius`, `color` | rayon en km de la sphère apparente (ombre ≈ 2,6 rayons de Schwarzschild) et sa couleur (défaut noir) ; observateur à l'intérieur : tout le ciel prend cette couleur |
| `distorsion` | `radius`, `strength`, `extent` | trou noir / trou blanc : `radius` = rayon de l'horizon en km ; dessine l'ombre (≈ 2,6 R) et déforme tout le ciel derrière (anneau d'Einstein), y compris un disque `ring` du même objet ; `strength` signé (> 0 trou noir, < 0 trou blanc, déformation inverse et ombre blanche) ; en plongeant, l'univers se replie en bulle derrière l'observateur, devient lumineux et flou de 40 % à 20 % de R, puis s'éteint jusqu'au noir à 10 % |
| | `strength`, `extent` | rayon d'Einstein et portée de la déformation, en rayons de l'objet (défauts 2,6 et 10) |
| `gamma_eruption` | `radius`, `spin`, `tilt` | longueur en km d'un jet ; deux jets le long de l'axe Y local (orienter avec `transform rot`) ; pulsar : `spin` (rad/s, phase sur l'horloge spatiale) autour de Y et `tilt` (°) des jets sur cet axe |
| | `width`, `colorA`, `colorB` | demi-largeur à la pointe (fraction de la longueur, ]0, 1], défaut 0,05) et dégradé base → pointe |
| | `speed`, `seed` | vitesse d'animation (≤ 20) et graine d'animation entière (s'ajoute à celle de l'UUID) |
| `ring` | `inner`, `radius` | rayons intérieur et extérieur en km (anneau dans le plan y=0 local : anneau de planète ou disque d'accrétion) |
| | `colorInner`, `colorOuter`, `opacity` | dégradé de couleurs et opacité ]0, 1] |
| | `emissive`, `occluder` | `true` = brille (alpha des couleurs = intensité) ; rayon en km de l'astre central qui cache la partie arrière (0 = aucun) |
| tous | `transform` | position, rotation (orientation du disque) et échelle dans le référentiel de l'objet porteur |

- **Composant `stellar_system`** : tag sans donnée posé sur chaque système stellaire (le repère qui contient une étoile et ses astres ; tous ceux des données par défaut). C'est le référentiel du rendu des étoiles : si l'observateur (joueur ou hôte) est dans un objet portant `stellar_system` (lui-même ou un ancêtre), les étoiles `star` de ce système sont rendues comme locales. Hors de tout système, elles sont ordinaires. Les mondes existants reçoivent le tag après `/spaceprovider refresh`.
- **Composant `artificial_structure`** : tag sans donnée pour les vaisseaux et stations. Un objet qui le porte n'est pas « notable » : lui et son sous-arbre sont ignorés pour la distance de l'objet le plus éloigné d'un système stellaire (qui fixe le pic de flare des étoiles).
- **Augmenter la proportion d'étoiles aléatoires** : `density` (sur `galaxy_stars`). Il multiplie la probabilité qu'une cellule de la grille porte une étoile ; cette probabilité est plafonnée à 0,5 par cellule, donc au-delà d'un certain seuil les zones déjà denses saturent et seules les zones clairsemées gagnent des étoiles. `thickness` et `arms` agissent aussi, mais indirectement : un disque plus épais contient plus d'étoiles, plus haut au-dessus du plan, et les bras concentrent les étoiles le long de la spirale.
- `galaxy_cloud` et `galaxy_stars` sont indépendants (l'un peut exister sans l'autre). Leur ordre dans la liste fixe l'ordre de dessin : mettre le nuage avant les étoiles, avec la même `transform`.
- La graine du nuage et des étoiles vient de l'UUID du SpaceObject porteur : elle est stable dans un monde, mais change si le provider recrée l'objet dans un autre monde.
