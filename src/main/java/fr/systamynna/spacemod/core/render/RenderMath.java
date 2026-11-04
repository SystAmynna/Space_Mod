package fr.systamynna.spacemod.core.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import fr.systamynna.spacemod.core.SpaceObject;

/**
 * Calcul pur (sans Minecraft client) de la place d'un élément de rendu dans le ciel d'un observateur : tout en double,
 * relatif à l'ancêtre commun (jamais de position globale), le float n'apparaît qu'au dessin.
 */
public final class RenderMath {
    private RenderMath() {}

    /** Déplacement rigide {@code p ↦ t + R(p)} (km). */
    public record Pose(Vector3d t, Quaterniond q) {
        static Pose identity() { return new Pose(new Vector3d(), new Quaterniond()); }

        public Vector3d apply(Vector3d p) { return q.transform(new Vector3d(p)).add(t); }

        /** {@code outer ∘ this} : applique this, puis outer. */
        Pose then(Pose outer) { return new Pose(outer.apply(t), new Quaterniond(outer.q).mul(q)); }
    }

    /** Observateur : chaîne d'ancêtres de l'objet de référence (lui-même en tête) et décalage dans son référentiel. */
    public record Observer(List<SpaceObject> chain, Vector3d pos, Quaterniond rot, Vector3d scale) {}

    /** Élément placé dans le repère de l'observateur (km). */
    public record Placed(Vector3d pos, double radius, Vector3d scale, Quaterniond orientation) {
        public double distance() { return pos.length(); }

        /** Rayon angulaire (rad) du plus grand axe, {@code NaN} si l'observateur est dans l'objet. */
        public double angularRadius() {
            double r = radius * Math.max(scale.x, Math.max(scale.y, scale.z)), d = distance();
            return d > r ? Math.asin(r / d) : Double.NaN;
        }
    }

    /** Pose de {@code o} : son référentiel → celui de son parent (son transform, en unités de {@code scale} km, passe en km). */
    public static Pose local(SpaceObject o) {
        var tr = o.transform;
        return new Pose(new Vector3d(tr.x, tr.y, tr.z).mul(o.getScale()), RenderTransform.fromEuler(tr.yaw, tr.pitch, tr.roll));
    }

    /** {@code o}, son parent, son grand-parent… tant que {@code lookup} les connaît. */
    public static List<SpaceObject> chain(Function<UUID, SpaceObject> lookup, SpaceObject o) {
        List<SpaceObject> out = new ArrayList<>();
        for (SpaceObject c = o; c != null; c = c.getParent() == null ? null : lookup.apply(c.getParent())) out.add(c);
        return out;
    }

    /** Référentiel de {@code chain[0]} → celui de {@code chain[upTo]} (donne la position de l'observateur dans le repère d'un ancêtre). */
    public static Pose toAncestor(List<SpaceObject> chain, int upTo) {
        Pose acc = Pose.identity();
        for (int i = 0; i < upTo; i++) acc = acc.then(local(chain.get(i)));
        return acc;
    }

    public static Observer observer(Function<UUID, SpaceObject> lookup, SpaceObject host, RenderConfig c) {
        return new Observer(chain(lookup, host), new Vector3d(c.pos[0], c.pos[1], c.pos[2]), RenderTransform.fromEuler(c.rot[0], c.rot[1], c.rot[2]),
                new Vector3d(c.scale[0], c.scale[1], c.scale[2]));
    }

    /**
     * Place {@code rt} (porté par {@code carrier}, de rayon propre {@code radius}) dans le repère de l'observateur.
     * {@code null} si aucun ancêtre commun connu, position non finie ou confondue avec l'observateur.
     */
    @Nullable
    public static Placed place(Function<UUID, SpaceObject> lookup, Observer ob, SpaceObject carrier, RenderTransform rt, double radius) {
        List<SpaceObject> bChain = chain(lookup, carrier);
        Map<UUID, Integer> hostIdx = new HashMap<>();
        for (int i = 0; i < ob.chain.size(); i++) hostIdx.put(ob.chain.get(i).getUuid(), i);
        int i = -1, j = -1;
        for (int k = 0; k < bChain.size() && i < 0; k++) {
            Integer h = hostIdx.get(bChain.get(k).getUuid());
            if (h != null) { i = h; j = k; }
        }
        if (i < 0) return null;
        Pose pa = toAncestor(ob.chain, i), pb = toAncestor(bChain, j);
        Vector3d obsPos = pa.apply(ob.pos);
        Quaterniond obsQ = new Quaterniond(pa.q).mul(ob.rot);
        Vector3d d = pb.apply(rt.pos).sub(obsPos);
        if (!Double.isFinite(d.x + d.y + d.z) || d.lengthSquared() == 0) return null;
        Quaterniond inv = new Quaterniond(obsQ).conjugate();
        Vector3d rel = inv.transform(d).mul(ob.scale);
        Quaterniond orient = new Quaterniond(inv).mul(new Quaterniond(pb.q).mul(rt.rot));
        return new Placed(rel, radius, new Vector3d(rt.scale), orient);
    }

    /** {yaw, pitch} en degrés (convention Minecraft : yaw 0 = +Z, pitch négatif = vers le haut) pour regarder dans la direction {@code d} ; {@code null} si nulle. */
    @Nullable
    public static float[] lookAngles(Vector3d d) {
        double len = d.length();
        if (!(len > 0) || !Double.isFinite(len)) return null;
        return new float[] {(float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) Math.toDegrees(-Math.asin(d.y / len))};
    }
}
