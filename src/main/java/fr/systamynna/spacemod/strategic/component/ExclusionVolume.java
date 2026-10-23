package fr.systamynna.spacemod.strategic.component;

import fr.systamynna.spacemod.strategic.component.hostedPropriety.LogicalVolume;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Volume d'exclusion : agit comme une collision, un vaisseau n'y entre pas librement. Indépendant de toute dimension :
 * une étoile ou un trou noir en a un sans avoir de {@link LogicalVolume}. Les contacts sont résolus par
 * {@code ExclusionResolver}, derrière lequel un moteur physique (Sable / Rapier) pourra se brancher.
 *
 * @param shape forme du volume, centrée sur l'objet
 * @param scope à qui s'applique le volume
 */
public record ExclusionVolume(ExclusionShape shape, Scope scope) implements Component {
    /** À qui s'applique un volume d'exclusion. Les navettes ({@code Archetypes.SHUTTLE}) ignorent {@link #LARGE_ONLY}. */
    public enum Scope {
        /** Tous les vaisseaux, navettes comprises. */
        ALL,
        /** Les grands vaisseaux seulement. */
        LARGE_ONLY
    }

    /** Rapport par défaut entre exclusion et volume logique : 2R pour une sphère. */
    public static final double DEFAULT_FACTOR = 2.0;

    /** Sphère de ce rayon, qui repousse les grands vaisseaux seulement. */
    public ExclusionVolume(double radius) {
        this(new ExclusionShape.Sphere(radius), Scope.LARGE_ONLY);
    }

    /**
     * Exclusion d'un objet hébergeant une dimension : le volume logique élargi (2R pour une sphère).
     *
     * @param factor rapport entre le rayon d'exclusion et le rayon logique
     */
    public static ExclusionVolume scaled(LogicalVolume logical, double factor) {
        return new ExclusionVolume(logical.radius() * factor);
    }

    /** Rayon de la sphère englobante, dans l'unité du référentiel de l'objet. */
    public double radius() {
        return shape.boundingRadius();
    }

    /** Identifiant du type de ce composant dans le registre {@link ComponentTypes}. */
    @Override
    public ResourceLocation typeId() {
        return ComponentTypes.EXCLUSION_VOLUME.id();
    }

    /** Écrit le composant en NBT ; relu à l'identique par {@link #load}. La clé {@code radius} reste celle de l'ancien format. */
    @Override
    public CompoundTag save() {
        CompoundTag tag = shape.save();
        tag.putString("scope", scope.name());
        return tag;
    }

    /** Relit un composant écrit par {@link #save()} ; l'ancien format {@code {radius}} donne une sphère {@link Scope#LARGE_ONLY}. */
    static ExclusionVolume load(CompoundTag tag) {
        Scope scope = tag.contains("scope") ? Scope.valueOf(tag.getString("scope")) : Scope.LARGE_ONLY;
        return new ExclusionVolume(ExclusionShape.load(tag), scope);
    }
}
