package fr.systamynna.spacemod.core.component.components;

import java.util.List;

import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.component.SpaceComponent;
import net.minecraft.nbt.CompoundTag;

/** Position et rotation de l'objet, en double précision. */
public class Transform implements SpaceComponent {
    public static final String ID = "transform";

    public double x, y, z, yaw, pitch, roll;

    @Override
    public String id() { return ID; }

    @Override
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

    @Override
    public String describe() { return "pos=(" + x + ", " + y + ", " + z + "), rot=(yaw " + yaw + ", pitch " + pitch + ", roll " + roll + ")"; }

    @Override
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

    @Override
    public List<String> suggest(String args) {
        if (!args.contains(" ")) return List.of("pos", "rot");
        return args.trim().split("\\s+").length <= 4 ? List.of("~") : List.of();
    }

    private static SpaceObjectException usage() { return new SpaceObjectException("amynnaspacemod.component.usage", "pos <x> <y> <z> | rot <yaw> <pitch> <roll> (~ = keep)"); }

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
