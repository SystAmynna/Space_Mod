package fr.systamynna.spacemod.client.render;

import java.util.Arrays;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import fr.systamynna.spacemod.strategic.component.Render;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Sphère texturée (planète, lune, étoile). Paramètres : {@code texture} (équirectangulaire), {@code scale} (1 par
 * défaut ; plus de 1 pour une couche d'atmosphère empilée) et {@code shade} (faux pour une étoile : pas d'ombre), {@code color} ({@code #rrggbb}, teinte multipliée à la texture ; blanc par défaut).
 * Valeurs de LOD reprises de hololib.
 */
final class SphereRenderObject extends RenderObject {
    private static final int[] SECTORS = { 24, 48, 96, 192 };
    private static final int[] RINGS = { 6, 12, 24, 48 };
    /** Taille à l'écran au-dessus de laquelle on monte d'un niveau. */
    private static final float[] LOD_UP = { 0.1f, 0.4f, 1.4f };
    private static final float AMBIENT = 0.06f;
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final SphereMesh[] MESHES = new SphereMesh[SECTORS.length];

    private final ResourceLocation texture;
    private final float scale;
    private final boolean shade;
    private final float[] tint;
    private final Matrix4f model = new Matrix4f();
    private final Vector3f lightLocal = new Vector3f();
    private float[] colors = new float[0];

    SphereRenderObject(Render.Layer layer) {
        CompoundTag p = layer.params();
        texture = p.contains("texture") ? ResourceLocation.parse(p.getString("texture")) : WHITE;
        scale = p.contains("scale") ? (float) p.getDouble("scale") : 1f;
        shade = !p.contains("shade") || p.getBoolean("shade");
        tint = Billboard.color(p.getString("color"));
    }

    @Override
    public void render(RenderContext ctx) {
        float radius = RenderContext.SKY_DISTANCE * (float) Math.sin(ctx.angularRadius()) * scale;
        Vector3f d = ctx.direction();
        model.set(ctx.view()).translate(d.x * RenderContext.SKY_DISTANCE, d.y * RenderContext.SKY_DISTANCE,
                d.z * RenderContext.SKY_DISTANCE);
        int level = lod(ctx.screenSize(), LOD_UP);
        SphereMesh mesh = mesh(level);
        model.rotate(ctx.bodyRotation()).scale(radius);
        shadeVertices(mesh, ctx);

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        bind(texture);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int j = 0; j < mesh.rings; j++) {
            for (int i = 0; i < mesh.sectors; i++) {
                vertex(buffer, mesh, mesh.index(i, j));
                vertex(buffer, mesh, mesh.index(i + 1, j));
                vertex(buffer, mesh, mesh.index(i + 1, j + 1));
                vertex(buffer, mesh, mesh.index(i, j + 1));
            }
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /** Éclairage de Lambert par sommet, calculé dans le repère du corps (la lumière y est ramenée une fois). */
    private void shadeVertices(SphereMesh mesh, RenderContext ctx) {
        int n = mesh.vertexCount();
        if (colors.length < n) colors = new float[n];
        if (!shade || ctx.light() == null) {
            Arrays.fill(colors, 0, n, 1f);
            return;
        }
        ctx.bodyRotation().conjugate(new Quaternionf()).transform(ctx.light(), lightLocal);
        for (int k = 0; k < n; k++) {
            float lambert = mesh.x[k] * lightLocal.x + mesh.y[k] * lightLocal.y + mesh.z[k] * lightLocal.z;
            colors[k] = Math.max(AMBIENT, lambert);
        }
    }

    private void vertex(BufferBuilder buffer, SphereMesh mesh, int k) {
        float c = colors[k];
        buffer.addVertex(model, mesh.x[k], mesh.y[k], mesh.z[k]).setUv(mesh.u[k], mesh.v[k]).setColor(c * tint[0], c * tint[1], c * tint[2], 1f);
    }

    private static SphereMesh mesh(int level) {
        if (MESHES[level] == null) MESHES[level] = SphereMesh.build(SECTORS[level], RINGS[level]);
        return MESHES[level];
    }

    /** Lie la texture avec un filtre linéaire (sans mipmaps : les PNG n'en ont pas). */
    private static void bind(ResourceLocation location) {
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(location);
        tex.setFilter(true, false);
        RenderSystem.setShaderTexture(0, location);
    }
}
