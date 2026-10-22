package fr.systamynna.spacemod.client.render;

import java.util.function.Supplier;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.ShaderInstance;

/** Quad face à la caméra, en mélange additif, dessiné avec un core shader ; sert aux halos d'étoile et aux galaxies. */
final class Billboard {
    /** Classe utilitaire. */
    private Billboard() {}

    /**
     * @param half demi-taille angulaire (rad)
     * @param squash rapport petit axe / grand axe (1 : cercle ; inclinaison d'une galaxie)
     * @param roll rotation du quad autour de la direction visée (rad)
     */
    static void draw(RenderContext ctx, float half, float squash, float roll, float r, float g, float b, float a,
            Supplier<ShaderInstance> shader) {
        draw(ctx, half, squash, roll, r, g, b, a, 0f, shader);
    }

    /** @param disc fraction du quad occupée par le disque de l'astre (uniform {@code DiscRadius}, si le shader en a un) */
    static void draw(RenderContext ctx, float half, float squash, float roll, float r, float g, float b, float a, float disc,
            Supplier<ShaderInstance> shader) {
        float s = RenderContext.SKY_DISTANCE * half;
        Vector3f d = ctx.direction();
        Matrix4f model = new Matrix4f(ctx.view()).translate(d.x * RenderContext.SKY_DISTANCE, d.y * RenderContext.SKY_DISTANCE,
                d.z * RenderContext.SKY_DISTANCE);
        // base du plan perpendiculaire à la direction
        Vector3f u = (Math.abs(d.y) < 0.99f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).cross(d).normalize();
        Vector3f v = new Vector3f(d).cross(u);
        Vector3f major = new Vector3f(u).mul((float) Math.cos(roll)).fma((float) Math.sin(roll), v).mul(s);
        Vector3f minor = new Vector3f(v).mul((float) Math.cos(roll)).fma((float) -Math.sin(roll), u).mul(s * squash);

        RenderSystem.setShader(shader);
        Uniform discUniform = shader.get().getUniform("DiscRadius");
        if (discUniform != null) discUniform.set(disc);
        // sans test de profondeur : la sphère de l'astre, plus proche de la caméra que le plan du quad, masquerait le halo
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(model, -major.x - minor.x, -major.y - minor.y, -major.z - minor.z).setUv(0, 0).setColor(r, g, b, a);
        buffer.addVertex(model, major.x - minor.x, major.y - minor.y, major.z - minor.z).setUv(1, 0).setColor(r, g, b, a);
        buffer.addVertex(model, major.x + minor.x, major.y + minor.y, major.z + minor.z).setUv(1, 1).setColor(r, g, b, a);
        buffer.addVertex(model, -major.x + minor.x, -major.y + minor.y, -major.z + minor.z).setUv(0, 1).setColor(r, g, b, a);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
    }

    /** Couleur {@code #rrggbb} en trois composantes dans [0, 1] ; blanc si la valeur est absente ou illisible. */
    static float[] color(String hex) {
        try {
            int c = Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16);
            return new float[] { (c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f };
        } catch (NumberFormatException e) {
            return new float[] { 1, 1, 1 };
        }
    }
}
