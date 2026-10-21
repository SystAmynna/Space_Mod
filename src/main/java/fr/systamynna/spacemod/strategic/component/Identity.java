package fr.systamynna.spacemod.strategic.component;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Nom d'affichage et archétype (voir {@code Archetypes}) d'un objet.
 *
 * @param name nom d'affichage, libre
 * @param archetype identifiant d'archétype d'origine (par exemple {@code planet}) ; informatif, le comportement vient des composants
 */
public record Identity(String name, String archetype) implements Component {
    /** Identifiant du type de ce composant dans le registre {@link ComponentTypes}. */
    @Override
    public ResourceLocation typeId() {
        return ComponentTypes.IDENTITY.id();
    }

    /** Écrit le composant en NBT ; relu à l'identique par {@link #load}. */
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("archetype", archetype);
        return tag;
    }

    /** Relit un composant écrit par {@link #save()}. */
    static Identity load(CompoundTag tag) {
        return new Identity(tag.getString("name"), tag.getString("archetype"));
    }
}
