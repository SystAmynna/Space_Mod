package fr.systamynna.spacemod.client.render;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.core.render.SpaceRenderObject;
import fr.systamynna.spacemod.core.render.StarRender;
import net.minecraft.client.renderer.ShaderInstance;

/** Étoile : boule éblouissante de loin, plasma émissif de près (voir {@code shaders/core/star.fsh}). */
final class StarDrawer extends EffectDrawer {
    @Override
    @Nullable
    ShaderInstance shader() { return ClientShaders.star; }

    @Override
    void uniforms(ShaderInstance sh, SpaceRenderObject ro) {
        StarRender s = (StarRender) ro;
        color(sh, "ColorA", s.hot);
        color(sh, "ColorB", s.cool);
        sh.safeGetUniform("Params").set((float) s.speed, (float) s.granulation, (float) s.emissive);
    }
}
