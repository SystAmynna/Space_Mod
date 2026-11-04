package fr.systamynna.spacemod.core.render;

import java.util.Arrays;
import java.util.List;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import fr.systamynna.spacemod.core.SpaceObjectException;
import net.minecraft.nbt.CompoundTag;

/** Transform complet d'un objet de rendu, dans le référentiel du SpaceObject porteur : position, rotation (quaternion) et échelle (3 axes). */
public final class RenderTransform {
    public final Vector3d pos = new Vector3d();
    public final Quaterniond rot = new Quaterniond();
    public final Vector3d scale = new Vector3d(1, 1, 1);

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putDouble("x", pos.x);
        t.putDouble("y", pos.y);
        t.putDouble("z", pos.z);
        t.putDouble("qx", rot.x);
        t.putDouble("qy", rot.y);
        t.putDouble("qz", rot.z);
        t.putDouble("qw", rot.w);
        t.putDouble("sx", scale.x);
        t.putDouble("sy", scale.y);
        t.putDouble("sz", scale.z);
        return t;
    }

    /** Un champ invalide (non fini, quaternion non unitaire à 1e-6, échelle ≤ 0) retombe sur sa valeur neutre : un paquet invalide ne doit pas planter le client. */
    public static RenderTransform load(CompoundTag t) {
        RenderTransform r = new RenderTransform();
        double x = t.getDouble("x"), y = t.getDouble("y"), z = t.getDouble("z");
        if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) r.pos.set(x, y, z);
        Quaterniond q = new Quaterniond(t.getDouble("qx"), t.getDouble("qy"), t.getDouble("qz"), t.contains("qw") ? t.getDouble("qw") : 1);
        double n = Math.sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w);
        if (Double.isFinite(n) && Math.abs(n - 1) < 1e-6) r.rot.set(q).normalize();
        double sx = t.contains("sx") ? t.getDouble("sx") : 1, sy = t.contains("sy") ? t.getDouble("sy") : 1, sz = t.contains("sz") ? t.getDouble("sz") : 1;
        if (sx > 0 && sy > 0 && sz > 0 && Double.isFinite(sx + sy + sz)) r.scale.set(sx, sy, sz);
        return r;
    }

    public String describe() {
        return "pos=" + pos + ", rot=" + rot + ", scale=" + scale;
    }

    /** Euler (degrés) → quaternion, ordre {@code Rz(roll)·Rx(pitch)·Ry(yaw)} : le lacet (spin) est appliqué en premier, autour de l'axe incliné. */
    public static Quaterniond fromEuler(double yaw, double pitch, double roll) {
        return new Quaterniond().rotateZ(Math.toRadians(roll)).rotateX(Math.toRadians(pitch)).rotateY(Math.toRadians(yaw));
    }

    /**
     * Enchaîne des groupes {@code pos|scale a b c} ({@code ~} = inchangé) et {@code rot yaw pitch roll} (degrés, trois valeurs, pas de {@code ~}).
     * Tout est validé avant d'affecter.
     */
    public void editAll(String args) {
        String[] a = args.trim().split("\\s+");
        if (a.length % 4 != 0) throw usage();
        Vector3d p = new Vector3d(pos), s = new Vector3d(scale);
        Quaterniond q = new Quaterniond(rot);
        for (int i = 0; i < a.length; i += 4) {
            String[] g = Arrays.copyOfRange(a, i, i + 4);
            try {
                switch (g[0]) {
                    case "pos" -> apply(p, g);
                    case "scale" -> apply(s, g);
                    case "rot" -> q = fromEuler(Double.parseDouble(g[1]), Double.parseDouble(g[2]), Double.parseDouble(g[3]));
                    default -> throw usage();
                }
            } catch (NumberFormatException e) {
                throw usage();
            }
        }
        if (!(Double.isFinite(p.x + p.y + p.z) && Double.isFinite(q.x + q.y + q.z + q.w) && s.x > 0 && s.y > 0 && s.z > 0 && Double.isFinite(s.x + s.y + s.z))) throw usage();
        pos.set(p);
        rot.set(q);
        scale.set(s);
    }

    private static void apply(Vector3d v, String[] g) {
        double[] d = {v.x, v.y, v.z};
        for (int i = 0; i < 3; i++) if (!g[i + 1].equals("~")) d[i] = Double.parseDouble(g[i + 1]);
        v.set(d[0], d[1], d[2]);
    }

    /** Mot courant = dernier de {@code args} : début de groupe → mot-clé, sinon {@code ~}. */
    public static List<String> suggest(String args) {
        return (args.split(" ", -1).length - 1) % 4 == 0 ? List.of("pos", "rot", "scale") : List.of("~");
    }

    private static SpaceObjectException usage() {
        return new SpaceObjectException("amynnaspacemod.component.usage", "pos <x> <y> <z> | rot <yaw> <pitch> <roll> | scale <x> <y> <z> (~ = keep except rot, chainable; scale > 0)");
    }
}
