package fr.systamynna.spacemod.strategic.component;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * L'objet peut être détruit. Absent = indestructible. Un objet détruit reste en place, marqué, et sa dimension reste
 * attribuée ; un opérateur peut le restaurer.
 *
 * @param destroyed {@code true} si l'objet est actuellement détruit
 */
public record Destroyable(boolean destroyed) implements Component {
    /** Composant d'un objet destructible encore intact. */
    public static Destroyable alive() {
        return new Destroyable(false);
    }

    /** Identifiant du type de ce composant dans le registre {@link ComponentTypes}. */
    @Override
    public ResourceLocation typeId() {
        return ComponentTypes.DESTROYABLE.id();
    }

    /** Écrit le composant en NBT ; relu à l'identique par {@link #load}. */
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("destroyed", destroyed);
        return tag;
    }

    /** Relit un composant écrit par {@link #save()}. */
    static Destroyable load(CompoundTag tag) {
        return new Destroyable(tag.getBoolean("destroyed"));
    }
}
