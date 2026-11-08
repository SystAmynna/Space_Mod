package fr.systamynna.spacemod.client.render;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.core.render.AtmosphereRender;
import fr.systamynna.spacemod.core.render.SpaceRenderObject;
import net.minecraft.client.renderer.ShaderInstance;

/** Coquille d'atmosphère éclairée par les étoiles, unité du shader = rayon extérieur (voir {@code shaders/core/atmosphere.fsh}). */
final class AtmosphereDrawer extends EffectDrawer {
    @Override
    @Nullable
    ShaderInstance shader() { return ClientShaders.atmosphere; }

    /** Après la planète (0) et son ombre (1), avant ses anneaux (3). */
    @Override
    public int priority() { return 2; }

    @Override
    void uniforms(ShaderInstance sh, SpaceRenderObject ro) {
        AtmosphereRender a = (AtmosphereRender) ro;
        color(sh, "ColorA", a.day);
        color(sh, "ColorB", a.sunset);
        sh.safeGetUniform("Params").set((float) (a.inner / a.radius()), (float) a.density, 0f, 0f);
    }
}
