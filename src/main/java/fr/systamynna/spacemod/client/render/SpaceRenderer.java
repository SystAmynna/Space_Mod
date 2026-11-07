package fr.systamynna.spacemod.client.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import fr.systamynna.spacemod.client.ClientLayer;
import fr.systamynna.spacemod.core.render.RenderMath.Placed;
import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.clock.SpaceClock;
import fr.systamynna.spacemod.core.render.BlackHoleRender;
import fr.systamynna.spacemod.core.render.DistorsionRender;
import fr.systamynna.spacemod.core.render.RenderConfig;
import fr.systamynna.spacemod.core.render.RingRender;
import fr.systamynna.spacemod.core.render.RenderMath;
import fr.systamynna.spacemod.core.render.SpaceRender;
import fr.systamynna.spacemod.core.render.StarRender;
import fr.systamynna.spacemod.core.render.SpaceRenderObject;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Rendu client du ciel spatial, en couches suivant l'arbre : les galaxies (types {@code custom}, du plus loin au plus proche), puis les étoiles
 * majeures du conteneur courant, puis les autres éléments (système local). Les {@link SpaceRender} des objets s'y déclarent (via la {@link ClientLayer}) ;
 * à chaque image on place leurs éléments vus du SpaceObject de référence et on dessine les éléments texturés par séries de même texture (un appel GPU par série).
 */
public final class SpaceRenderer implements ClientLayer.Listener {
    public static final SpaceRenderer INSTANCE = new SpaceRenderer();

    /** Rayon fictif de la sphère du ciel (les éléments sont ramenés dessus, seuls direction et angle comptent). */
    public static final double SKY_RADIUS = 100;
    /** Sous ce rayon angulaire (rad) l'élément est ignoré (les étoiles lointaines sont dessinées par {@link MajorStarsDrawer}). */
    private static final double MIN_ANGLE = 2e-4;

    private final Map<UUID, SpaceRender> renders = new HashMap<>();
    private final Map<String, SpaceRenderObjectDrawer> drawers = Map.of("planet", new PlanetDrawer(),
            "galaxy_cloud", new GalaxyCloudDrawer(), "galaxy_stars", new GalaxyStarsDrawer(),
            "star", new StarDrawer(), "black_hole", new BlackHoleDrawer(), "gamma_eruption", new GammaEruptionDrawer(),
            "ring", new RingDrawer(), "distorsion", new DistorsionDrawer());
    // shortcut: liste et enregistrements de placement réalloués à chaque image ; à réutiliser si le nombre d'éléments grossit.
    /** {@code flare} : intensité du flare d'une étoile, ou passe d'un anneau (1 = moitié arrière, 2 = moitié avant) ; {@code prio} : ordre à distance égale. */
    private record Item(SpaceRenderObject ro, SpaceRenderObjectDrawer drawer, Placed placed, ResourceLocation texture, UUID carrier, float flare, int prio) {}

    /** Distance (km) de l'étoile à l'objet le plus éloigné de son système, plancher 1 UA ; mise en cache ~1 s par étoile. */
    private static final double MIN_FARTHEST = 1.496e8;
    private final Map<UUID, double[]> farthest = new HashMap<>();

    // shortcut: ClientLayer.children est un parcours complet, d'où le recalcul seulement chaque seconde ; ajouter un index parent→enfants si le client reçoit beaucoup d'objets.
    private double farthest(ClientLayer layer, SpaceObject system, SpaceObject star) {
        long now = System.nanoTime();
        double[] c = farthest.get(star.getUuid());
        if (c == null || now > (long) c[1]) {
            c = new double[] {Math.max(MIN_FARTHEST, RenderMath.farthestFrom(o -> layer.children(o), system, star)), now + 1_000_000_000L};
            farthest.put(star.getUuid(), c);
        }
        return c[0];
    }

    private SpaceRenderer() {}

    @Override
    public void applied(SpaceObject o) { o.getComponent(SpaceRender.class).ifPresentOrElse(r -> renders.put(o.getUuid(), r), () -> renders.remove(o.getUuid())); }

    @Override
    public void dropped(UUID uuid) {
        renders.remove(uuid);
        farthest.remove(uuid);
    }

    @Override
    public void cleared() {
        renders.clear();
        farthest.clear();
    }

    /** Config du ciel de ce niveau, ou {@code null} (ciel vanilla). */
    static RenderConfig config(ClientLevel level) { return ClientRenderState.forDimension(level.dimension().location()); }

    /** Dessine le ciel ; {@code modelView} = rotation de la caméra (sans translation). */
    public void draw(ClientLevel level, Matrix4f modelView) {
        RenderConfig cfg = config(level);
        ClientLayer layer = ClientLayer.INSTANCE;
        SpaceObject host = cfg == null ? null : layer.get(cfg.object);
        if (host == null) return;
        layer.step(SpaceClock.CLIENT.now() / 50); // temps continu (nanoTime) : mouvement fluide à chaque image
        RenderMath.Observer ob = RenderMath.observer(layer::get, host, cfg);

        List<Item> items = new ArrayList<>(), galaxies = new ArrayList<>();
        for (Map.Entry<UUID, SpaceRender> e : renders.entrySet()) {
            SpaceObject carrier = layer.get(e.getKey());
            if (carrier == null) continue;
            for (SpaceRenderObject ro : e.getValue().objects) {
                SpaceRenderObjectDrawer d = drawers.get(ro.type());
                if (d == null) continue;
                Placed p = RenderMath.place(layer::get, ob, carrier, ro.transform(), ro.radius());
                if (p == null) continue;
                double ar = p.angularRadius();
                if (d.custom()) { // l'observateur peut être dans la galaxie (NaN) : on la dessine quand même
                    if (Double.isNaN(ar) || ar >= MIN_ANGLE) galaxies.add(new Item(ro, d, p, null, e.getKey(), 0f, 0));
                    continue;
                }
                if (d.overlay()) { // effet local : l'observateur peut être dedans (NaN), comme pour un trou noir ou un anneau
                    // une étoile est toujours dessinée (au moins sa base d'étoile lointaine), même sous le seuil angulaire : sans cela le Soleil disparaît au-delà de ~22 UA
                    if (Double.isNaN(ar) || ar >= MIN_ANGLE || ro instanceof StarRender) {
                        SpaceObject system = ro instanceof StarRender ? RenderMath.stellarSystemOf(layer::get, ob, carrier) : null; // étoile locale : l'observateur est dans son système stellaire
                        float flare = system == null ? 0f : (float) RenderMath.flare(p.distance() / farthest(layer, system, carrier));
                        // anneau d'un trou noir : moitié arrière avant la lentille (déformée), moitié avant après la sphère noire (jamais déformée)
                        if (ro instanceof RingRender && e.getValue().objects.stream().anyMatch(o -> o instanceof DistorsionRender || o instanceof BlackHoleRender)) {
                            items.add(new Item(ro, d, p, null, e.getKey(), 1f, d.priority()));
                            items.add(new Item(ro, d, p, null, e.getKey(), 2f, 3));
                        } else items.add(new Item(ro, d, p, null, e.getKey(), flare, d.priority()));
                    }
                    continue;
                }
                if (!(ar >= MIN_ANGLE)) continue;
                ResourceLocation tex = d.texture(ro);
                if (tex != null) items.add(new Item(ro, d, p, tex, e.getKey(), 0f, 0));
            }
        }
        // shortcut: la priorité (lentille après anneau, trou noir après lentille) est un décalage relatif de 1e-9 sur la distance ; assez pour départager un même porteur.
        Comparator<Item> farToNear = Comparator.comparingDouble((Item i) -> i.placed.distance() * (1 - 1e-9 * i.prio)).reversed();
        galaxies.sort(farToNear);
        items.sort(farToNear);

        drawFar(modelView, ob, galaxies);
        drawItems(modelView, ob, galaxies, items);
    }

    /** Couches lointaines vues avec la rotation {@code mv} : galaxies puis étoiles majeures (quasi fixes d'une image à l'autre, mises en cache par les lentilles). */
    private static void drawFar(Matrix4f mv, RenderMath.Observer ob, List<Item> galaxies) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        for (Item i : galaxies) i.drawer.draw(mv, i.placed, i.carrier, i.ro, i.flare); // couche 1 : galaxies
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        MajorStarsDrawer.INSTANCE.draw(ob, mv); // couche 2 : étoiles majeures ; les éléments du système local se dessinent par-dessus
    }

    /** Éléments du système local vus avec la rotation {@code mv}, loin → près, par-dessus les couches lointaines. */
    private void drawItems(Matrix4f mv, RenderMath.Observer ob, List<Item> galaxies, List<Item> items) {
        if (items.isEmpty()) return;

        beginItems();
        BufferBuilder bb = null;
        ResourceLocation cur = null;
        for (int n = 0; n < items.size(); n++) {
            Item i = items.get(n);
            if (i.drawer.overlay()) { // effet à shader : vide le lot courant, se dessine, puis le lot reprend
                if (bb != null) {
                    flush(bb, cur);
                    bb = null;
                }
                if (i.ro instanceof DistorsionRender) { // lentille : capture d'abord tout ce qui est derrière elle, dans toutes les directions
                    if (DistorsionDrawer.capturing) continue;
                    List<Item> behind = items.subList(0, n);
                    DistorsionDrawer.capture(face -> drawFar(face, ob, galaxies), face -> drawItems(face, ob, galaxies, behind), i.placed, (DistorsionRender) i.ro);
                    beginItems();
                }
                i.drawer.draw(mv, i.placed, i.carrier, i.ro, i.flare);
                continue;
            }
            if (bb != null && !i.texture.equals(cur)) {
                flush(bb, cur);
                bb = null;
            }
            if (bb == null) {
                bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
                cur = i.texture;
            }
            i.drawer.emit(bb, mv, i.placed, SKY_RADIUS / i.placed.distance(), i.ro);
        }
        if (bb != null) flush(bb, cur);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void beginItems() {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
    }

    private static void flush(BufferBuilder bb, ResourceLocation tex) {
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.enableCull(); // sans depth test, la face arrière de la sphère recouvrirait la face avant
        BufferUploader.drawWithShader(bb.buildOrThrow());
        RenderSystem.disableCull();
    }
}
