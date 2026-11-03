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

/**
 * Objet spatial : UUID, nom (unique parmi ses frères) et parent (null pour la racine).
 * Son {@link #transform} est exprimé dans son propre référentiel parent ; son {@link #getScale() échelle} s'applique aux transforms de ses enfants.
 */
public class SpaceObject {
    /** Nom de la racine. */
    public static final String ROOT_NAME = "universe";

    private final UUID uuid;
    private final String name;
    @Nullable
    private UUID parent;
    /** Position/rotation relatives au parent. */
    public final Transform transform = new Transform();
    private double scale = 1.0;
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

    /** Unités du référentiel de cet objet (celui de ses enfants) dans celles de son parent. */
    public double getScale() { return scale; }

    void setScale(double scale) {
        if (!(scale > 0) || Double.isInfinite(scale)) throw new SpaceObjectException("amynnaspacemod.spaceobject.bad_scale", scale);
        this.scale = scale;
    }

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
        t.put("transform", transform.save());
        t.putDouble("scale", scale);
        CompoundTag comps = new CompoundTag();
        components.forEach((id, c) -> comps.put(id, c.save()));
        t.put("components", comps);
        return t;
    }

    public static SpaceObject load(UUID uuid, @Nullable UUID parent, CompoundTag data) {
        // Sans nom, c'est la racine créée avant l'introduction des noms.
        SpaceObject o = new SpaceObject(uuid, parent, data.contains("name") ? data.getString("name") : ROOT_NAME);
        o.transform.set(Transform.load(data.getCompound("transform")));
        if (data.contains("scale")) o.scale = data.getDouble("scale");
        CompoundTag comps = data.getCompound("components");
        for (String id : comps.getAllKeys()) {
            SpaceComponent c = SpaceComponents.load(id, comps.getCompound(id));
            if (c == null) AmynnaSpaceMod.LOGGER.warn("Composant inconnu '{}' ignoré sur {}.", id, uuid);
            else o.putComponent(c);
        }
        return o;
    }
}
