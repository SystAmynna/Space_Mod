package fr.systamynna.spacemod.client.render;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.core.render.GalaxyStarsRender;
import fr.systamynna.spacemod.core.render.SpaceRenderObject;
import net.minecraft.client.renderer.ShaderInstance;

/** Étoiles aléatoires d'une galaxie, sans ray-marching (voir {@code shaders/core/galaxy_stars.fsh}). */
final class GalaxyStarsDrawer extends SkyShaderDrawer {
    @Override
    @Nullable
    ShaderInstance shader() { return ClientShaders.galaxyStars; }

    @Override
    void uniforms(ShaderInstance sh, SpaceRenderObject ro) {
        GalaxyStarsRender g = (GalaxyStarsRender) ro;
        sh.safeGetUniform("Params").set((float) g.thickness, (float) g.arms);
        sh.safeGetUniform("Stars").set((float) g.density);
    }
}
