package fr.systamynna.spacemod.client.sky;

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
import fr.systamynna.spacemod.client.render.SpaceRenderer;
import fr.systamynna.spacemod.core.SpaceNet.SkyChunk;
import fr.systamynna.spacemod.core.SpaceNet.SkyDrop;
import fr.systamynna.spacemod.core.SpaceNet.Star;
import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.render.RenderMath;
import fr.systamynna.spacemod.core.render.SpaceRender;
import fr.systamynna.spacemod.core.sky.SkyParams;
import fr.systamynna.spacemod.core.sky.SkyStreamer.CellKey;
import net.minecraft.client.renderer.GameRenderer;

/**
 * Ciel lointain côté client : cache des cellules d'étoiles reçues, dessiné en un seul buffer GPU statique (carrés tangents à la sphère du ciel,
 * additifs). Le buffer est exprimé dans le repère de l'ancêtre porteur des résumés : il n'est reconstruit que si les cellules, les systèmes « détaillés »
 * ou la position de l'observateur changent ; la rotation du repère (rotation d'une planète...) s'applique à chaque image par la matrice de dessin.
 * Un système dont au moins un enfant a un {@link SpaceRender} côté client est dessiné en détail par {@link SpaceRenderer} : son résumé est masqué.
 */
public final class ClientSky implements ClientLayer.Listener {
    public static final ClientSky INSTANCE = new ClientSky();

    /** Demi-côté (rad) d'une étoile au flux limite, et plafond. */
    private static final double BASE_ANGLE = 8e-4, MAX_ANGLE = 6e-3;
    /** Déplacement (km) de l'observateur qui déclenche une reconstruction (~67 UA : ~0,1 px pour l'étoile la plus proche). shortcut: seuil absolu, à rendre relatif à la distance de l'étoile la plus proche. */
    private static final double OBS_EPS_KM = 1e10;

    private final Map<CellKey, List<Star>> cells = new HashMap<>();
    /** Objet avec un SpaceRender → son parent ; parent → nombre de ses enfants rendus. Mis à jour à apply/drop, jamais balayés par image. */
    private final Map<UUID, UUID> renderParent = new HashMap<>();
    private final Map<UUID, Integer> detailed = new HashMap<>();
    @Nullable
    private UUID frame;
    private boolean dirty;
    private final Vector3d builtAt = new Vector3d();
    private boolean built;
    private VertexBuffer vbo;
    private int count;
    private final Matrix4f mv = new Matrix4f();

    private ClientSky() {}

    // --- données ---

    public void chunk(SkyChunk p) {
        CellKey k = new CellKey(p.parent(), p.cx(), p.cy(), p.cz());
        if (p.first()) cells.put(k, new ArrayList<>(p.stars()));
        else cells.computeIfAbsent(k, x -> new ArrayList<>()).addAll(p.stars());
        frame = p.parent();
        dirty = true;
    }

    public void drop(SkyDrop p) {
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
        if (dirty || !built || obs.distanceSquared(builtAt) > OBS_EPS_KM * OBS_EPS_KM) rebuild(obs);
        if (count == 0) return;
        // direction dans le repère → direction vue par l'observateur : rotation inverse de celle de l'observateur dans le repère
        mv.set(modelView).rotate(new Quaternionf(pa.q().mul(ob.rot(), new Quaterniond()).conjugate()));
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        vbo.bind();
        vbo.drawWithShader(mv, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
        VertexBuffer.unbind();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /** Reconstruit le buffer : un carré par étoile, tangent à la sphère du ciel, taille et alpha selon le flux {@code lum / d²}. */
    private void rebuild(Vector3d obs) {
        dirty = false;
        built = true;
        builtAt.set(obs);
        double R = SpaceRenderer.SKY_RADIUS, ly2 = SkyParams.LY_KM * SkyParams.LY_KM;
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        count = 0;
        for (Map.Entry<CellKey, List<Star>> e : cells.entrySet()) {
            CellKey k = e.getKey();
            if (!k.parent().equals(frame)) continue;
            double ox = k.cx() * SkyParams.CELL_KM - obs.x, oy = k.cy() * SkyParams.CELL_KM - obs.y, oz = k.cz() * SkyParams.CELL_KM - obs.z;
            for (Star s : e.getValue()) {
                if (detailed.containsKey(s.uuid())) continue;
                double dx = ox + s.x(), dy = oy + s.y(), dz = oz + s.z(), d2 = dx * dx + dy * dy + dz * dz;
                if (!(d2 > 0)) continue;
                double b = s.lum() * ly2 / d2 / SkyParams.FLUX_LIMIT; // brillance relative à la limite de visibilité
                if (b < 0.1) continue;
                double m = Math.max(0, Math.log10(b)), h = R * Math.min(MAX_ANGLE, BASE_ANGLE * (1 + 0.6 * m));
                int a = (int) (255 * Math.min(1, 0.35 + 0.2 * m));
                double inv = 1 / Math.sqrt(d2), nx = dx * inv, ny = dy * inv, nz = dz * inv;
                // base tangente (t1, t2) : t1 = up x n (up = y, ou x près des pôles)
                double ux = Math.abs(ny) < 0.99 ? 0 : 1, uy = ux == 0 ? 1 : 0;
                double t1x = uy * nz, t1y = -ux * nz, t1z = ux * ny - uy * nx, l = 1 / Math.sqrt(t1x * t1x + t1y * t1y + t1z * t1z);
                t1x *= l * h; t1y *= l * h; t1z *= l * h;
                double t2x = ny * t1z - nz * t1y, t2y = nz * t1x - nx * t1z, t2z = nx * t1y - ny * t1x;
                double cx = nx * R, cy = ny * R, cz = nz * R;
                int r = s.color() >> 16 & 255, g = s.color() >> 8 & 255, bl = s.color() & 255;
                bb.addVertex((float) (cx - t1x - t2x), (float) (cy - t1y - t2y), (float) (cz - t1z - t2z)).setColor(r, g, bl, a);
                bb.addVertex((float) (cx + t1x - t2x), (float) (cy + t1y - t2y), (float) (cz + t1z - t2z)).setColor(r, g, bl, a);
                bb.addVertex((float) (cx + t1x + t2x), (float) (cy + t1y + t2y), (float) (cz + t1z + t2z)).setColor(r, g, bl, a);
                bb.addVertex((float) (cx - t1x + t2x), (float) (cy - t1y + t2y), (float) (cz - t1z + t2z)).setColor(r, g, bl, a);
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
