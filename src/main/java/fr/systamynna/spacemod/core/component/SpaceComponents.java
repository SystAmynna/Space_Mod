package fr.systamynna.spacemod.core.component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.component.components.ArtificialStructure;
import fr.systamynna.spacemod.core.component.components.Identity;
import fr.systamynna.spacemod.core.component.components.MajorContainer;
import fr.systamynna.spacemod.core.component.components.MajorObject;
import fr.systamynna.spacemod.core.component.components.MinorObject;
import fr.systamynna.spacemod.core.component.components.StellarSystem;
import fr.systamynna.spacemod.core.render.SpaceRender;
import net.minecraft.nbt.CompoundTag;

/** Registre des composants connus : l'unique endroit à compléter pour en ajouter un. */
public final class SpaceComponents {
    private record Entry(Class<? extends SpaceComponent> type, Supplier<SpaceComponent> create, Function<CompoundTag, SpaceComponent> load) {}

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    static {
        ENTRIES.put(Identity.ID, new Entry(Identity.class, () -> new Identity(""), Identity::load));
        ENTRIES.put(SpaceRender.ID, new Entry(SpaceRender.class, SpaceRender::new, SpaceRender::load));
        ENTRIES.put(MajorObject.ID, new Entry(MajorObject.class, MajorObject::new, MajorObject::load));
        ENTRIES.put(MajorContainer.ID, new Entry(MajorContainer.class, MajorContainer::new, MajorContainer::load));
        ENTRIES.put(MinorObject.ID, new Entry(MinorObject.class, MinorObject::new, MinorObject::load));
        ENTRIES.put(ArtificialStructure.ID, new Entry(ArtificialStructure.class, ArtificialStructure::new, ArtificialStructure::load));
        ENTRIES.put(StellarSystem.ID, new Entry(StellarSystem.class, StellarSystem::new, StellarSystem::load));
    }

    private SpaceComponents() {}

    public static List<String> ids() { return List.copyOf(ENTRIES.keySet()); }

    private static Entry entry(String id) {
        Entry e = ENTRIES.get(id);
        if (e == null) throw new SpaceObjectException("amynnaspacemod.component.unknown", id);
        return e;
    }

    public static Class<? extends SpaceComponent> type(String id) { return entry(id).type(); }

    public static SpaceComponent create(String id) { return entry(id).create().get(); }

    /** {@code null} si l'id est inconnu. */
    @Nullable
    public static SpaceComponent load(String id, CompoundTag data) {
        Entry e = ENTRIES.get(id);
        return e == null ? null : e.load().apply(data);
    }
}
