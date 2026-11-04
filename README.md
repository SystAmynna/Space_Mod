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
