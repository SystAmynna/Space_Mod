package fr.systamynna.spacemod.client.render;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.core.render.RingRender;
import fr.systamynna.spacemod.core.render.SpaceRenderObject;
import net.minecraft.client.renderer.ShaderInstance;

/** Anneau plat, unité du shader = rayon extérieur (voir {@code shaders/core/ring.fsh}). */
final class RingDrawer extends EffectDrawer {
    @Override
    @Nullable
    ShaderInstance shader() { return ClientShaders.ring; }

    @Override
    void uniforms(ShaderInstance sh, SpaceRenderObject ro) {
        RingRender r = (RingRender) ro;
        color(sh, "ColorA", r.colorInner);
        color(sh, "ColorB", r.colorOuter);
        sh.safeGetUniform("Params").set((float) (r.inner / r.radius()), (float) r.opacity, r.emissive ? 1f : 0f, (float) (r.occluder / r.radius()));
    }
}
