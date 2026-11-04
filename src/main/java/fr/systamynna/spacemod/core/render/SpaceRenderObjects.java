package fr.systamynna.spacemod.core.render;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.core.SpaceObjectException;
import net.minecraft.nbt.CompoundTag;

/** Registre des types de {@link SpaceRenderObject} : l'unique endroit (avec son drawer client) à compléter pour en ajouter un. */
public final class SpaceRenderObjects {
    private record Entry(Function<CompoundTag, SpaceRenderObject> load, Function<List<String>, SpaceRenderObject> create, String usage) {}

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    static {
        ENTRIES.put(PlanetRender.TYPE, new Entry(PlanetRender::load, a -> {
            if (a.size() != 2) throw usage(PlanetRender.TYPE);
            double r;
            try { r = Double.parseDouble(a.get(1)); } catch (NumberFormatException e) { throw usage(PlanetRender.TYPE); }
            if (!(r > 0) || !Double.isFinite(r)) throw usage(PlanetRender.TYPE);
            return new PlanetRender(new RenderTransform(), a.get(0), r);
        }, "planet <texture> <radius>"));
    }

    private SpaceRenderObjects() {}

    public static List<String> ids() { return List.copyOf(ENTRIES.keySet()); }

    /** {@code null} si le type est inconnu ou les données invalides. */
    @Nullable
    public static SpaceRenderObject load(CompoundTag t) {
        Entry e = ENTRIES.get(t.getString("type"));
        return e == null ? null : e.load().apply(t);
    }

    public static SpaceRenderObject create(String type, List<String> args) {
        Entry e = ENTRIES.get(type);
        if (e == null) throw new SpaceObjectException("amynnaspacemod.component.usage", "add " + String.join("|", ids()) + " ...");
        return e.create().apply(args);
    }

    private static SpaceObjectException usage(String type) { return new SpaceObjectException("amynnaspacemod.component.usage", "add " + ENTRIES.get(type).usage()); }
}
