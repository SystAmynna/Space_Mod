package fr.systamynna.spacemod.strategic.component.hostedPropriety;

import net.minecraft.nbt.CompoundTag;

/**
 * Volume de déclenchement d'un {@link PortPoint} : y entrer ordonne le chargement de la dimension, avant que le
 * {@link LogicalVolume} ne soit atteint.
 *
 * @param radius rayon, dans l'unité du référentiel de l'objet
 */
public record TriggerVolume(double radius) {
    /** Écrit le volume en NBT ; relu à l'identique par {@link #load}. */
    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("radius", radius);
        return tag;
    }

    /** Relit un volume écrit par {@link #save()}. */
    static TriggerVolume load(CompoundTag tag) {
        return new TriggerVolume(tag.getDouble("radius"));
    }
}
