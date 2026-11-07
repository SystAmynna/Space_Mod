package fr.systamynna.spacemod.client.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

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

import fr.systamynna.spacemod.client.ClientLayer;
import fr.systamynna.spacemod.core.SpaceNet.MajorChunk;
import fr.systamynna.spacemod.core.SpaceNet.MajorDrop;
import fr.systamynna.spacemod.core.SpaceNet.Star;
import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.render.RenderMath;
import fr.systamynna.spacemod.core.render.SpaceRender;
import fr.systamynna.spacemod.core.major.MajorParams;
import fr.systamynna.spacemod.core.major.MajorStreamer.CellKey;
import com.mojang.blaze3d.platform.NativeImage;
import fr.systamynna.spacemod.AmynnaSpaceMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Étoiles majeures côté client : cache des cellules d'étoiles reçues du {@code MajorContainer} courant, dessiné en un seul buffer GPU statique (carrés tangents à la sphère du ciel,
 * additifs). Le buffer est exprimé dans le repère du conteneur : il n'est reconstruit que si les cellules, les systèmes « détaillés »
 * ou la position de l'observateur changent ; la rotation du repère (rotation d'une planète...) s'applique à chaque image par la matrice de dessin.
 * Un système dont au moins un enfant a un {@link SpaceRender} côté client est dessiné en détail par {@link SpaceRenderer} : son étoile est masquée.
 */
public final class MajorStarsDrawer implements ClientLayer.Listener {
    public static final MajorStarsDrawer INSTANCE = new MajorStarsDrawer();

    /** Demi-côté (rad) d'une étoile au flux limite, et plafond. */
    private static final double BASE_ANGLE = 8e-4, MAX_ANGLE = 6e-3;
    /** Déplacement (km) de l'observateur qui déclenche une reconstruction (~67 UA : ~0,1 px pour l'étoile la plus proche). shortcut: seuil absolu, à rendre relatif à la distance de l'étoile la plus proche. */
    private static final double OBS_EPS_KM = 1e10;
    /** Demi-côté minimal d'une étoile à l'écran (px) : en dessous, elle scintille en passant d'un pixel à l'autre. */
    private static final double MIN_HALF_PX = 2.5;

    @Nullable
    private static ResourceLocation SPRITE;

    private final Map<CellKey, List<Star>> cells = new HashMap<>();
    /** Objet avec un SpaceRender → son parent ; parent → nombre de ses enfants rendus. Mis à jour à apply/drop, jamais balayés par image. */
    private final Map<UUID, UUID> renderParent = new HashMap<>();
    private final Map<UUID, Integer> detailed = new HashMap<>();
    @Nullable
    private UUID frame;
    private boolean dirty;
    private final Vector3d builtAt = new Vector3d();
    private boolean built;
    /** Angle d'un pixel (rad) pris en compte à la dernière reconstruction. */
    private double builtPix;
    private VertexBuffer vbo;
    private int count;
    private final Matrix4f mv = new Matrix4f();

    private MajorStarsDrawer() {}

    // --- données ---

    public void chunk(MajorChunk p) {
        CellKey k = new CellKey(p.parent(), p.cx(), p.cy(), p.cz());
        if (p.first()) cells.put(k, new ArrayList<>(p.stars()));
        else cells.computeIfAbsent(k, x -> new ArrayList<>()).addAll(p.stars());
        frame = p.parent();
        dirty = true;
    }

    public void drop(MajorDrop p) {
        cells.remove(new CellKey(p.parent(), p.cx(), p.cy(), p.cz()));
        dirty = true;
    }

    public void clear() {
        cells.clear();
        frame = null;
        dirty = true;
    }

    @Override
    public void applied(SpaceObject o) {
        UUID now = o.getComponent(SpaceRender.class).isPresent() ? o.getParent() : null;
        UUID was = now == null ? renderParent.remove(o.getUuid()) : renderParent.put(o.getUuid(), now);
        if (Objects.equals(was, now)) return;
        if (was != null) detailed.computeIfPresent(was, (k, n) -> n == 1 ? null : n - 1);
        if (now != null) detailed.merge(now, 1, Integer::sum);
        dirty = true;
    }

    @Override
    public void dropped(UUID uuid) {
        UUID was = renderParent.remove(uuid);
        if (was == null) return;
        detailed.computeIfPresent(was, (k, n) -> n == 1 ? null : n - 1);
        dirty = true;
    }

    @Override
    public void cleared() {
        renderParent.clear();
        detailed.clear();
        clear();
    }

    // --- rendu ---

    /** Dessine les étoiles lointaines ; {@code modelView} = rotation de la caméra. À appeler avant les sphères. */
    public void draw(RenderMath.Observer ob, Matrix4f modelView) {
        int i = -1;
        for (int k = 0; k < ob.chain().size() && frame != null; k++) if (ob.chain().get(k).getUuid().equals(frame)) i = k;
        if (i < 0) return; // pas de résumés, ou le repère n'est pas (encore) un ancêtre connu
        RenderMath.Pose pa = RenderMath.toAncestor(ob.chain(), i);
        Vector3d obs = pa.apply(ob.pos());
        // taille angulaire d'un pixel : varie avec le FOV (spyglass) et la fenêtre ; le buffer statique est refait quand elle change de plus de 5 %
        double pix = 2 / RenderSystem.getProjectionMatrix().m11() / Math.max(1, Minecraft.getInstance().getWindow().getHeight());
        if (dirty || !built || obs.distanceSquared(builtAt) > OBS_EPS_KM * OBS_EPS_KM || Math.abs(pix / builtPix - 1) > 0.05) rebuild(obs, pix);
        if (count == 0) return;
        // direction dans le repère → direction vue par l'observateur : rotation inverse de celle de l'observateur dans le repère
        mv.set(modelView).rotate(new Quaternionf(pa.q().mul(ob.rot(), new Quaterniond()).conjugate()));
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, sprite());
        vbo.bind();
        vbo.drawWithShader(mv, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
        VertexBuffer.unbind();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /** Sprite d'étoile : dégradé radial blanc, alpha {@code (1 - r²)²} nul au bord du quad (créé à la première utilisation). */
    private static ResourceLocation sprite() {
        if (SPRITE != null) return SPRITE;
        int n = 64;
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, n, n, false);
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                double r = Math.min(1, Math.hypot((x + 0.5) / n * 2 - 1, (y + 0.5) / n * 2 - 1)), a = (1 - r * r) * (1 - r * r);
                img.setPixelRGBA(x, y, (int) Math.round(255 * a) << 24 | 0xFFFFFF); // ABGR
            }
        DynamicTexture tex = new DynamicTexture(img);
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, "render/star_sprite");
        Minecraft.getInstance().getTextureManager().register(loc, tex);
        tex.setFilter(true, false);
        return SPRITE = loc;
    }

    /** Reconstruit le buffer : un carré par étoile, tangent à la sphère du ciel, taille et alpha selon le flux {@code lum / d²}. */
    private void rebuild(Vector3d obs, double pix) {
        dirty = false;
        built = true;
        builtPix = pix;
        builtAt.set(obs);
        double R = SpaceRenderer.SKY_RADIUS, ly2 = MajorParams.LY_KM * MajorParams.LY_KM;
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        count = 0;
        for (Map.Entry<CellKey, List<Star>> e : cells.entrySet()) {
            CellKey k = e.getKey();
            if (!k.parent().equals(frame)) continue;
            double ox = k.cx() * MajorParams.CELL_KM - obs.x, oy = k.cy() * MajorParams.CELL_KM - obs.y, oz = k.cz() * MajorParams.CELL_KM - obs.z;
            for (Star s : e.getValue()) {
                if (detailed.containsKey(s.uuid())) continue;
                double dx = ox + s.x(), dy = oy + s.y(), dz = oz + s.z(), d2 = dx * dx + dy * dy + dz * dz;
                if (!(d2 > 0)) continue;
                double b = s.lum() * ly2 / d2 / MajorParams.FLUX_LIMIT; // brillance relative à la limite de visibilité
                if (b < 0.1) continue;
                double m = Math.max(0, Math.log10(b)), h = R * Math.max(2 * Math.min(MAX_ANGLE, BASE_ANGLE * (1 + 0.6 * m)), MIN_HALF_PX * pix); // x2 : le sprite s'éteint avant le bord du quad ; jamais sous MIN_HALF_PX
                int a = (int) (255 * Math.min(1, 0.35 + 0.2 * m));
                double inv = 1 / Math.sqrt(d2), nx = dx * inv, ny = dy * inv, nz = dz * inv;
                // base tangente (t1, t2) : t1 = up x n (up = y, ou x près des pôles)
                double ux = Math.abs(ny) < 0.99 ? 0 : 1, uy = ux == 0 ? 1 : 0;
                double t1x = uy * nz, t1y = -ux * nz, t1z = ux * ny - uy * nx, l = 1 / Math.sqrt(t1x * t1x + t1y * t1y + t1z * t1z);
                t1x *= l * h; t1y *= l * h; t1z *= l * h;
                double t2x = ny * t1z - nz * t1y, t2y = nz * t1x - nx * t1z, t2z = nx * t1y - ny * t1x;
                double cx = nx * R, cy = ny * R, cz = nz * R;
                int r = s.color() >> 16 & 255, g = s.color() >> 8 & 255, bl = s.color() & 255;
                bb.addVertex((float) (cx - t1x - t2x), (float) (cy - t1y - t2y), (float) (cz - t1z - t2z)).setUv(0f, 0f).setColor(r, g, bl, a);
                bb.addVertex((float) (cx + t1x - t2x), (float) (cy + t1y - t2y), (float) (cz + t1z - t2z)).setUv(1f, 0f).setColor(r, g, bl, a);
                bb.addVertex((float) (cx + t1x + t2x), (float) (cy + t1y + t2y), (float) (cz + t1z + t2z)).setUv(1f, 1f).setColor(r, g, bl, a);
                bb.addVertex((float) (cx - t1x + t2x), (float) (cy - t1y + t2y), (float) (cz - t1z + t2z)).setUv(0f, 1f).setColor(r, g, bl, a);
                count++;
            }
        }
        MeshData mesh = bb.build();
        if (mesh == null) {
            count = 0;
            return;
        }
        if (vbo == null) vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vbo.bind();
        vbo.upload(mesh);
        VertexBuffer.unbind();
    }
}
