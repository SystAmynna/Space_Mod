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

    /** Après la planète, son ombre (1) et son atmosphère (2) : l'avant de l'anneau les recouvre (la partie arrière est cachée par l'occulteur). */
    @Override
    public int priority() { return 3; }

    @Override
    void uniforms(ShaderInstance sh, SpaceRenderObject ro) {
        RingRender r = (RingRender) ro;
        color(sh, "ColorA", r.colorInner);
        color(sh, "ColorB", r.colorOuter);
        sh.safeGetUniform("Params").set((float) (r.inner / r.radius()), (float) r.opacity, r.emissive ? 1f : 0f, (float) (r.occluder / r.radius()));
        sh.safeGetUniform("Thickness").set((float) (r.thickness / r.radius())); // la passe arrive par l'uniforme Flare (voir SpaceRenderer)
    }
}
