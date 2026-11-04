package fr.systamynna.spacemod.core.render;

import java.util.List;

import net.minecraft.nbt.CompoundTag;

/**
 * Élément de rendu générique, intégré au mod : seules ses données sont répliquées (le dessin est côté client, {@code SpaceRenderObjectDrawers}).
 * Les éléments variables (textures) sont des noms d'assets possédés par le serveur.
 */
public interface SpaceRenderObject {
    String type();

    RenderTransform transform();

    /** Rayon propre en km (avant {@link RenderTransform#scale}) ; sert à l'angle apparent. */
    double radius();

    /** Noms des assets (textures) utilisés. */
    List<String> assets();

    /** Doit contenir la clé {@code type}. */
    CompoundTag save();

    String describe();
}
