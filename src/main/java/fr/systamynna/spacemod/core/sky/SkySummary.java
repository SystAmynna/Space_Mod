package fr.systamynna.spacemod.core.sky;

import java.util.UUID;

/** Résumé de ciel d'un objet : sa position dans le repère de {@code parent} (km), son échelle propre (km), sa couleur RGB et sa luminosité (Soleil = 1). */
public record SkySummary(UUID uuid, UUID parent, double x, double y, double z, double scaleKm, int color, float lum) {}
