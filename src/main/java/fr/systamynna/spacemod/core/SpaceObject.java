package fr.systamynna.spacemod.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.core.component.SpaceComponent;
import fr.systamynna.spacemod.core.component.SpaceComponents;
import fr.systamynna.spacemod.core.controller.SpaceController;
import fr.systamynna.spacemod.core.controller.SpaceControllers;
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
    /** Fait bouger l'objet ; null = immobile. Répliqué au client. */
    @Nullable
    private SpaceController controller;
    /** Temps (ticks de SpaceClock) auquel {@link #transform} a été calculé par le contrôleur ; NaN si jamais. */
    private double transformTime = Double.NaN;

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

    @Nullable
    public SpaceController getController() { return controller; }

    void setController(@Nullable SpaceController controller) {
        this.controller = controller;
        transformTime = Double.NaN;
    }

    /** Recalcule {@link #transform} à l'instant {@code ticks} (sans le persister ni le répliquer). */
    public void step(double ticks) {
        if (controller == null) return;
        controller.apply(ticks, transform);
        transformTime = ticks;
    }

    /** Rechargement ou réception : laisse le contrôleur corriger le transform sauvegardé. */
    public void resume(double nowTicks) {
        if (controller == null) return;
        if (Double.isNaN(transformTime)) controller.apply(nowTicks, transform);
        else controller.resume(transform, transformTime, nowTicks);
        transformTime = nowTicks;
    }

    void putComponent(SpaceComponent c) { components.put(c.id(), c); }

    boolean removeComponent(Class<? extends SpaceComponent> type) {
        return components.values().removeIf(type::isInstance);
    }

    /**
     * Aligne scale, composants, contrôleur (et transform si {@code src} n'a pas de contrôleur : sinon il est recalculé) sur {@code src}.
     * Renvoie vrai si quelque chose a changé.
     */
    boolean assign(SpaceObject src) {
        CompoundTag a = save(), b = src.save();
        for (CompoundTag t : new CompoundTag[] {a, b}) t.remove("transformTime");
        if (controller != null || src.controller != null) {
            a.remove("transform");
            b.remove("transform");
        }
        if (a.equals(b)) return false;
        if (src.controller == null) transform.set(src.transform);
        scale = src.scale;
        components.clear();
        components.putAll(src.components);
        setController(src.controller);
        return true;
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
        if (controller != null) {
            t.put("controller", SpaceControllers.save(controller));
            if (!Double.isNaN(transformTime)) t.putDouble("transformTime", transformTime);
        }
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
        if (data.contains("controller")) {
            o.controller = SpaceControllers.load(data.getCompound("controller"));
            if (o.controller != null && data.contains("transformTime")) o.transformTime = data.getDouble("transformTime");
        }
        return o;
    }
}
