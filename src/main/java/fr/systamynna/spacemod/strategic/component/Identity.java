package fr.systamynna.spacemod.strategic.component;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Nom, identifiant texte et archétype (voir {@code Archetypes}) d'un objet.
 *
 * @param name nom de l'objet, libre et pas forcément unique (par exemple {@code earth})
 * @param id identifiant texte {@code <parent>/<parent>/.../<nom>} (par exemple {@code milky_way/sol/earth/moon}), vide
 *        tant qu'il n'est pas calculé ; unique dans la couche
 * @param archetype identifiant d'archétype d'origine (par exemple {@code planet}) ; informatif, le comportement vient des composants
 */
public record Identity(String name, String id, String archetype) implements Component {
    /** Identité sans identifiant texte encore calculé. */
    public Identity(String name, String archetype) {
        this(name, "", archetype);
    }

    /** Identité d'un enfant de {@code parent} ({@code null} : une galaxie) : son id est celui du parent suivi de {@code slug}. */
    public static Identity child(@Nullable Identity parent, String slug, String name, String archetype) {
        return new Identity(name, parent == null || parent.id.isEmpty() ? slug : parent.id + "/" + slug, archetype);
    }

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
        tag.putString("id", id);
        tag.putString("archetype", archetype);
        return tag;
    }

    /** Relit un composant écrit par {@link #save()} ; un ancien composant sans id prend un id vide. */
    public static Identity load(CompoundTag tag) {
        return new Identity(tag.getString("name"), tag.getString("id"), tag.getString("archetype"));
    }
}
