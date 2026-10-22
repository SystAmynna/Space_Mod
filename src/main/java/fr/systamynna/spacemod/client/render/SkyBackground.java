package fr.systamynna.spacemod.client.render;

import java.util.Random;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * Motif lointain : étoiles et nébuleuses tirées d'une graine, la même pour tous les joueurs d'une dimension. Chaque client
 * le génère lui-même, une fois par graine. Les positions sont en coordonnées galactiques (x vers le centre, z normale du plan) :
 * la plupart des étoiles sont dans l'anneau galactique et se densifient vers le centre. Le repère galactique est posé à
 * l'affichage par la matrice, pas dans les sommets.
 */
final class SkyBackground {
    /** Nombre d'étoiles du motif. */
    static final int STARS = 14000;
    private static final int NEBULAE = 14;
    private static final int NEBULA_FAN = 20;
    /** Part des étoiles dans l'anneau ; le reste est réparti uniformément sur la sphère. */
    private static final float BAND_SHARE = 0.75f;
    /** Demi-épaisseur de l'anneau (rad), celle du nuage galactique de {@code sol.json}. shortcut: constante, pas lue de la galaxie. */
    private static final float THICKNESS = 0.28f;
    /** Gain de densité au centre galactique (rapport à la densité loin du centre). */
    private static final float CENTER_GAIN = 4f;
    private static final float CENTER_WIDTH = 0.36f;

    /** Étoiles et nébuleuses, 8 flottants chacune : x y z taille r g b a. */
    record Field(float[] stars, float[] nebulae) {}

    private static int cachedSeed;
    private static VertexBuffer starBuffer;
    private static VertexBuffer nebulaBuffer;

    /** Classe utilitaire. */
    private SkyBackground() {}

    /**
     * @param galactic base {@code {e1, e2, n}} de {@link Galactic#basis}, ou {@code null} sans galaxie : repère inertiel de l'hôte
     */
    static void render(int seed, Matrix4f view, @Nullable Vector3f[] galactic, Quaternionf inertial) {
        if (starBuffer == null || seed != cachedSeed) build(seed);
        Matrix4f model = new Matrix4f(view);
        if (galactic != null) model.mul(new Matrix4f().setColumn(0, new Vector4f(galactic[0], 0)).setColumn(1,
                new Vector4f(galactic[1], 0)).setColumn(2, new Vector4f(galactic[2], 0)));
        else model.rotate(inertial);
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        draw(nebulaBuffer, model, GameRenderer.getPositionColorShader());
        Uniform disc = SkyShaders.starGlow().getUniform("DiscRadius");
        if (disc != null) disc.set(0f);
        draw(starBuffer, model, SkyShaders.starGlow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
    }

    private static void draw(VertexBuffer buffer, Matrix4f model, ShaderInstance shader) {
        buffer.bind();
        buffer.drawWithShader(model, RenderSystem.getProjectionMatrix(), shader);
        VertexBuffer.unbind();
    }

    private static void build(int seed) {
        if (starBuffer != null) starBuffer.close();
        if (nebulaBuffer != null) nebulaBuffer.close();
        Field field = generate(seed);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < field.stars.length; i += 8) star(buf, field.stars, i);
        starBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        starBuffer.bind();
        starBuffer.upload(buf.buildOrThrow());
        buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < field.nebulae.length; i += 8) nebula(buf, field.nebulae, i);
        nebulaBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        nebulaBuffer.bind();
        nebulaBuffer.upload(buf.buildOrThrow());
        VertexBuffer.unbind();
        cachedSeed = seed;
    }

    /** Tire le motif de la graine, en coordonnées galactiques ; déterministe. */
    static Field generate(int seed) {
        Random rnd = new Random(seed);
        float[] stars = new float[STARS * 8];
        for (int i = 0; i < STARS; i++) {
            Vector3f d = direction(rnd);
            float mag = (float) Math.pow(rnd.nextFloat(), 3); // beaucoup de faibles, peu de brillantes
            float tint = rnd.nextFloat();
            put(stars, i * 8, d, 0.003f + 0.006f * mag, 0.75f + 0.25f * tint, 0.8f + 0.1f * (1 - tint), 1f - 0.25f * tint,
                    0.3f + 0.7f * mag);
        }
        float[] nebulae = new float[NEBULAE * 8];
        for (int i = 0; i < NEBULAE; i++) {
            Vector3f d = direction(rnd);
            put(nebulae, i * 8, d, 0.12f + 0.2f * rnd.nextFloat(), 0.3f + 0.5f * rnd.nextFloat(), 0.2f + 0.4f * rnd.nextFloat(),
                    0.4f + 0.5f * rnd.nextFloat(), 0.05f + 0.06f * rnd.nextFloat());
        }
        return new Field(stars, nebulae);
    }

    /** Direction galactique : dans l'anneau (plus dense et plus épais vers le centre) ou, pour le reste, uniforme. */
    private static Vector3f direction(Random rnd) {
        if (rnd.nextFloat() >= BAND_SHARE) {
            float z = 2 * rnd.nextFloat() - 1;
            float phi = (float) (2 * Math.PI * rnd.nextFloat());
            float s = (float) Math.sqrt(1 - z * z);
            return new Vector3f(s * (float) Math.cos(phi), s * (float) Math.sin(phi), z);
        }
        float lon;
        float center;
        do {
            lon = (float) (Math.PI * (2 * rnd.nextFloat() - 1));
            center = (float) Math.exp(-lon * lon / CENTER_WIDTH);
        } while (rnd.nextFloat() * (1 + CENTER_GAIN) > 1 + CENTER_GAIN * center);
        float lat = (float) rnd.nextGaussian() * THICKNESS * (1 + center);
        lat = Math.max(-1.5f, Math.min(1.5f, lat));
        float cl = (float) Math.cos(lat);
        return new Vector3f(cl * (float) Math.cos(lon), cl * (float) Math.sin(lon), (float) Math.sin(lat));
    }

    private static void put(float[] a, int o, Vector3f d, float size, float r, float g, float b, float alpha) {
        a[o] = d.x;
        a[o + 1] = d.y;
        a[o + 2] = d.z;
        a[o + 3] = size;
        a[o + 4] = r;
        a[o + 5] = g;
        a[o + 6] = b;
        a[o + 7] = alpha;
    }

    /** Base du plan tangent à la direction de l'entrée {@code o}, à l'échelle de {@code scale}. */
    private static Vector3f[] tangent(float[] a, int o, float scale) {
        Vector3f d = new Vector3f(a[o], a[o + 1], a[o + 2]);
        Vector3f u = (Math.abs(d.y) < 0.99f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).cross(d).normalize(scale);
        return new Vector3f[] { new Vector3f(d).mul(RenderContext.SKY_DISTANCE), u, new Vector3f(d).cross(u) };
    }

    /** Quad texturé (UV 0-1) pour le shader de halo. */
    private static void star(BufferBuilder buf, float[] a, int o) {
        Vector3f[] t = tangent(a, o, RenderContext.SKY_DISTANCE * a[o + 3]);
        Vector3f p = t[0], u = t[1], v = t[2];
        float r = a[o + 4], g = a[o + 5], b = a[o + 6], al = a[o + 7];
        buf.addVertex(p.x - u.x - v.x, p.y - u.y - v.y, p.z - u.z - v.z).setUv(0, 0).setColor(r, g, b, al);
        buf.addVertex(p.x + u.x - v.x, p.y + u.y - v.y, p.z + u.z - v.z).setUv(1, 0).setColor(r, g, b, al);
        buf.addVertex(p.x + u.x + v.x, p.y + u.y + v.y, p.z + u.z + v.z).setUv(1, 1).setColor(r, g, b, al);
        buf.addVertex(p.x - u.x + v.x, p.y - u.y + v.y, p.z - u.z + v.z).setUv(0, 1).setColor(r, g, b, al);
    }

    /** Disque flou : éventail de triangles, opaque au centre et transparent au bord. */
    private static void nebula(BufferBuilder buf, float[] a, int o) {
        Vector3f[] t = tangent(a, o, RenderContext.SKY_DISTANCE * a[o + 3]);
        for (int k = 0; k < NEBULA_FAN; k++) {
            float a0 = (float) (2 * Math.PI * k / NEBULA_FAN), a1 = (float) (2 * Math.PI * (k + 1) / NEBULA_FAN);
            buf.addVertex(t[0].x, t[0].y, t[0].z).setColor(a[o + 4], a[o + 5], a[o + 6], a[o + 7]);
            edge(buf, a, o, t, a0);
            edge(buf, a, o, t, a1);
        }
    }

    private static void edge(BufferBuilder buf, float[] a, int o, Vector3f[] t, float angle) {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        buf.addVertex(t[0].x + c * t[1].x + s * t[2].x, t[0].y + c * t[1].y + s * t[2].y, t[0].z + c * t[1].z + s * t[2].z)
                .setColor(a[o + 4], a[o + 5], a[o + 6], 0f);
    }
}
