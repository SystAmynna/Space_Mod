package fr.systamynna.spacemod.client.render;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.core.render.GalaxyCloudRender;
import fr.systamynna.spacemod.core.render.SpaceRenderObject;
import net.minecraft.client.renderer.ShaderInstance;

/** Nuage d'une galaxie : ray-marching dans le volume (voir {@code shaders/core/galaxy_cloud.fsh}). */
final class GalaxyCloudDrawer extends GalaxyShaderDrawer {
    @Override
    @Nullable
    ShaderInstance shader() { return ClientShaders.galaxyCloud; }

    @Override
    void uniforms(ShaderInstance sh, SpaceRenderObject ro) {
        GalaxyCloudRender g = (GalaxyCloudRender) ro;
        sh.safeGetUniform("Params").set((float) g.thickness, (float) g.arms);
    }
}
