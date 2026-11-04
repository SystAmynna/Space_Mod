package fr.systamynna.spacemod.client.render;

import javax.annotation.Nullable;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.BufferBuilder;

import fr.systamynna.spacemod.core.render.RenderMath.Placed;
import fr.systamynna.spacemod.core.render.SpaceRenderObject;
import net.minecraft.resources.ResourceLocation;

/** Dessin client d'un type de {@link SpaceRenderObject} ; les éléments de même texture consécutifs partagent un seul appel GPU. */
interface SpaceRenderObjectDrawer {
    /** Texture, ou {@code null} si pas encore disponible (l'élément est ignoré cette image). */
    @Nullable
    ResourceLocation texture(SpaceRenderObject ro);

    /** Ajoute les triangles (POSITION_TEX_COLOR) ; {@code k} ramène l'élément sur la sphère du ciel, {@code modelView} est la rotation de la caméra. */
    void emit(BufferBuilder out, Matrix4f modelView, Placed p, double k, SpaceRenderObject ro);
}
