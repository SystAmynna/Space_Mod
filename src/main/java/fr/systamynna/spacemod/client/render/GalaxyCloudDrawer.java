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
        sh.safeGetUniform("ColorCore").set((float) g.core.r(), (float) g.core.g(), (float) g.core.b(), (float) g.core.a());
        sh.safeGetUniform("ColorEdge").set((float) g.edge.r(), (float) g.edge.g(), (float) g.edge.b(), (float) g.edge.a());
    }
}
