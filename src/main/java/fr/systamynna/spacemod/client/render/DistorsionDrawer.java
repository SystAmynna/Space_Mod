package fr.systamynna.spacemod.client.render;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexSorting;

import fr.systamynna.spacemod.core.render.DistorsionRender;
import fr.systamynna.spacemod.core.render.RenderMath.Placed;
import fr.systamynna.spacemod.core.render.SpaceRenderObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * Trou noir / trou blanc : le ciel déjà dessiné derrière l'objet est capturé dans une cubemap (toutes les directions, pas seulement l'écran),
 * puis redessiné déformé par {@code shaders/core/distorsion.fsh} (lentille, ombre, chute).
 * Résolution calée sur celle de l'écran (un texel au centre d'une face ≈ un pixel), mipmaps pour les zones comprimées ; de loin seules les faces
 * que la lentille peut lire sont dessinées. Les couches lointaines (galaxies, étoiles majeures), coûteuses et quasi fixes, sont gardées dans un cache
 * rafraîchi d'une face par image ; les éléments locaux (disque, étoiles du système) sont redessinés par-dessus à chaque image.
 * En fin de plongée, une passe de brillance ({@code distorsion_bright.fsh}) isole les objets lumineux pour leur halo.
 * shortcut: une capture par lentille visible et par image ; suffisant pour une ou deux lentilles, à revoir au-delà.
 * shortcut: si l'observateur tourne vite, les faces du cache pas encore rafraîchies sont décalées quelques images ; invalider le cache sur rotation si ça se voit.
 */
final class DistorsionDrawer extends EffectDrawer {
    /** Côté (px) d'une face : pas de {@link #STEP} (pas de réallocation à chaque image pendant le FOV dynamique), plafonné.
     * shortcut: plafond 2048 (~230 Mo avec le cache et les mips) ; au-delà (4K, spyglass) la lentille est un peu plus douce que l'écran. */
    private static final int STEP = 256, MAX_SIZE = 2048;
    /** Distances en rayons de l'horizon (voir distorsion.fsh) : sous FALL_FROM la chute replie tout le ciel (6 faces) ; sous GLOW_FROM les objets lumineux s'étalent. */
    private static final double FALL_FROM = 4, GLOW_FROM = 0.4;
    /** Ombre vue de loin, en rayons (3√3/2, voir distorsion.fsh) ; et demi-angle du coin d'une face, atan(√2). */
    private static final double SHADOW = 2.6, FACE_CORNER = Math.atan(Math.sqrt(2));
    /** Unités de texture des cubemaps : le shader les lit par les uniformes {@code Sky} et {@code Bright} (MC ne lie ses samplers qu'en 2D). */
    private static final int UNIT = 3, BRIGHT_UNIT = 4;
    /** La cubemap de brillance est à 1/BRIGHT_DIV de la résolution (elle ne sert qu'aux halos). */
    private static final int BRIGHT_DIV = 4;
    /** Une face du cache plus vieille que ça est redessinée tout de suite (première capture, retour près d'une lentille, nouvelle face utile). */
    private static final long STALE_NS = 500_000_000L;
    /** Face {@code GL_TEXTURE_CUBE_MAP_POSITIVE_X + i} : direction visée puis haut (convention GL des cubemaps). */
    private static final float[][] FACES = {{1, 0, 0, 0, -1, 0}, {-1, 0, 0, 0, -1, 0}, {0, 1, 0, 0, 0, 1}, {0, -1, 0, 0, 0, -1}, {0, 0, 1, 0, -1, 0},
            {0, 0, -1, 0, -1, 0}};
    private static final float[] BLACK = {0, 0, 0, 1};

    /** Cubemaps : lue par la lentille (mips), cache des couches lointaines, brillance (HDR, mips). */
    private static int cube = -1, far, bright, fbo, readFbo, size;
    private static final long[] farStamp = new long[6];
    /** Vrai pendant une capture : les autres lentilles ne s'y dessinent pas (pas de récursion). */
    static boolean capturing;

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
        bindCube(UNIT, cube);
        bindCube(BRIGHT_UNIT, bright);
    }

    private static void bindCube(int unit, int tex) {
        RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, tex);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
    }

    /**
     * Remplit la cubemap lue par la lentille : couches lointaines ({@code drawFar}, depuis le cache) puis éléments locaux derrière la lentille
     * ({@code drawNear}) ; chacun reçoit la rotation de vue de la face. {@code p}, {@code d} : la lentille, pour ne traiter que les faces qu'elle lit.
     */
    static void capture(Consumer<Matrix4f> drawFar, Consumer<Matrix4f> drawNear, Placed p, DistorsionRender d) {
        // texel au centre d'une face (2/size rad) ≈ pixel écran (2/(m11·H))
        int want = Math.min(MAX_SIZE, Math.max(STEP, (int) Math.ceil(RenderSystem.getProjectionMatrix().m11() * Minecraft.getInstance().getWindow().getHeight() / STEP) * STEP));
        if (cube < 0 || want != size) allocate(want);
        double dr = p.distance() / (p.radius() * p.scale().x); // shortcut: échelle supposée uniforme, comme SkyShaderDrawer
        double cone = sourceCone(dr, d);
        Vector3d axis = new Vector3d(p.pos()).normalize();
        boolean[] needed = new boolean[6], refresh = new boolean[6];
        long now = System.nanoTime();
        int oldest = -1;
        for (int i = 0; i < 6; i++) {
            float[] f = FACES[i];
            needed[i] = axis.angle(new Vector3d(f[0], f[1], f[2])) <= cone + FACE_CORNER;
            if (!needed[i]) continue;
            if (now - farStamp[i] > STALE_NS) refresh[i] = true;
            else if (oldest < 0 || farStamp[i] < farStamp[oldest]) oldest = i;
        }
        if (oldest >= 0) refresh[oldest] = true; // + une face à jour par image, à tour de rôle

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float) (Math.PI / 2), 1, 0.05f, 1000f), VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.viewport(0, 0, size, size);
        capturing = true;
        try {
            for (int i = 0; i < 6; i++) {
                if (!needed[i]) continue;
                float[] f = FACES[i];
                Matrix4f view = new Matrix4f().lookAt(0, 0, 0, f[0], f[1], f[2], f[3], f[4], f[5]);
                if (refresh[i]) {
                    target(far, i);
                    GL30.glClearBufferfv(GL11.GL_COLOR, 0, BLACK); // n'altère pas la couleur de clear de MC
                    drawFar.accept(view);
                    farStamp[i] = now;
                }
                // face lue par la lentille : copie du cache lointain, puis éléments locaux par-dessus
                target(cube, i);
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
                GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, far, 0);
                GL30.glBlitFramebuffer(0, 0, size, size, 0, 0, size, size, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
                GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
                drawNear.accept(view);
            }
            mipmaps(cube);
            ShaderInstance sh = ClientShaders.distorsionBright;
            if (dr < GLOW_FROM && sh != null) brightPass(sh, needed);
        } finally {
            capturing = false;
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
            RenderSystem.restoreProjectionMatrix();
        }
    }

    /** Dessin dans la face {@code i} de la cubemap {@code tex}. */
    private static void target(int tex, int i) {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, tex, 0);
    }

    /** Objets lumineux (au-dessus du seuil) des faces utiles, au quart de la résolution, puis mips : les halos de fin de plongée. */
    private static void brightPass(ShaderInstance sh, boolean[] needed) {
        int bs = size / BRIGHT_DIV;
        RenderSystem.viewport(0, 0, bs, bs);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        bindCube(UNIT, cube);
        sh.safeGetUniform("Texel").set(2f / size);
        VertexBuffer vb = SkyShaderDrawer.sphere();
        vb.bind();
        for (int i = 0; i < 6; i++) {
            if (!needed[i]) continue;
            float[] f = FACES[i];
            Vector3d n = new Vector3d(f[0], f[1], f[2]), v = new Vector3d(f[3], f[4], f[5]), u = n.cross(v, new Vector3d()); // axes s, t de la face (comme lookAt)
            sh.safeGetUniform("FaceN").set((float) n.x, (float) n.y, (float) n.z);
            sh.safeGetUniform("FaceU").set((float) u.x, (float) u.y, (float) u.z);
            sh.safeGetUniform("FaceV").set((float) v.x, (float) v.y, (float) v.z);
            target(bright, i);
            vb.drawWithShader(new Matrix4f().lookAt(0, 0, 0, f[0], f[1], f[2], f[3], f[4], f[5]), RenderSystem.getProjectionMatrix(), sh);
        }
        VertexBuffer.unbind();
        mipmaps(bright);
    }

    private static void mipmaps(int tex) {
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, tex);
        GL30.glGenerateMipmap(GL13.GL_TEXTURE_CUBE_MAP);
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, 0);
    }

    /** Demi-angle (rad) autour de la lentille des directions qu'elle peut lire : π pendant la chute, sinon sa portée plus la déviation maximale (bord de l'ombre). */
    static double sourceCone(double dr, DistorsionRender d) {
        if (!(dr >= FALL_FROM)) return Math.PI;
        double thetaR = Math.asin(1 / dr);
        return Math.min(Math.PI, d.extent * thetaR + d.strength * d.strength * thetaR / SHADOW);
    }

    private static void allocate(int s) {
        if (cube < 0) {
            cube = GL11.glGenTextures();
            far = GL11.glGenTextures();
            bright = GL11.glGenTextures();
            fbo = GlStateManager.glGenFramebuffers();
            readFbo = GlStateManager.glGenFramebuffers();
            GL11.glEnable(GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS); // filtrage sans couture entre les faces (n'affecte que les cubemaps)
        }
        size = s;
        Arrays.fill(farStamp, 0); // cache périmé : tout redessiner
        cubeTex(cube, s, GL11.GL_RGBA8, GL11.GL_UNSIGNED_BYTE, true); // mips : zones comprimées sans scintillement
        cubeTex(far, s, GL11.GL_RGBA8, GL11.GL_UNSIGNED_BYTE, false);
        cubeTex(bright, s / BRIGHT_DIV, GL30.GL_RGBA16F, GL11.GL_FLOAT, true); // HDR : la somme des objets lumineux dépasse 1
    }

    private static void cubeTex(int tex, int s, int format, int type, boolean mips) {
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, tex);
        for (int i = 0; i < 6; i++) GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, format, s, s, 0, GL11.GL_RGBA, type, (ByteBuffer) null);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, mips ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        if (mips) GL30.glGenerateMipmap(GL13.GL_TEXTURE_CUBE_MAP); // chaîne allouée (texture complète avant la première capture)
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, 0);
    }
}
