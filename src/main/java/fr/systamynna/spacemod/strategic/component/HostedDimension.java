package fr.systamynna.spacemod.strategic.component;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * L'objet possède une dimension persistante. Seule la clé est stockée ; la taille (R) vit dans {@link LogicalVolume}.
 * La création et la restauration de la dimension relèvent de la bibliothèque de dimensions, pas d'ici.
 *
 * @param dimension clé de la dimension hébergée ; unique dans la couche (une dimension n'a qu'un hôte)
 */
public record HostedDimension(ResourceLocation dimension) implements Component {
    /** Identifiant du type de ce composant dans le registre {@link ComponentTypes}. */
    @Override
    public ResourceLocation typeId() {
        return ComponentTypes.HOSTED_DIMENSION.id();
    }

    /** Écrit le composant en NBT ; relu à l'identique par {@link #load}. */
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", dimension.toString());
        return tag;
    }

    /** Relit un composant écrit par {@link #save()}. */
    static HostedDimension load(CompoundTag tag) {
        return new HostedDimension(ResourceLocation.parse(tag.getString("dimension")));
    }
}
