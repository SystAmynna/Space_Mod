package fr.systamynna.spacemod.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.core.component.SpaceComponent;
import fr.systamynna.spacemod.core.component.SpaceComponents;
import net.minecraft.nbt.CompoundTag;

/** Objet spatial : UUID, nom (unique parmi ses frères) et parent (null pour la racine). */
public class SpaceObject {
    /** Nom de la racine. */
    public static final String ROOT_NAME = "universe";

    private final UUID uuid;
    private final String name;
    @Nullable
    private UUID parent;
    private final Map<String, SpaceComponent> components = new LinkedHashMap<>();

    public SpaceObject(UUID uuid, @Nullable UUID parent, String name) {
        this.uuid = uuid;
        this.parent = parent;
        this.name = name;
    }

    public UUID getUuid() { return uuid; }

    public String getName() { return name; }

    @Nullable
    public UUID getParent() { return parent; }

    void setParent(@Nullable UUID parent) { this.parent = parent; }

    public <T extends SpaceComponent> Optional<T> getComponent(Class<T> type) {
        return Optional.ofNullable(type.cast(components.values().stream().filter(type::isInstance).findFirst().orElse(null)));
    }

    public java.util.Collection<SpaceComponent> components() { return java.util.Collections.unmodifiableCollection(components.values()); }

    void putComponent(SpaceComponent c) { components.put(c.id(), c); }

    boolean removeComponent(Class<? extends SpaceComponent> type) {
        return components.values().removeIf(type::isInstance);
    }

    /** Payload persisté (les futurs champs s'ajoutent ici). */
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        CompoundTag comps = new CompoundTag();
        components.forEach((id, c) -> comps.put(id, c.save()));
        t.put("components", comps);
        return t;
    }

    public static SpaceObject load(UUID uuid, @Nullable UUID parent, CompoundTag data) {
        // Sans nom, c'est la racine créée avant l'introduction des noms.
        SpaceObject o = new SpaceObject(uuid, parent, data.contains("name") ? data.getString("name") : ROOT_NAME);
        CompoundTag comps = data.getCompound("components");
        for (String id : comps.getAllKeys()) {
            SpaceComponent c = SpaceComponents.load(id, comps.getCompound(id));
            if (c == null) AmynnaSpaceMod.LOGGER.warn("Composant inconnu '{}' ignoré sur {}.", id, uuid);
            else o.putComponent(c);
        }
        return o;
    }
}
