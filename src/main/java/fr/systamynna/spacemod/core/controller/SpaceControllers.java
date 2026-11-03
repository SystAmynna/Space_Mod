package fr.systamynna.spacemod.core.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.controller.controllers.MultipleController;
import fr.systamynna.spacemod.core.controller.controllers.OrbitalController;
import net.minecraft.nbt.CompoundTag;

/** Registre des contrôleurs connus : l'unique endroit à compléter pour en ajouter un. */
public final class SpaceControllers {
    private record Entry(Supplier<SpaceController> create, Function<CompoundTag, SpaceController> load) {}

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    static {
        ENTRIES.put(OrbitalController.ID, new Entry(OrbitalController::new, OrbitalController::load));
        ENTRIES.put(MultipleController.ID, new Entry(MultipleController::new, MultipleController::load));
    }

    private SpaceControllers() {}

    public static List<String> ids() { return List.copyOf(ENTRIES.keySet()); }

    public static SpaceController create(String id) {
        Entry e = ENTRIES.get(id);
        if (e == null) throw new SpaceObjectException("amynnaspacemod.controller.unknown", id);
        return e.create().get();
    }

    /** Payload du contrôleur + sa clé {@code type}. */
    public static CompoundTag save(SpaceController c) {
        CompoundTag t = c.save();
        t.putString("type", c.id());
        return t;
    }

    /** {@code null} si le type est inconnu. */
    @Nullable
    public static SpaceController load(CompoundTag t) {
        Entry e = ENTRIES.get(t.getString("type"));
        if (e == null) {
            AmynnaSpaceMod.LOGGER.warn("Contrôleur inconnu '{}' ignoré.", t.getString("type"));
            return null;
        }
        return e.load().apply(t);
    }
}
