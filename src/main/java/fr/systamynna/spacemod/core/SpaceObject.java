package fr.systamynna.spacemod.core;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;

/** Objet spatial : UUID, nom (unique parmi ses frères) et parent (null pour la racine). */
public class SpaceObject {
    /** Nom de la racine. */
    public static final String ROOT_NAME = "universe";

    private final UUID uuid;
    private final String name;
    @Nullable
    private UUID parent;

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

    /** Payload persisté (les futurs champs s'ajoutent ici). */
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        return t;
    }

    public static SpaceObject load(UUID uuid, @Nullable UUID parent, CompoundTag data) {
        // Sans nom, c'est la racine créée avant l'introduction des noms.
        return new SpaceObject(uuid, parent, data.contains("name") ? data.getString("name") : ROOT_NAME);
    }
}
