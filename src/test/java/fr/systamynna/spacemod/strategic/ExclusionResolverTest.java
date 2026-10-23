package fr.systamynna.spacemod.strategic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.strategic.ExclusionResolver.Obstacle;
import fr.systamynna.spacemod.strategic.ExclusionResolver.Result;
import fr.systamynna.spacemod.strategic.component.ExclusionShape;

/** Contacts sphériques : zone de rayon 100 à l'origine, vaisseau de rayon 10 (contact à 110). */
class ExclusionResolverTest {
    private static final double EPS = 1e-9;
    private final ExclusionResolver resolver = new SphereExclusion();
    private final Obstacle sphere = new Obstacle(new ExclusionShape.Sphere(100), new double[] { 0, 0, 0 });

    private Result resolve(double[] from, double[] to, double[] v, Obstacle... obstacles) {
        return resolver.resolve(from, to, v, 10, List.of(obstacles));
    }

    @Test
    void outsideIsUntouched() {
        Result r = resolve(new double[] { 200, 0, 0 }, new double[] { 150, 0, 0 }, new double[] { -50, 0, 0 }, sphere);
        assertFalse(r.touched());
        assertArrayEquals(new double[] { 150, 0, 0 }, r.position(), EPS);
        assertArrayEquals(new double[] { -50, 0, 0 }, r.velocity(), EPS);
    }

    @Test
    void insideIsPushedToTheSurfaceAndInwardVelocityIsRemoved() {
        Result r = resolve(new double[] { 105, 0, 0 }, new double[] { 100, 0, 0 }, new double[] { -5, 3, 0 }, sphere);
        assertTrue(r.touched());
        assertArrayEquals(new double[] { 110, 0, 0 }, r.position(), EPS);
        assertArrayEquals(new double[] { 0, 3, 0 }, r.velocity(), EPS);
    }

    @Test
    void fastMoveThroughTheZoneStopsAtTheSurface() {
        Result r = resolve(new double[] { 500, 0, 0 }, new double[] { -500, 0, 0 }, new double[] { -1000, 0, 0 }, sphere);
        assertTrue(r.touched());
        assertArrayEquals(new double[] { 110, 0, 0 }, r.position(), EPS);
        assertArrayEquals(new double[] { 0, 0, 0 }, r.velocity(), EPS);
    }

    @Test
    void glancingMoveSlidesAlongTheSurface() {
        // vers le centre depuis le dessus : le mouvement tangentiel est conservé
        Result r = resolve(new double[] { 0, 200, 0 }, new double[] { 60, 80, 0 }, new double[] { 60, -120, 0 }, sphere);
        double dist = Math.sqrt(r.position()[0] * r.position()[0] + r.position()[1] * r.position()[1]);
        assertTrue(r.touched());
        assertTrue(dist >= 110 - 1e-6, "jamais à l'intérieur");
        assertTrue(r.position()[0] > 0, "glisse vers +X");
        assertTrue(r.velocity()[0] > 0, "vitesse tangentielle conservée");
    }

    @Test
    void severalObstaclesAreAllRespected() {
        Obstacle other = new Obstacle(new ExclusionShape.Sphere(100), new double[] { 300, 0, 0 });
        Result r = resolve(new double[] { 150, 0, 0 }, new double[] { 150, 0, 0 }, new double[] { 0, 0, 0 }, sphere, other);
        // 150 est hors de la première (110) et de la seconde (|150−300| = 150 > 110)
        assertFalse(r.touched());
        Result inside = resolve(new double[] { 200, 0, 0 }, new double[] { 200, 0, 0 }, new double[] { 0, 0, 0 }, sphere, other);
        // à 200 : 100 de la seconde, donc repoussé à 110 d'elle, soit x = 190
        assertTrue(inside.touched());
        assertEquals(190, inside.position()[0], 1e-6);
    }
}
