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
import fr.systamynna.spacemod.core.render.RenderConfig;
import fr.systamynna.spacemod.core.render.RenderMath;
import fr.systamynna.spacemod.core.render.SpaceRender;
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
    private final Map<String, SpaceRenderObjectDrawer> drawers = Map.of("planet", new PlanetDrawer(), "galaxy", new GalaxyDrawer());
    // shortcut: liste et enregistrements de placement réalloués à chaque image ; à réutiliser si le nombre d'éléments grossit.
    private record Item(SpaceRenderObject ro, SpaceRenderObjectDrawer drawer, Placed placed, ResourceLocation texture, UUID carrier) {}

    private SpaceRenderer() {}

    @Override
    public void applied(SpaceObject o) { o.getComponent(SpaceRender.class).ifPresentOrElse(r -> renders.put(o.getUuid(), r), () -> renders.remove(o.getUuid())); }

    @Override
    public void dropped(UUID uuid) { renders.remove(uuid); }

    @Override
    public void cleared() { renders.clear(); }

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
                    if (Double.isNaN(ar) || ar >= MIN_ANGLE) galaxies.add(new Item(ro, d, p, null, e.getKey()));
                    continue;
                }
                if (!(ar >= MIN_ANGLE)) continue;
                ResourceLocation tex = d.texture(ro);
                if (tex != null) items.add(new Item(ro, d, p, tex, e.getKey()));
            }
        }
        Comparator<Item> farToNear = Comparator.comparingDouble((Item i) -> i.placed.distance()).reversed();
        galaxies.sort(farToNear);
        items.sort(farToNear);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        for (Item i : galaxies) i.drawer.draw(modelView, i.placed, i.carrier, i.ro); // couche 1 : galaxies
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        MajorStarsDrawer.INSTANCE.draw(ob, modelView); // couche 2 : étoiles majeures ; les éléments du système local se dessinent par-dessus
        if (items.isEmpty()) return;

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        BufferBuilder bb = null;
        ResourceLocation cur = null;
        for (Item i : items) {
            if (bb != null && !i.texture.equals(cur)) {
                flush(bb, cur);
                bb = null;
            }
            if (bb == null) {
                bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
                cur = i.texture;
            }
            i.drawer.emit(bb, modelView, i.placed, SKY_RADIUS / i.placed.distance(), i.ro);
        }
        if (bb != null) flush(bb, cur);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void flush(BufferBuilder bb, ResourceLocation tex) {
        RenderSystem.setShaderTexture(0, tex);
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }
}
