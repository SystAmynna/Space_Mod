package fr.systamynna.spacemod.strategic.component;

import net.minecraft.nbt.CompoundTag;

/**
 * Forme d'un {@link ExclusionVolume}. Seule la sphère existe pour le moment ; toute autre forme est ramenée à sa sphère
 * englobante par la résolution des contacts, jusqu'à ce qu'un moteur de collision (Sable / Rapier) la gère.
 */
public interface ExclusionShape {
    /** Rayon de la plus petite sphère centrée sur l'objet qui contient la forme, dans l'unité du référentiel de l'objet. */
    double boundingRadius();

    /** Écrit la forme en NBT (clé {@code shape} = type) ; relue par {@link #load}. */
    CompoundTag save();

    /** Sphère centrée sur l'objet. */
    record Sphere(double radius) implements ExclusionShape {
        @Override
        public double boundingRadius() {
            return radius;
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("shape", "sphere");
            tag.putDouble("radius", radius);
            return tag;
        }
    }

    /** Relit une forme écrite par {@link #save()} ; un NBT sans {@code shape} (ancien format {@code {radius}}) donne une sphère. */
    static ExclusionShape load(CompoundTag tag) {
        return new Sphere(tag.getDouble("radius"));
    }
}
