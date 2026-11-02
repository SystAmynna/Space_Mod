package fr.systamynna.spacemod.core;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;

/** Objet spatial : identifié par un UUID, rattaché à un parent (null pour la racine). */
public class SpaceObject {
    private final UUID uuid;
    @Nullable
    private final UUID parent;

    public SpaceObject(UUID uuid, @Nullable UUID parent) {
        this.uuid = uuid;
        this.parent = parent;
    }

    public UUID getUuid() { return uuid; }

    @Nullable
    public UUID getParent() { return parent; }

    /** Payload persisté (vide pour l'instant ; les futurs champs s'ajoutent ici). */
    public CompoundTag save() { return new CompoundTag(); }

    public static SpaceObject load(UUID uuid, @Nullable UUID parent, CompoundTag data) {
        return new SpaceObject(uuid, parent);
    }
}
