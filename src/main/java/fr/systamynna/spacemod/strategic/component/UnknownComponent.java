package fr.systamynna.spacemod.strategic.component;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Composant d'un type non enregistré (mod retiré, version plus récente) : réécrit tel quel.
 *
 * @param typeId identifiant du type d'origine
 * @param data données NBT d'origine
 */
public record UnknownComponent(ResourceLocation typeId, CompoundTag data) implements Component {
    /** Renvoie une copie des données d'origine, sans les interpréter, pour que la réécriture ne perde rien. */
    @Override
    public CompoundTag save() {
        return data.copy();
    }
}
