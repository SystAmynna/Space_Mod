package fr.systamynna.spacemod.strategic.component;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Volume logique : zone de rayon R autour d'un objet qui a une dimension ; c'est aussi la taille de cette dimension.
 * Pas forcément une sphère à terme (un Halo praticable ne l'est pas). Distinct de l'{@link ExclusionVolume}.
 *
 * @param radius rayon R, dans l'unité du référentiel de l'objet
 */
public record LogicalVolume(double radius) implements Component {
    /** Identifiant du type de ce composant dans le registre {@link ComponentTypes}. */
    @Override
    public ResourceLocation typeId() {
        return ComponentTypes.LOGICAL_VOLUME.id();
    }

    // TODO : Devra contenir une shape (exploitable de manière abstraite via Sable -> Rapier pour tester les collisions)

    /** Écrit le composant en NBT ; relu à l'identique par {@link #load}. */
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("radius", radius);
        return tag;
    }

    /** Relit un composant écrit par {@link #save()}. */
    static LogicalVolume load(CompoundTag tag) {
        return new LogicalVolume(tag.getDouble("radius"));
    }
}
