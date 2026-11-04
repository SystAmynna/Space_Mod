package fr.systamynna.spacemod.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import net.minecraft.nbt.CompoundTag;

/** Position et rotation, en double précision, dans le référentiel du parent de l'objet qui le porte ; x, y, z sont en unités de {@link SpaceObject#getScale()} km. */
public class Transform {
    public double x, y, z, yaw, pitch, roll;

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putDouble("x", x);
        t.putDouble("y", y);
        t.putDouble("z", z);
        t.putDouble("yaw", yaw);
        t.putDouble("pitch", pitch);
        t.putDouble("roll", roll);
        return t;
    }

    public String describe() { return "pos=(" + x + ", " + y + ", " + z + "), rot=(yaw " + yaw + ", pitch " + pitch + ", roll " + roll + ")"; }

    public void edit(String args) {
        String[] a = args.trim().split("\\s+");
        if (a.length != 4 || !(a[0].equals("pos") || a[0].equals("rot"))) throw usage();
        boolean pos = a[0].equals("pos");
        double[] v = pos ? new double[] {x, y, z} : new double[] {yaw, pitch, roll};
        try { // tout est validé avant d'affecter
            for (int i = 0; i < 3; i++) if (!a[i + 1].equals("~")) v[i] = Double.parseDouble(a[i + 1]);
        } catch (NumberFormatException e) {
            throw usage();
        }
        if (pos) { x = v[0]; y = v[1]; z = v[2]; } else { yaw = v[0]; pitch = v[1]; roll = v[2]; }
    }

    /** Enchaîne plusieurs groupes {@code pos|rot a b c} ; tout est validé avant d'affecter. */
    public void editAll(String args) {
        String[] a = args.trim().split("\\s+");
        if (a.length % 4 != 0) throw usage();
        Transform copy = new Transform();
        copy.set(this);
        for (int i = 0; i < a.length; i += 4) copy.edit(String.join(" ", java.util.Arrays.copyOfRange(a, i, i + 4)));
        set(copy);
    }

    /** Mot courant = dernier de {@code args} : début de groupe → pos/rot, sinon {@code ~}. */
    public List<String> suggest(String args) {
        return (args.split(" ", -1).length - 1) % 4 == 0 ? List.of("pos", "rot") : List.of("~");
    }

    private static SpaceObjectException usage() { return new SpaceObjectException("amynnaspacemod.component.usage", "pos <x> <y> <z> | rot <yaw> <pitch> <roll> (~ = keep, chainable)"); }

    public void set(Transform o) { x = o.x; y = o.y; z = o.z; yaw = o.yaw; pitch = o.pitch; roll = o.roll; }

    /** Chiffres significatifs conservés, relativement au plus grand opérande : absorbe le bruit d'arrondi des doubles (~16 chiffres). */
    private static final int DIGITS = 14;

    /** Arrondit {@code v} à {@link #DIGITS} chiffres significatifs de {@code ref} (et non de {@code v} : 10.05 - 10 perd des chiffres). */
    private static double round(double v, double ref) {
        if (v == 0 || ref == 0 || !Double.isFinite(v) || !Double.isFinite(ref)) return v;
        return new BigDecimal(v).setScale(DIGITS - 1 - (int) Math.floor(Math.log10(Math.abs(ref))), RoundingMode.HALF_EVEN).doubleValue();
    }

    private static double add(double a, double b) { return round(a + b, Math.max(Math.abs(a), Math.abs(b))); }

    private static double sub(double a, double b) { return round(a - b, Math.max(Math.abs(a), Math.abs(b))); }

    /**
     * Monte d'un référentiel : {@code this}, exprimé dans le référentiel de l'objet porteur de {@code frame}, est converti dans celui du parent
     * de cet objet. Les unités de {@code this} et du résultat sont identiques ; {@code k} = (km par unité de {@code frame}) / (km par unité de {@code this}).
     */
    // shortcut: rotations additionnées, pas composées (les offsets enfants ne sont pas tournés par le parent) ; à revoir quand le rendu en aura besoin.
    public Transform up(Transform frame, double k) {
        Transform r = new Transform();
        r.x = add(x, k * frame.x);
        r.y = add(y, k * frame.y);
        r.z = add(z, k * frame.z);
        r.yaw = add(frame.yaw, yaw);
        r.pitch = add(frame.pitch, pitch);
        r.roll = add(frame.roll, roll);
        return r;
    }

    /** Inverse de {@link #up}. */
    public Transform down(Transform frame, double k) {
        Transform r = new Transform();
        r.x = sub(x, k * frame.x);
        r.y = sub(y, k * frame.y);
        r.z = sub(z, k * frame.z);
        r.yaw = sub(yaw, frame.yaw);
        r.pitch = sub(pitch, frame.pitch);
        r.roll = sub(roll, frame.roll);
        return r;
    }

    public static Transform load(CompoundTag t) {
        Transform r = new Transform();
        r.x = t.getDouble("x");
        r.y = t.getDouble("y");
        r.z = t.getDouble("z");
        r.yaw = t.getDouble("yaw");
        r.pitch = t.getDouble("pitch");
        r.roll = t.getDouble("roll");
        return r;
    }
}
