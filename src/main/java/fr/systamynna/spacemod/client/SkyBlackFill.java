package fr.systamynna.spacemod.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;

import net.minecraft.client.renderer.GameRenderer;

/**
 * Fond noir parfait : un quad plein écran en projection identité, tracé à la place du ciel vanilla. Même principe que
 * le {@code SkyCancelFill} de hololib, porté en 1.21.1.
 */
final class SkyBlackFill {
    private static final Matrix4f IDENTITY = new Matrix4f();

    /** Classe utilitaire. */
    private SkyBlackFill() {}

    static void render() {
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(IDENTITY, VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.getModelViewStack().pushMatrix().identity();
        RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515); // GL_LEQUAL
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            buffer.addVertex(-1f, -1f, 1f).setColor(0f, 0f, 0f, 1f);
            buffer.addVertex(1f, -1f, 1f).setColor(0f, 0f, 0f, 1f);
            buffer.addVertex(1f, 1f, 1f).setColor(0f, 0f, 0f, 1f);
            buffer.addVertex(-1f, 1f, 1f).setColor(0f, 0f, 0f, 1f);
            MeshData mesh = buffer.buildOrThrow();
            BufferUploader.drawWithShader(mesh);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }
}
