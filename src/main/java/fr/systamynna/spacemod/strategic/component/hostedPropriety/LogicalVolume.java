package fr.systamynna.spacemod.strategic.component.hostedPropriety;

import fr.systamynna.spacemod.strategic.component.ExclusionVolume;
import net.minecraft.nbt.CompoundTag;

/**
 * Volume logique d'un {@link PortPoint} : y entrer déclenche le transport vers la dimension. Distinct de
 * l'{@link ExclusionVolume}.
 *
 * @param radius rayon R, dans l'unité du référentiel de l'objet
 */
public record LogicalVolume(double radius) {
    // TODO : Devra contenir une shape (exploitable de manière abstraite via Sable -> Rapier pour tester les collisions)

    /** Écrit le volume en NBT ; relu à l'identique par {@link #load}. */
    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("radius", radius);
        return tag;
    }

    /** Relit un volume écrit par {@link #save()}. */
    static LogicalVolume load(CompoundTag tag) {
        return new LogicalVolume(tag.getDouble("radius"));
    }
}
