package fr.systamynna.spacemod.client.render;

import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import fr.systamynna.spacemod.strategic.component.Render;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.nbt.CompoundTag;

/**
 * Nuage de la galaxie de l'observateur (la bande de la Voie lactée), à poser sur la galaxie elle-même : sa direction est
 * celle du centre galactique, son plan celui de la galaxie (plan XZ du cadre). Paramètres : {@code color} ({@code #rrggbb}),
 * {@code brightness} (1), {@code thickness} (demi-épaisseur de la bande en rad, 0.3), {@code bulge} (surbrillance vers le
 * centre, 3) et {@code seed} (motif des poussières).
 * shortcut: suppose l'observateur dans la galaxie ; le nuage s'estompe seulement avec le rayon angulaire de la galaxie.
 */
final class GalacticCloudRenderObject extends RenderObject {
    private static final int SEGMENTS = 192;
    private static final int RINGS = 12;

    private final float[] color;
    private final float brightness;
    private final float thickness;
    private final float bulge;
    private final float seedA;
    private final float seedB;

    GalacticCloudRenderObject(Render.Layer layer) {
        CompoundTag p = layer.params();
        color = Billboard.color(p.getString("color"));
        brightness = p.contains("brightness") ? (float) p.getDouble("brightness") : 1f;
        thickness = p.contains("thickness") ? (float) p.getDouble("thickness") : 0.3f;
        bulge = p.contains("bulge") ? (float) p.getDouble("bulge") : 3f;
        long seed = (long) p.getDouble("seed");
        seedA = (seed * 0.618f) % 6.28f;
        seedB = (seed * 0.414f) % 6.28f;
    }

    @Override
    public void render(RenderContext ctx) {
        Vector3f[] basis = Galactic.basis(ctx);
        Vector3f e1 = basis[0], e2 = basis[1], n = basis[2];
        float fade = Math.min(1f, ctx.angularRadius() / (float) (Math.PI / 4));

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float range = thickness * 3f;
        for (int i = 0; i < SEGMENTS; i++) {
            for (int j = 0; j < RINGS; j++) {
                vertex(buffer, ctx, e1, e2, n, i, j, range, fade);
                vertex(buffer, ctx, e1, e2, n, i + 1, j, range, fade);
                vertex(buffer, ctx, e1, e2, n, i + 1, j + 1, range, fade);
                vertex(buffer, ctx, e1, e2, n, i, j + 1, range, fade);
            }
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
    }

    private void vertex(BufferBuilder buffer, RenderContext ctx, Vector3f e1, Vector3f e2, Vector3f n, int i, int j,
            float range, float fade) {
        float phi = (float) (2 * Math.PI * i / SEGMENTS);
        float lat = -range + 2 * range * j / RINGS;
        float cl = (float) Math.cos(lat);
        float x = cl * (float) Math.cos(phi);
        float y = cl * (float) Math.sin(phi);
        float z = (float) Math.sin(lat);
        float angToCenter = (float) Math.acos(Math.max(-1f, Math.min(1f, x)));
        float t = lat / thickness;
        float density = (float) Math.exp(-t * t);
        // poussières : modulation lente le long de la bande, déterministe pour la graine
        density *= 0.7f + 0.3f * (float) (Math.sin(5 * phi + seedA) * Math.cos(11 * phi + seedB + lat * 4));
        float center = 1f + bulge * (float) Math.exp(-(angToCenter * angToCenter) / 0.36f);
        float a = Math.min(1f, 0.12f * density * center * brightness * fade);
        float d = RenderContext.SKY_DISTANCE;
        buffer.addVertex(ctx.view(), d * (x * e1.x + y * e2.x + z * n.x), d * (x * e1.y + y * e2.y + z * n.y),
                d * (x * e1.z + y * e2.z + z * n.z)).setColor(color[0], color[1], color[2], a);
    }
}
