package fr.systamynna.spacemod.strategic;

import java.util.List;

import fr.systamynna.spacemod.strategic.component.ExclusionShape;

/**
 * Résout les contacts entre un vaisseau et des volumes d'exclusion. {@link SphereExclusion} le fait en maths pures ; un
 * moteur de collision (Sable / Rapier) pourra le remplacer en implémentant cette interface.
 */
public interface ExclusionResolver {
    /**
     * Volume à éviter.
     *
     * @param shape forme du volume
     * @param center centre du volume, dans le même référentiel que la position du vaisseau
     */
    record Obstacle(ExclusionShape shape, double[] center) {}

    /**
     * Résultat d'un déplacement.
     *
     * @param position position atteinte
     * @param velocity vitesse corrigée (composante entrante supprimée)
     * @param touched {@code true} si un volume a corrigé le déplacement
     */
    record Result(double[] position, double[] velocity, boolean touched) {}

    /**
     * Déplace le vaisseau de {@code from} à {@code to} sans entrer dans un volume.
     *
     * @param selfRadius rayon de la sphère englobante du vaisseau, ajouté à celui de chaque volume
     */
    Result resolve(double[] from, double[] to, double[] velocity, double selfRadius, List<Obstacle> obstacles);
}
