package fr.systamynna.spacemod.strategic;

import java.util.List;

/**
 * Contacts en maths pures : toute forme est ramenée à sa sphère englobante. Un déplacement qui traverserait un volume
 * s'arrête à sa surface et glisse le long, un vaisseau déjà dedans est repoussé radialement ; la composante de la vitesse
 * qui entre dans le volume est supprimée, la composante tangentielle est conservée.
 * shortcut: les volumes sont supposés immobiles pendant le tick (leur déplacement par rail est négligeable devant la
 * vitesse du vaisseau) ; à reprendre avec le moteur physique.
 */
public final class SphereExclusion implements ExclusionResolver {
    /** Deux passes : sortir d'un volume peut pousser dans un autre. */
    private static final int PASSES = 2;

    @Override
    public Result resolve(double[] from, double[] to, double[] velocity, double selfRadius, List<Obstacle> obstacles) {
        double[] p0 = from.clone();
        double[] p1 = to.clone();
        double[] v = velocity.clone();
        boolean touched = false;
        for (int pass = 0; pass < PASSES; pass++) {
            for (Obstacle o : obstacles) {
                double radius = o.shape().boundingRadius() + selfRadius;
                double[] c = o.center();
                double[] n = null;
                double[] d = sub(p1, p0);
                double[] rel = sub(p0, c);
                double a = dot(d, d);
                double b = 2 * dot(d, rel);
                double cc = dot(rel, rel) - radius * radius;
                if (cc >= 0 && a > 0) {
                    // départ à l'extérieur : le segment traverse-t-il la sphère ?
                    double disc = b * b - 4 * a * cc;
                    if (disc >= 0) {
                        double t = (-b - Math.sqrt(disc)) / (2 * a);
                        if (t >= 0 && t <= 1) {
                            double[] hit = add(p0, scale(d, t));
                            n = scale(sub(hit, c), 1 / radius);
                            double[] rest = scale(d, 1 - t);
                            // le reste du déplacement glisse le long de la surface
                            double into = Math.min(0, dot(rest, n));
                            p1 = add(hit, sub(rest, scale(n, into)));
                        }
                    }
                }
                double[] out = sub(p1, c);
                double dist = Math.sqrt(dot(out, out));
                if (dist < radius) {
                    // arrivée (ou départ) à l'intérieur : poussée radiale jusqu'à la surface
                    n = dist > 0 ? scale(out, 1 / dist) : new double[] { 1, 0, 0 };
                    p1 = add(c, scale(n, radius));
                }
                if (n != null) {
                    double into = Math.min(0, dot(v, n));
                    v = sub(v, scale(n, into));
                    touched = true;
                }
            }
            if (!touched) break;
        }
        return new Result(p1, v, touched);
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[] { a[0] - b[0], a[1] - b[1], a[2] - b[2] };
    }

    private static double[] add(double[] a, double[] b) {
        return new double[] { a[0] + b[0], a[1] + b[1], a[2] + b[2] };
    }

    private static double[] scale(double[] a, double k) {
        return new double[] { a[0] * k, a[1] * k, a[2] * k };
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
}
