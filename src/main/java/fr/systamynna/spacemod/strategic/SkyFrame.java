package fr.systamynna.spacemod.strategic;

import fr.systamynna.spacemod.strategic.component.Transform;

/**
 * Repère du ciel d'une dimension d'objet : rotation propre de l'hôte composée avec celle du placement et du port, et
 * décalage du port. Partagé par le rendu du ciel (client) et {@code /spacelook} (serveur), pour que les deux visent pareil.
 * shortcut: échelle de l'hôte et rotation des cadres intermédiaires ignorées, à revoir avec les shapes.
 *
 * @param toDimension passage du repère de l'hôte à celui de la dimension (rotation seule)
 * @param q quaternion de {@code toDimension}, {@code {x, y, z, w}} normalisé
 * @param port position du port dans le repère de l'hôte, après le spin de l'hôte
 */
public record SkyFrame(Transform toDimension, double[] q, double[] port) {
    /**
     * @param hostSpin transform local de l'hôte au temps voulu ({@link RailMath#local})
     * @param skyRotation rotation du placement composée avec celle du port ({@code HostedDimension.skyRotation})
     * @param portOffset position du port dans l'hôte
     */
    public static SkyFrame of(Transform hostSpin, double[] skyRotation, double[] portOffset) {
        double[] q = normalize(mul(new double[] { hostSpin.qx(), hostSpin.qy(), hostSpin.qz(), hostSpin.qw() }, skyRotation));
        Transform toDimension = new Transform(0, 0, 0, q[0], q[1], q[2], q[3], 1, 1, 1);
        double[] port = new Transform(0, 0, 0, hostSpin.qx(), hostSpin.qy(), hostSpin.qz(), hostSpin.qw(), 1, 1, 1)
                .apply(portOffset);
        return new SkyFrame(toDimension, q, port);
    }

    /** Position {@code rel} (relative à l'hôte, repère de l'hôte) vue du port, dans le repère de la dimension. */
    public double[] view(double[] rel) {
        return toDimension.invert(new double[] { rel[0] - port[0], rel[1] - port[1], rel[2] - port[2] });
    }

    /** Lacet et tangage Minecraft (degrés) pour regarder dans la direction {@code v} ; {@code null} si {@code v} est nul. */
    public static float[] lookAngles(double[] v) {
        double n = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (!(n > 0) || Double.isInfinite(n)) return null;
        return new float[] { (float) Math.toDegrees(Math.atan2(-v[0], v[2])), (float) Math.toDegrees(-Math.asin(v[1] / n)) };
    }

    /** Produit de Hamilton {@code a ⊗ b}, quaternions {@code {x, y, z, w}}. */
    public static double[] mul(double[] a, double[] b) {
        return new double[] {
                a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
                a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
                a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
                a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2] };
    }

    public static double[] conjugate(double[] q) {
        return new double[] { -q[0], -q[1], -q[2], q[3] };
    }

    public static double[] normalize(double[] q) {
        double n = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        return new double[] { q[0] / n, q[1] / n, q[2] / n, q[3] / n };
    }
}
