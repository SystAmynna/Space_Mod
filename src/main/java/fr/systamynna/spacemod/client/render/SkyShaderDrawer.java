package fr.systamynna.spacemod.client.render;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import fr.systamynna.spacemod.core.render.RenderMath.Placed;
import fr.systamynna.spacemod.core.render.Rgba;
import fr.systamynna.spacemod.core.render.SpaceRenderObject;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

/**
 * Base des dessins à shader (galaxies, étoiles, trous noirs, anneaux…) : une sphère plein ciel dont le fragment lance un rayon dans le repère de la galaxie.
 * Le même dessin sert vue de l'extérieur (galaxie lointaine) et de l'intérieur (la nôtre) : un rayon qui rate la sphère englobante est rejeté.
 * Les sous-classes donnent le shader et ses uniformes propres ({@link GalaxyCloudDrawer}, {@link GalaxyStarsDrawer}).
 * shortcut: une sphère plein ciel par galaxie (coût de remplissage) ; passer à un quad englobant au-delà d'une dizaine de galaxies visibles.
 */
abstract class SkyShaderDrawer implements SpaceRenderObjectDrawer {
    private static final int LON = 32, LAT = 16;

    /** Partagée par tous les dessins de galaxie. */
    @Nullable
    private static VertexBuffer sphere;
    private final Matrix3f rot = new Matrix3f();

    /** Shader de ce dessin, ou {@code null} tant que les ressources ne sont pas chargées. */
    @Nullable
    abstract ShaderInstance shader();

    /** Uniformes propres au type, en plus de {@code GalRot}, {@code CamPos} et {@code Seed}. */
    abstract void uniforms(ShaderInstance sh, SpaceRenderObject ro);

    /** Galaxies par défaut ; les effets locaux (étoile, anneau, trou noir…) surchargent par {@code custom()=false} et {@code overlay()=true}. */
    @Override
    public boolean custom() { return true; }

    static void color(ShaderInstance sh, String name, Rgba c) { sh.safeGetUniform(name).set((float) c.r(), (float) c.g(), (float) c.b(), (float) c.a()); }

    /** Préparation propre au type juste avant le dessin (ex. capture de l'image pour la lentille). */
    void beforeDraw(ShaderInstance sh) {}

    @Override
    @Nullable
    public ResourceLocation texture(SpaceRenderObject ro) { return null; }

    @Override
    public void emit(BufferBuilder out, Matrix4f modelView, Placed p, double k, SpaceRenderObject ro) {}

    @Override
    public void draw(Matrix4f modelView, Placed p, UUID carrier, SpaceRenderObject ro, float flare) {
        ShaderInstance sh = shader();
        if (sh == null) return; // ressources pas (encore) chargées
        // galaxie -> observateur : p.orientation ; on veut l'inverse. shortcut: échelle supposée uniforme (p.scale().x)
        Quaterniond inv = p.orientation().conjugate(new Quaterniond());
        Vector3d cam = inv.transform(new Vector3d(p.pos()).negate()).div(p.radius() * p.scale().x);
        long s = (carrier.getMostSignificantBits() ^ carrier.getLeastSignificantBits()) * 0x9E3779B97F4A7C15L;
        sh.safeGetUniform("GalRot").set(rot.set(new Quaternionf(inv)));
        sh.safeGetUniform("CamPos").set((float) cam.x, (float) cam.y, (float) cam.z);
        sh.safeGetUniform("Seed").set((s & 0xFFFFFF) / 16777216f, (s >>> 24 & 0xFFFFFF) / 16777216f);
        sh.safeGetUniform("Flare").set(flare);
        sh.safeGetUniform("Time").set((float) (System.nanoTime() / 1e9 % 3600)); // modulo : garde la précision float
        uniforms(sh, ro);
        beforeDraw(sh);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        VertexBuffer vb = sphere();
        vb.bind();
        vb.drawWithShader(modelView, RenderSystem.getProjectionMatrix(), sh);
        VertexBuffer.unbind();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    static VertexBuffer sphere() {
        if (sphere != null) return sphere;
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        float R = (float) SpaceRenderer.SKY_RADIUS;
        for (int la = 0; la < LAT; la++)
            for (int lo = 0; lo < LON; lo++) {
                int[][] c = {{lo, la}, {lo + 1, la}, {lo + 1, la + 1}, {lo, la}, {lo + 1, la + 1}, {lo, la + 1}};
                for (int[] v : c) {
                    double th = Math.PI * v[1] / LAT, ph = 2 * Math.PI * v[0] / LON;
                    bb.addVertex((float) (R * Math.sin(th) * Math.cos(ph)), (float) (R * Math.cos(th)), (float) (R * Math.sin(th) * Math.sin(ph)));
                }
            }
        MeshData mesh = bb.buildOrThrow();
        sphere = new VertexBuffer(VertexBuffer.Usage.STATIC);
        sphere.bind();
        sphere.upload(mesh);
        VertexBuffer.unbind();
        return sphere;
    }
}
