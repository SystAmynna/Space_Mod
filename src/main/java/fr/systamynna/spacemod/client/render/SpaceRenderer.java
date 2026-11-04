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
import fr.systamynna.spacemod.client.sky.ClientSky;
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
 * Rendu client du ciel spatial. Les {@link SpaceRender} des objets s'y déclarent (via la {@link ClientLayer}) ; à chaque image on place
 * leurs éléments vus du SpaceObject de référence, du plus loin au plus proche, et on dessine par séries de même texture (un appel GPU par série).
 */
public final class SpaceRenderer implements ClientLayer.Listener {
    public static final SpaceRenderer INSTANCE = new SpaceRenderer();

    /** Rayon fictif de la sphère du ciel (les éléments sont ramenés dessus, seuls direction et angle comptent). */
    public static final double SKY_RADIUS = 100;
    /** Sous ce rayon angulaire (rad) l'élément est ignoré (les étoiles lointaines sont dessinées par {@link ClientSky}). */
    private static final double MIN_ANGLE = 2e-4;

    private final Map<UUID, SpaceRender> renders = new HashMap<>();
    private final Map<String, SpaceRenderObjectDrawer> drawers = Map.of("planet", new PlanetDrawer());
    // shortcut: liste et enregistrements de placement réalloués à chaque image ; à réutiliser si le nombre d'éléments grossit.
    private record Item(SpaceRenderObject ro, SpaceRenderObjectDrawer drawer, Placed placed, ResourceLocation texture) {}

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
        ClientSky.INSTANCE.draw(ob, modelView); // étoiles lointaines d'abord : les sphères se dessinent par-dessus

        List<Item> items = new ArrayList<>();
        for (Map.Entry<UUID, SpaceRender> e : renders.entrySet()) {
            SpaceObject carrier = layer.get(e.getKey());
            if (carrier == null) continue;
            for (SpaceRenderObject ro : e.getValue().objects) {
                SpaceRenderObjectDrawer d = drawers.get(ro.type());
                if (d == null) continue;
                Placed p = RenderMath.place(layer::get, ob, carrier, ro.transform(), ro.radius());
                if (p == null || !(p.angularRadius() >= MIN_ANGLE)) continue;
                ResourceLocation tex = d.texture(ro);
                if (tex != null) items.add(new Item(ro, d, p, tex));
            }
        }
        if (items.isEmpty()) return;
        items.sort(Comparator.comparingDouble((Item i) -> i.placed.distance()).reversed());

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
