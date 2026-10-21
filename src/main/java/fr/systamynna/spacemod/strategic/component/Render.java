package fr.systamynna.spacemod.strategic.component;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Donnée de rendu côté ciel : modèle ou proxy et rayon apparent. Aucune logique de rendu ici.
 *
 * @param model modèle spatial ou proxy affiché dans le ciel
 * @param apparentRadius rayon apparent de l'objet, dans l'unité du parent ; sert au seuil de visibilité
 */
public record Render(ResourceLocation model, double apparentRadius) implements Component {
    /** Identifiant du type de ce composant dans le registre {@link ComponentTypes}. */
    @Override
    public ResourceLocation typeId() {
        return ComponentTypes.RENDER.id();
    }

    /** Écrit le composant en NBT ; relu à l'identique par {@link #load}. */
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("model", model.toString());
        tag.putDouble("apparent_radius", apparentRadius);
        return tag;
    }

    /** Relit un composant écrit par {@link #save()}. */
    static Render load(CompoundTag tag) {
        return new Render(ResourceLocation.parse(tag.getString("model")), tag.getDouble("apparent_radius"));
    }
}
