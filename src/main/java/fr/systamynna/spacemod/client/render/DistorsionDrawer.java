package fr.systamynna.spacemod.client.render;

import javax.annotation.Nullable;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import fr.systamynna.spacemod.core.render.DistorsionRender;
import fr.systamynna.spacemod.core.render.SpaceRenderObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * Lentille gravitationnelle en espace écran : copie l'image déjà dessinée (tout ce qui est plus loin) puis la redessine déformée.
 * shortcut: une copie plein écran par lentille visible et bords d'écran en clamp ; suffisant pour une ou deux lentilles, à revoir au-delà.
 */
final class DistorsionDrawer extends EffectDrawer {
    @Nullable
    private static TextureTarget copy;

    @Override
    @Nullable
    ShaderInstance shader() { return ClientShaders.distorsion; }

    @Override
    public int priority() { return 1; }

    @Override
    void uniforms(ShaderInstance sh, SpaceRenderObject ro) {
        DistorsionRender d = (DistorsionRender) ro;
        sh.safeGetUniform("Params").set((float) d.strength, (float) d.extent);
    }

    @Override
    void beforeDraw(ShaderInstance sh) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int w = main.width, h = main.height;
        if (copy == null) {
            copy = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        } else if (copy.width != w || copy.height != h) copy.resize(w, h, Minecraft.ON_OSX);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(true);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        sh.safeGetUniform("ScreenSize").set((float) w, (float) h);
    }
}
