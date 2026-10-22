package fr.systamynna.spacemod.strategic.component;

import fr.systamynna.spacemod.strategic.component.hostedPropriety.LogicalVolume;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Volume d'exclusion : agit comme une collision, un objet massif (croiseur) n'y entre pas librement. Indépendant de
 * toute dimension : une étoile ou un trou noir en a un sans avoir de {@link LogicalVolume}.
 *
 * @param radius rayon d'exclusion, dans l'unité du référentiel de l'objet
 */
public record ExclusionVolume(double radius) implements Component {
    /** Rapport par défaut entre exclusion et volume logique : 2R pour une sphère. */
    public static final double DEFAULT_FACTOR = 2.0;

    // TODO : Devra contenir une shape (sphère par défaut)

    /**
     * Exclusion d'un objet hébergeant une dimension : le volume logique élargi (2R pour une sphère).
     *
     * @param factor rapport entre le rayon d'exclusion et le rayon logique
     */
    public static ExclusionVolume scaled(LogicalVolume logical, double factor) {
        return new ExclusionVolume(logical.radius() * factor);
    }

    /** Identifiant du type de ce composant dans le registre {@link ComponentTypes}. */
    @Override
    public ResourceLocation typeId() {
        return ComponentTypes.EXCLUSION_VOLUME.id();
    }

    /** Écrit le composant en NBT ; relu à l'identique par {@link #load}. */
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("radius", radius);
        return tag;
    }

    /** Relit un composant écrit par {@link #save()}. */
    static ExclusionVolume load(CompoundTag tag) {
        return new ExclusionVolume(tag.getDouble("radius"));
    }
}
