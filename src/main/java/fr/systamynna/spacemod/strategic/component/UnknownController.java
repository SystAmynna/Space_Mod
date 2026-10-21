package fr.systamynna.spacemod.strategic.component;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Contrôleur d'un type non enregistré (mod retiré, version plus récente) : réécrit tel quel, l'objet reste immobile.
 *
 * @param typeId identifiant du type d'origine
 * @param data données NBT d'origine
 */
public record UnknownController(ResourceLocation typeId, CompoundTag data) implements Controller {
    /** Copie des données d'origine, sans les interpréter. */
    @Override
    public CompoundTag save() {
        return data.copy();
    }

    /** Ne sait pas calculer de mouvement : le Transform stocké est renvoyé tel quel. */
    @Override
    public Transform resolve(Transform stored, double spaceTime) {
        return stored;
    }
}
