# Crédits des textures d'astres (`assets/amynnaspacemod/textures/space/`)

## Attribution CC BY 4.0 — Solar System Scope

Les textures suivantes sont adaptées des textures de **Solar System Scope** (INOVE),
<https://www.solarsystemscope.com/textures/>, publiées sous licence
**Creative Commons Attribution 4.0 International (CC BY 4.0)**,
<https://creativecommons.org/licenses/by/4.0/>. Modifications : redimensionnées (2048 × 1024 → 1024 × 512 ou
512 × 256, filtre Lanczos) et converties de JPEG en PNG RGB ; `generic/star.png` est en plus réduite à sa luminance
(voir le tableau). Aucune garantie n'est donnée par l'auteur ; l'auteur ne cautionne pas ce mod.

`generic/star.png`, `sol/mercure.png`, `sol/venus.png`, `sol/terre.png`, `sol/mars.png`, `sol/jupiter.png`,
`sol/saturne.png`, `sol/uranus.png`, `sol/neptune.png`, `sol/lune.png`, `sol/ceres.png`, `sol/eris.png`,
`sol/haumea.png`, `sol/makemake.png`.

## Autres sources

- **USGS Astrogeology Science Center** (catalogue Astropedia, <https://astrogeology.usgs.gov/>, fichiers
  <https://asc-pds-services.s3.us-west-2.amazonaws.com/mosaic/>) : mosaïques globales réalisées à partir des données
  des missions de la NASA (Voyager, Galileo, Cassini, Viking, New Horizons). **Domaine public** (métadonnées FGDC :
  « Public domain », « Please cite authors ») ; les auteurs et missions sont cités ci-dessous. Images utilisées :
  aperçus 1024 × 512 publiés avec chaque produit, sauf Callisto (GeoTIFF complet).
- **NASA / JPL-Caltech** (« Solar System Simulator », <https://space.jpl.nasa.gov/tmaps/>, cartes « Caltech/JPL/USGS »
  d'après Voyager et Viking, auteur du site David Seal) : réutilisables à toute fin selon la politique d'usage des
  images du JPL (<https://www.jpl.nasa.gov/jpl-image-use-policy>), mention « Courtesy NASA/JPL-Caltech » ; aucune
  caution de la NASA, du JPL ou de Caltech n'est revendiquée.
- **Screaming Brain Studios**, « Planet Surface Textures » (OpenGameArt,
  <https://opengameart.org/content/planet-surface-textures>), **CC0 1.0** (domaine public,
  <https://creativecommons.org/publicdomain/zero/1.0/>) : textures procédurales (« fault formation » et colorisation
  peinte à la main, pas d'IA). Crédit facultatif, donné par courtoisie.

## Fichiers

Toutes les images sont équirectangulaires 2:1, nord en haut, est à droite, **longitude 0 au centre** (les cartes
centrées sur 180° ont été décalées d'une demi-largeur), PNG RGB 8 bits sans alpha. « Remplissage » = zone jamais
observée par les sondes (noire dans la source) remplie par un reflet très flouté de la zone observée voisine, ramené
vers la teinte moyenne (aucun relief inventé, aucune donnée réelle) ; le pourcentage est la part de l'image remplie.

| Fichier | Taille | Source (fichier d'origine) | Auteur / crédit | Licence | Modifications |
|---|---|---|---|---|---|
| `generic/star.png` | 1024 × 512 | Solar System Scope, `2k_sun.jpg` | Solar System Scope | CC BY 4.0 | luminance seule, étirée (99e centile → blanc) : teintée par la couleur de l'étoile au rendu ; redimensionnée |
| `sol/mercure.png` | 1024 × 512 | Solar System Scope, `2k_mercury.jpg` | Solar System Scope | CC BY 4.0 | redimensionnée, PNG |
| `sol/venus.png` | 1024 × 512 | Solar System Scope, `2k_venus_atmosphere.jpg` | Solar System Scope | CC BY 4.0 | redimensionnée, PNG |
| `sol/terre.png` | 1024 × 512 | Solar System Scope, `2k_earth_daymap.jpg` | Solar System Scope | CC BY 4.0 | redimensionnée, PNG |
| `sol/mars.png` | 1024 × 512 | Solar System Scope, `2k_mars.jpg` | Solar System Scope | CC BY 4.0 | redimensionnée, PNG |
| `sol/jupiter.png` | 1024 × 512 | Solar System Scope, `2k_jupiter.jpg` | Solar System Scope | CC BY 4.0 | redimensionnée, PNG |
| `sol/saturne.png` | 1024 × 512 | Solar System Scope, `2k_saturn.jpg` | Solar System Scope | CC BY 4.0 | redimensionnée, PNG |
| `sol/uranus.png` | 1024 × 512 | Solar System Scope, `2k_uranus.jpg` | Solar System Scope | CC BY 4.0 | redimensionnée, PNG |
| `sol/neptune.png` | 1024 × 512 | Solar System Scope, `2k_neptune.jpg` | Solar System Scope | CC BY 4.0 | redimensionnée, PNG |
| `sol/lune.png` | 512 × 256 | Solar System Scope, `2k_moon.jpg` | Solar System Scope | CC BY 4.0 | redimensionnée, PNG |
| `sol/ceres.png` | 512 × 256 | Solar System Scope, `2k_ceres_fictional.jpg` (carte **fictive**) | Solar System Scope | CC BY 4.0 | redimensionnée, PNG |
| `sol/eris.png` | 512 × 256 | Solar System Scope, `2k_eris_fictional.jpg` (carte **fictive**) | Solar System Scope | CC BY 4.0 | redimensionnée, PNG |
| `sol/haumea.png` | 512 × 256 | Solar System Scope, `2k_haumea_fictional.jpg` (carte **fictive**) | Solar System Scope | CC BY 4.0 | redimensionnée, PNG |
| `sol/makemake.png` | 512 × 256 | Solar System Scope, `2k_makemake_fictional.jpg` (carte **fictive**) | Solar System Scope | CC BY 4.0 | redimensionnée, PNG |
| `sol/io.png` | 512 × 256 | USGS, « Io Galileo SSI Global Color Merge Mosaic 1km » (aperçu 1024 px) | NASA / JPL, Galileo SSI ; USGS Astrogeology | domaine public | redimensionnée, PNG |
| `sol/europe.png` | 512 × 256 | USGS, « Europa Voyager - Galileo SSI Global Mosaic 500m » (aperçu) | NASA / JPL, Voyager et Galileo ; USGS Astrogeology (Archinal, Colvin, Davies, Gitlin, Kirk, Weller) | domaine public | centrée sur 0°, remplissage 5 % (bandes polaires), redimensionnée, PNG |
| `sol/ganymede.png` | 512 × 256 | USGS, « Ganymede Voyager - Galileo SSI Color Global Mosaic 1.4km » (aperçu) | NASA / JPL, Voyager et Galileo ; USGS Astrogeology | domaine public | centrée sur 0°, remplissage 5 % (pôles), redimensionnée, PNG |
| `sol/callisto.png` | 512 × 256 | USGS, `Callisto_Voyager_GalileoSSI_global_mosaic_1km.tif` | NASA / JPL, Voyager et Galileo ; USGS Astrogeology | domaine public | réduite, centrée sur 0°, remplissage 4 % (pôles), PNG RGB |
| `sol/encelade.png` | 512 × 256 | USGS, « Enceladus Cassini Global Mosaic 110m » (aperçu) | NASA / JPL / Space Science Institute, équipe Cassini | domaine public | redimensionnée, PNG |
| `sol/tethys.png` | 512 × 256 | USGS, « Tethys Cassini Global Mosaic 293m » (aperçu) | NASA / JPL / Space Science Institute | domaine public | redimensionnée, PNG |
| `sol/dione.png` | 512 × 256 | USGS, « Dione Cassini - Voyager Global Mosaic 154m » (aperçu) | NASA / JPL / Space Science Institute | domaine public | redimensionnée, PNG |
| `sol/rhea.png` | 512 × 256 | USGS, « Rhea Cassini - Voyager Global Mosaic 417m » (aperçu) | NASA / JPL / Space Science Institute | domaine public | redimensionnée, PNG |
| `sol/japet.png` | 512 × 256 | USGS, « Iapetus Cassini - Voyager Global Mosaic 803m » (aperçu) | NASA / JPL / Space Science Institute, équipe Cassini | domaine public | redimensionnée, PNG |
| `sol/titan.png` | 512 × 256 | USGS, « Titan Cassini ISS Global Mosaic 4005m » (PIA19658, aperçu) | NASA / JPL-Caltech / Space Science Institute | domaine public (produit NASA) | centrée sur 0° ; **brume** : couleur orangée uniforme (217, 167, 74) modulée à ±10 % par la carte de surface (vue de l'espace, la surface est cachée) ; redimensionnée, PNG |
| `sol/phobos.png` | 512 × 256 | USGS, « Phobos Viking Global Mosaic 5m » (cartes de P. Stooke, PDS, aperçu) | NASA, Viking ; P. Stooke ; contrôle DLR ; USGS Astrogeology | domaine public | redimensionnée, PNG |
| `sol/deimos.png` | 512 × 256 | JPL Solar System Simulator, `mar2kuu2.jpg` (Viking) | Courtesy NASA/JPL-Caltech, Caltech/JPL/USGS | politique d'usage JPL (toute fin) | redimensionnée, PNG RGB |
| `sol/miranda.png` | 512 × 256 | JPL Solar System Simulator, `ura5vuu2.jpg` (Voyager 2) | Courtesy NASA/JPL-Caltech, Caltech/JPL/USGS | politique d'usage JPL | remplissage 57 % (hémisphère nord jamais vu), redimensionnée, PNG RGB |
| `sol/ariel.png` | 512 × 256 | JPL Solar System Simulator, `ura1vuu2.jpg` (Voyager 2) | Courtesy NASA/JPL-Caltech, Caltech/JPL/USGS | politique d'usage JPL | remplissage 62 %, redimensionnée, PNG RGB |
| `sol/umbriel.png` | 512 × 256 | JPL Solar System Simulator, `ura2vuu2.jpg` (Voyager 2) | Courtesy NASA/JPL-Caltech, Caltech/JPL/USGS | politique d'usage JPL | remplissage 60 %, redimensionnée, PNG RGB |
| `sol/titania.png` | 512 × 256 | JPL Solar System Simulator, `ura3vuu2.jpg` (Voyager 2) | Courtesy NASA/JPL-Caltech, Caltech/JPL/USGS | politique d'usage JPL | remplissage 63 %, redimensionnée, PNG RGB |
| `sol/oberon.png` | 512 × 256 | JPL Solar System Simulator, `ura4vuu2.jpg` (Voyager 2) | Courtesy NASA/JPL-Caltech, Caltech/JPL/USGS | politique d'usage JPL | remplissage 62 %, redimensionnée, PNG RGB |
| `sol/triton.png` | 512 × 256 | USGS, « Triton Voyager 2 Global Color Mosaic 600m » (aperçu) | NASA / JPL, Voyager 2 ; Paul Schenk ; USGS Astrogeology | domaine public | remplissage 40 % (nord jamais vu), redimensionnée, PNG |
| `sol/pluton.png` | 512 × 256 | USGS, « Pluto New Horizons LORRI - MVIC Global Mosaic 300m » (juillet 2017, aperçu) | NASA / Johns Hopkins APL / Southwest Research Institute / Lunar and Planetary Institute, New Horizons | produit NASA, sans restriction d'accès (« Please cite authors ») | centrée sur 0°, remplissage 34 % (sud dans la nuit polaire), niveaux de gris colorés (brun (70, 40, 28) → beige (246, 232, 212)), redimensionnée, PNG |
| `sol/charon.png` | 512 × 256 | USGS, « Charon New Horizons LORRI MVIC Global Mosaic 300m » (aperçu) | NASA / Johns Hopkins APL / Southwest Research Institute / Lunar and Planetary Institute, New Horizons | produit NASA, sans restriction d'accès | remplissage 36 %, redimensionnée, PNG RGB |
| `generic/gas_hot_1.png` | 1024 × 512 | SBS, `Gaseous_17-1024x512.png` | Screaming Brain Studios | CC0 | saturation 55 %, luminosité 38 % |
| `generic/gas_hot_2.png` | 1024 × 512 | SBS, `Gaseous_10-1024x512.png` | Screaming Brain Studios | CC0 | saturation 60 %, luminosité 45 % |
| `generic/gas_temperate_1.png` | 1024 × 512 | SBS, `Gaseous_11-1024x512.png` | Screaming Brain Studios | CC0 | saturation 18 % |
| `generic/gas_temperate_2.png` | 1024 × 512 | SBS, `Gaseous_12-1024x512.png` | Screaming Brain Studios | CC0 | saturation 25 %, luminosité 97 % |
| `generic/gas_cold_1.png` | 1024 × 512 | SBS, `Gaseous_05-1024x512.png` | Screaming Brain Studios | CC0 | saturation 55 %, luminosité 105 % |
| `generic/gas_cold_2.png` | 1024 × 512 | SBS, `Gaseous_14-1024x512.png` | Screaming Brain Studios | CC0 | saturation 40 %, luminosité 95 % |
| `generic/ice_giant_1.png` | 1024 × 512 | SBS, `Gaseous_07-1024x512.png` | Screaming Brain Studios | CC0 | saturation 45 %, luminosité 110 % |
| `generic/ice_giant_2.png` | 1024 × 512 | SBS, `Gaseous_20-1024x512.png` | Screaming Brain Studios | CC0 | saturation 55 %, luminosité 85 % |
| `generic/rocky_hot_1.png` | 1024 × 512 | SBS, `Barren_01-1024x512.png` | Screaming Brain Studios | CC0 | saturation 35 %, luminosité 55 % |
| `generic/rocky_hot_2.png` | 1024 × 512 | SBS, `Martian_02-1024x512.png` | Screaming Brain Studios | CC0 | saturation 45 %, luminosité 60 % |
| `generic/rocky_temperate_1.png` | 1024 × 512 | SBS, `Sandy_01-1024x512.png` | Screaming Brain Studios | CC0 | saturation 60 % |
| `generic/rocky_temperate_2.png` | 1024 × 512 | SBS, `Tundra_02-1024x512.png` | Screaming Brain Studios | CC0 | saturation 65 % |
| `generic/rocky_temperate_3.png` | 1024 × 512 | SBS, `Dusty_02-1024x512.png` | Screaming Brain Studios | CC0 | saturation 55 % |
| `generic/rocky_icy_1.png` | 1024 × 512 | SBS, `Snowy_01-1024x512.png` | Screaming Brain Studios | CC0 | saturation 30 %, luminosité 112 % |
| `generic/rocky_icy_2.png` | 1024 × 512 | SBS, `Snowy_03-1024x512.png` | Screaming Brain Studios | CC0 | saturation 15 %, luminosité 110 %, teinte crème (236, 222, 200) à 45 % |
| `generic/moon_rocky_1.png` | 512 × 256 | SBS, `Barren_02-1024x512.png` | Screaming Brain Studios | CC0 | niveaux de gris, luminosité 85 %, redimensionnée |
| `generic/moon_rocky_2.png` | 512 × 256 | SBS, `Barren_04-1024x512.png` | Screaming Brain Studios | CC0 | saturation 20 %, luminosité 75 %, redimensionnée |
| `generic/moon_icy_1.png` | 512 × 256 | SBS, `Barren_03-1024x512.png` | Screaming Brain Studios | CC0 | niveaux de gris, contraste 80 %, luminosité 155 %, redimensionnée |
| `generic/moon_icy_2.png` | 512 × 256 | SBS, `Snowy_04-1024x512.png` | Screaming Brain Studios | CC0 | saturation 12 %, luminosité 108 %, redimensionnée |

Téléchargements du 2026-10-10 ; licences vérifiées à la source le même jour (page des textures de Solar System Scope,
métadonnées FGDC de l'USGS, politique d'usage des images du JPL, page OpenGameArt et `License.txt` du paquet SBS).
Conversion : script Python (Pillow) du sous-agent de la tâche T29, non versionné.
