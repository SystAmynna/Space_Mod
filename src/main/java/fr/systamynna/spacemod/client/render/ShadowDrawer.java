package fr.systamynna.spacemod.client.render;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import fr.systamynna.spacemod.core.render.ShadowRender;
import fr.systamynna.spacemod.core.render.SpaceRenderObject;
import net.minecraft.client.renderer.ShaderInstance;

/** Ombrage d'une sphère : multiplie l'image déjà dessinée (la planète) par l'éclairement des étoiles (voir {@code shaders/core/shadow.fsh}). */
final class ShadowDrawer extends EffectDrawer {
    @Override
    @Nullable
    ShaderInstance shader() { return ClientShaders.shadow; }

    /** Après la planète (0), avant son atmosphère (2) et ses anneaux (3). */
    @Override
    public int priority() { return 1; }

    @Override
    void blend() { RenderSystem.blendFunc(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.SRC_COLOR); }

    @Override
    void uniforms(ShaderInstance sh, SpaceRenderObject ro) {
        ShadowRender s = (ShadowRender) ro;
        sh.safeGetUniform("Params").set((float) s.ambient, (float) s.softness, 0f, 0f);
    }
}
