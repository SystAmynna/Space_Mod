package fr.systamynna.spacemod.client.render;

import org.joml.Vector3f;

/** Repère galactique du ciel : un seul calcul, partagé par le nuage et le motif de fond. */
final class Galactic {
    /** Classe utilitaire. */
    private Galactic() {}

    /**
     * Base {@code {e1, e2, n}} orthonormée directe de la galaxie vue par {@code ctx} : {@code e1} vers le centre galactique,
     * {@code n} normale du plan (axe Y du corps), {@code e2 = n × e1}. Si le centre est sur la normale, {@code e1} est un axe
     * quelconque du plan.
     */
    static Vector3f[] basis(RenderContext ctx) {
        Vector3f n = ctx.bodyRotation().transform(new Vector3f(0, 1, 0));
        Vector3f c = ctx.direction();
        Vector3f e1 = new Vector3f(c).fma(-c.dot(n), n);
        if (e1.lengthSquared() < 1e-6f) e1 = new Vector3f(n).cross(0, 0, 1);
        e1.normalize();
        return new Vector3f[] { e1, new Vector3f(n).cross(e1), n };
    }
}
