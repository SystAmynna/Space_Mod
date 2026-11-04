package fr.systamynna.spacemod.core.render;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.core.SpaceObjectException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Rendu du ciel d'une dimension ({@code /spacerender}) : le point de vue est le SpaceObject {@link #object}, décalé par
 * {@link #pos} (km), {@link #rot} (Euler lacet/tangage/roulis en degrés) et {@link #scale} (étire le ciel par axe).
 */
public final class RenderConfig {
    public final ResourceLocation dimension;
    public UUID object;
    public final double[] pos = {0, 0, 0};
    public final double[] rot = {0, 0, 0};
    public final double[] scale = {1, 1, 1};

    public RenderConfig(ResourceLocation dimension, UUID object) {
        this.dimension = dimension;
        this.object = object;
    }

    /** Copie de cette config (même décalage) rapportée à un autre objet. */
    public RenderConfig withObject(UUID other) {
        RenderConfig c = new RenderConfig(dimension, other);
        System.arraycopy(pos, 0, c.pos, 0, 3);
        System.arraycopy(rot, 0, c.rot, 0, 3);
        System.arraycopy(scale, 0, c.scale, 0, 3);
        return c;
    }

    /** Groupes {@code pos|rot|scale a b c} chaînables, {@code ~} = inchangé ; tout est validé avant d'affecter. */
    public void editAll(String args) {
        String[] a = args.trim().split("\\s+");
        if (a.length % 4 != 0) throw usage();
        double[] p = pos.clone(), r = rot.clone(), s = scale.clone();
        for (int i = 0; i < a.length; i += 4) {
            double[] v = switch (a[i]) {
                case "pos" -> p;
                case "rot" -> r;
                case "scale" -> s;
                default -> throw usage();
            };
            try {
                for (int k = 0; k < 3; k++) if (!a[i + 1 + k].equals("~")) v[k] = Double.parseDouble(a[i + 1 + k]);
            } catch (NumberFormatException e) {
                throw usage();
            }
        }
        for (double d : p) if (!Double.isFinite(d)) throw usage();
        for (double d : r) if (!Double.isFinite(d)) throw usage();
        for (double d : s) if (!(d > 0) || !Double.isFinite(d)) throw usage();
        System.arraycopy(p, 0, pos, 0, 3);
        System.arraycopy(r, 0, rot, 0, 3);
        System.arraycopy(s, 0, scale, 0, 3);
    }

    public static List<String> suggest(String args) {
        return (args.split(" ", -1).length - 1) % 4 == 0 ? List.of("pos", "rot", "scale") : List.of("~");
    }

    public String describe() {
        return "pos=" + Arrays.toString(pos) + ", rot=" + Arrays.toString(rot) + ", scale=" + Arrays.toString(scale);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("dimension", dimension.toString());
        t.putUUID("object", object);
        for (int i = 0; i < 3; i++) {
            t.putDouble("p" + i, pos[i]);
            t.putDouble("r" + i, rot[i]);
            t.putDouble("s" + i, scale[i]);
        }
        return t;
    }

    /** {@code null} si invalide (dimension/objet absents, valeurs non finies) : jamais d'exception sur des données reçues. */
    @Nullable
    public static RenderConfig load(CompoundTag t) {
        ResourceLocation dim = ResourceLocation.tryParse(t.getString("dimension"));
        if (dim == null || !t.hasUUID("object")) return null;
        RenderConfig c = new RenderConfig(dim, t.getUUID("object"));
        for (int i = 0; i < 3; i++) {
            double p = t.getDouble("p" + i), r = t.getDouble("r" + i), s = t.contains("s" + i) ? t.getDouble("s" + i) : 1;
            if (!Double.isFinite(p) || !Double.isFinite(r) || !(s > 0) || !Double.isFinite(s)) return null;
            c.pos[i] = p;
            c.rot[i] = r;
            c.scale[i] = s;
        }
        return c;
    }

    private static SpaceObjectException usage() {
        return new SpaceObjectException("amynnaspacemod.component.usage", "pos <x> <y> <z> | rot <yaw> <pitch> <roll> | scale <x> <y> <z> (~ = keep, chainable; scale > 0)");
    }
}
