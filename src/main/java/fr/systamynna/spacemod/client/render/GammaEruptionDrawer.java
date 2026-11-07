package fr.systamynna.spacemod.client.render;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.core.render.GammaEruptionRender;
import fr.systamynna.spacemod.core.render.SpaceRenderObject;
import net.minecraft.client.renderer.ShaderInstance;

/** Deux jets polaires incandescents (voir {@code shaders/core/gamma_eruption.fsh}). */
final class GammaEruptionDrawer extends EffectDrawer {
    @Override
    @Nullable
    ShaderInstance shader() { return ClientShaders.gammaEruption; }

    @Override
    void uniforms(ShaderInstance sh, SpaceRenderObject ro) {
        GammaEruptionRender g = (GammaEruptionRender) ro;
        color(sh, "ColorA", g.colorA);
        color(sh, "ColorB", g.colorB);
        sh.safeGetUniform("Params").set((float) g.width, (float) g.speed, (float) Math.floorMod(g.seed, 65536));
    }
}
