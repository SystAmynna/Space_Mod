package fr.systamynna.spacemod.core.controller.controllers;

import java.util.List;

import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.Transform;
import fr.systamynna.spacemod.core.controller.SpaceController;
import net.minecraft.nbt.CompoundTag;

/**
 * Rail orbital képlérien autour de l'origine du parent, plus rotation propre de l'objet.
 * Angles en degrés, périodes en ticks.
 */
public class OrbitalController implements SpaceController {
    public static final String ID = "orbital";

    /** Demi-grand axe. */
    public double a = 1;
    /** Excentricité, 0 ≤ e < 1. */
    public double e = 0;
    /** Inclinaison du plan orbital. */
    public double i = 0;
    /** Longitude du nœud ascendant (Ω). */
    public double node = 0;
    /** Argument du périastre (ω). */
    public double peri = 0;
    /** Anomalie moyenne à t = 0. */
    public double m0 = 0;
    /** Période orbitale. */
    public double period = 1200;
    /** Période de rotation propre ; 0 = pas de rotation, négatif = rétrograde. */
    public double spin = 0;
    public double spinPhase = 0;
    /** Inclinaison de l'axe de rotation (écrite dans {@code roll}). */
    public double tilt = 0;

    @Override
    public String id() { return ID; }

    // invocation de la magie noir des maths :)
    @Override
    public void apply(double ticks, Transform out) {
        // Fraction de tour d'abord : garde la précision quand ticks est très grand.
        double turns = ticks / period + m0 / 360;
        double m = 2 * StrictMath.PI * (turns - Math.floor(turns));
        // Kepler : E - e sin E = M, Newton.
        // shortcut: 20 itérations depuis M + e sin M, suffisant jusqu'à e ≈ 0.97 ; au-delà, changer le point de départ ou passer à Halley.
        double ea = m + e * StrictMath.sin(m);
        for (int k = 0; k < 20; k++) ea -= (ea - e * StrictMath.sin(ea) - m) / (1 - e * StrictMath.cos(ea));
        double xp = a * (StrictMath.cos(ea) - e);
        double yp = a * StrictMath.sqrt(1 - e * e) * StrictMath.sin(ea);
        // Rotations ω (dans le plan), i (autour de la ligne des nœuds), Ω (autour de Y) ; le plan de référence est XZ, Y vers le haut.
        double cw = StrictMath.cos(StrictMath.toRadians(peri)), sw = StrictMath.sin(StrictMath.toRadians(peri));
        double ci = StrictMath.cos(StrictMath.toRadians(i)), si = StrictMath.sin(StrictMath.toRadians(i));
        double cn = StrictMath.cos(StrictMath.toRadians(node)), sn = StrictMath.sin(StrictMath.toRadians(node));
        double x1 = cw * xp - sw * yp, y1 = sw * xp + cw * yp; // dans le plan orbital
        double z2 = ci * y1; // inclinaison autour de la ligne des nœuds (X) : y1 se répartit entre Z et la hauteur
        out.x = cn * x1 - sn * z2;
        out.z = sn * x1 + cn * z2;
        out.y = si * y1;
        if (spin != 0) {
            // Fraction de tour d'abord (même raison que l'orbite).
            double t = ticks / spin + spinPhase / 360;
            out.yaw = 360 * (t - Math.floor(t));
            out.pitch = 0;
            out.roll = tilt;
        }
    }

    @Override
    public String describe() {
        return "a=" + a + ", e=" + e + ", i=" + i + ", node=" + node + ", peri=" + peri + ", m0=" + m0 + ", period=" + period
                + ", spin=" + spin + ", spinphase=" + spinPhase + ", tilt=" + tilt;
    }

    private static final List<String> KEYS = List.of("a", "e", "i", "node", "peri", "m0", "period", "spin", "spinphase", "tilt");

    @Override
    public void edit(String args) {
        // Tout est validé avant d'être appliqué : une erreur ne laisse pas un contrôleur à moitié édité.
        OrbitalController n = copy();
        for (String w : args.trim().split("\\s+")) {
            String[] kv = w.split("=", 2);
            if (kv.length < 2 || !KEYS.contains(kv[0])) throw usage();
            if (kv[1].equals("~")) continue; // ~ = garder la valeur actuelle
            double v;
            try {
                v = Double.parseDouble(kv[1]);
            } catch (NumberFormatException ex) {
                throw usage();
            }
            if (!Double.isFinite(v)) throw usage();
            switch (kv[0]) {
                case "a" -> n.a = v;
                case "e" -> n.e = v;
                case "i" -> n.i = v;
                case "node" -> n.node = v;
                case "peri" -> n.peri = v;
                case "m0" -> n.m0 = v;
                case "period" -> n.period = v;
                case "spin" -> n.spin = v;
                case "spinphase" -> n.spinPhase = v;
                case "tilt" -> n.tilt = v;
                default -> throw usage();
            }
        }
        if (n.e < 0 || n.e >= 1 || n.period <= 0 || n.a < 0) throw usage();
        a = n.a; e = n.e; i = n.i; node = n.node; peri = n.peri; m0 = n.m0; period = n.period;
        spin = n.spin; spinPhase = n.spinPhase; tilt = n.tilt;
    }

    private OrbitalController copy() {
        OrbitalController c = new OrbitalController();
        c.a = a; c.e = e; c.i = i; c.node = node; c.peri = peri; c.m0 = m0; c.period = period;
        c.spin = spin; c.spinPhase = spinPhase; c.tilt = tilt;
        return c;
    }

    @Override
    public List<String> suggest(String args) {
        String last = args.substring(args.lastIndexOf(' ') + 1);
        int eq = last.indexOf('=');
        if (eq < 0) return KEYS.stream().map(k -> k + "=").toList();
        return last.length() == eq + 1 ? List.of(last + "~") : List.of();
    }

    private static SpaceObjectException usage() {
        return new SpaceObjectException("amynnaspacemod.controller.usage",
                "a=<>=0> e=<[0,1)> i=<deg> node=<deg> peri=<deg> m0=<deg> period=<ticks, >0> spin=<ticks, 0=off> spinphase=<deg> tilt=<deg> (chainable, ~ = keep)");
    }

    @Override
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putDouble("a", a); t.putDouble("e", e); t.putDouble("i", i); t.putDouble("node", node); t.putDouble("peri", peri);
        t.putDouble("m0", m0); t.putDouble("period", period);
        t.putDouble("spin", spin); t.putDouble("spinPhase", spinPhase); t.putDouble("tilt", tilt);
        return t;
    }

    public static OrbitalController load(CompoundTag t) {
        OrbitalController c = new OrbitalController();
        c.a = t.getDouble("a"); c.e = t.getDouble("e"); c.i = t.getDouble("i"); c.node = t.getDouble("node"); c.peri = t.getDouble("peri");
        c.m0 = t.getDouble("m0"); c.period = t.getDouble("period");
        c.spin = t.getDouble("spin"); c.spinPhase = t.getDouble("spinPhase"); c.tilt = t.getDouble("tilt");
        return c;
    }
}
