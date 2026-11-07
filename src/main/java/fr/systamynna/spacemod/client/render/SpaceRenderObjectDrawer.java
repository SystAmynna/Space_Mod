package fr.systamynna.spacemod.client.render;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.BufferBuilder;

import fr.systamynna.spacemod.core.render.RenderMath.Placed;
import fr.systamynna.spacemod.core.render.SpaceRenderObject;
import net.minecraft.resources.ResourceLocation;

/**
 * Dessin client d'un type de {@link SpaceRenderObject} ; les éléments de même texture consécutifs partagent un seul appel GPU.
 * Un type {@link #custom()} (shader propre) se dessine lui-même, avant les étoiles majeures et les éléments texturés (couche des galaxies).
 */
interface SpaceRenderObjectDrawer {
    /** Texture, ou {@code null} si pas encore disponible (l'élément est ignoré cette image). */
    @Nullable
    ResourceLocation texture(SpaceRenderObject ro);

    /** Ajoute les triangles (POSITION_TEX_COLOR) ; {@code k} ramène l'élément sur la sphère du ciel, {@code modelView} est la rotation de la caméra. */
    void emit(BufferBuilder out, Matrix4f modelView, Placed p, double k, SpaceRenderObject ro);

    /** Vrai si le type se dessine lui-même par {@link #draw} (pas de texture ni de lot). */
    default boolean custom() { return false; }

    /** Vrai pour un effet du système local (étoile, anneau, trou noir…) : dessiné par {@link #draw} dans l'ordre loin→près, avec les éléments texturés. */
    default boolean overlay() { return false; }

    /** Ordre à distance égale (même porteur) : plus petit d'abord. */
    default int priority() { return 0; }

    /** Dessin d'un type {@link #custom()} ; {@code carrier} = UUID du SpaceObject porteur (sert de graine) ; {@code flare} = intensité du flare [0, 1] d'une étoile (0 hors de son système stellaire ; voir {@code RenderMath.stellarSystemOf} et {@code RenderMath.flare}). */
    default void draw(Matrix4f modelView, Placed p, UUID carrier, SpaceRenderObject ro, float flare) {}
}
