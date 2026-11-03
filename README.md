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

`src/main/resources/data/amynnaspacemod/default/*.json` (importées par `/spaceprovider refresh`). Unités : année-lumière (univers, galaxie), UA (systèmes, étoiles), km (planètes et lunes) ; périodes en temps réel (1 s = 20 ticks), à accélérer avec `/spaceclock multiplier`.

Sources (valeurs recopiées/arrondies, à recouper avant tout usage scientifique) :
- NASA/JPL Solar System Dynamics : éléments orbitaux moyens des planètes (J2000), paramètres physiques et orbitaux des satellites.
- NASA Exoplanet Archive et publications associées : périodes des exoplanètes (demi-grands axes recalculés par la 3ᵉ loi de Kepler), masses stellaires.
- SIMBAD / Gaia (ESA) / RECONS : positions et distances des étoiles proches ; Andromède : distance usuelle de 2,54 Mal.
