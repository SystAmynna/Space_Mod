package fr.systamynna.spacemod.client.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.client.ClientKnowledgeState;
import fr.systamynna.spacemod.client.ClientSpace;
import fr.systamynna.spacemod.client.SkyContext;
import fr.systamynna.spacemod.network.SkyContextPacket;
import fr.systamynna.spacemod.strategic.RailMath;
import fr.systamynna.spacemod.strategic.SkyFrame;
import fr.systamynna.spacemod.strategic.SpaceObject;
import fr.systamynna.spacemod.strategic.component.ComponentTypes;
import fr.systamynna.spacemod.strategic.component.HostedDimension;
import fr.systamynna.spacemod.strategic.component.Render;
import fr.systamynna.spacemod.strategic.component.Transform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;

/**
 * Ciel « système » : dessine les objets du système de l'hôte, du plus loin au plus proche, chacun par sa pile de
 * {@link RenderObject}. Position relative en {@code double} ({@link RailMath#relativePosition}), ramenée dans le repère
 * de la dimension par la rotation de l'hôte et celle du port ; le {@code float} n'apparaît qu'à la fin.
 */
public final class SkyRenderer {
    /** Identifiant de ce rendu de ciel, à poser sur {@code HostedDimension.render}. */
    public static final ResourceLocation ID = HostedDimension.DEFAULT_RENDER;
    private static final int GL_DEPTH_BUFFER_BIT = 256;

    private static ClientSpace cachedFor;
    private static final Map<UUID, List<RenderObject>> LAYERS = new ConcurrentHashMap<>();
    private static final Set<UUID> FAILED = new HashSet<>();

    record Body(SpaceObject object, double[] view, double distance, float angularRadius) {}

    /** Classe utilitaire. */
    private SkyRenderer() {}

    /** Dessine le ciel si le contexte reçu le demande ; sans snapshot ou avec un snapshot périmé, ne dessine rien. */
    public static void render(ClientLevel level, Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick) {
        SkyContextPacket sky = SkyContext.get();
        ClientSpace space = ClientSpace.get();
        if (sky == null || space == null || !ID.equals(sky.sky()) || !sky.objectId().equals(space.hostId())) return;
        SpaceObject host = space.host();
        if (host == null) return;
        if (space != cachedFor) {
            LAYERS.clear();
            FAILED.clear();
            cachedFor = space;
        }
        space.extrapolate(level.getGameTime() + partialTick);
        double t = space.spaceTime(level.getGameTime() + partialTick);

        // repère de la dimension : rotation propre de l'hôte, composée avec celle du placement et du port (voir SkyFrame)
        SkyFrame frame = SkyFrame.of(RailMath.local(host, t), new double[] { sky.qx(), sky.qy(), sky.qz(), sky.qw() },
                new double[] { sky.x(), sky.y(), sky.z() });
        Transform toDimension = frame.toDimension();
        double[] q = frame.q();

        List<Body> bodies = new ArrayList<>();
        for (SpaceObject o : space.renderables()) {
            Render render = o.get(ComponentTypes.RENDER);
            try {
                double[] rel = RailMath.relativePosition(space, o, host, t);
                double[] v = frame.view(rel);
                double dist = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
                if (!(dist > 0) || Double.isInfinite(dist) || render.apparentRadius() <= 0) continue;
                float ang = (float) Math.asin(Math.min(1, render.apparentRadius() / dist));
                if (ang > 0) bodies.add(new Body(o, v, dist, ang));
            } catch (IllegalStateException e) {
                if (FAILED.add(o.id())) AmynnaSpaceMod.LOGGER.warn("Objet {} ignoré dans le ciel : {}", o.id(), e.getMessage());
            }
        }
        bodies.sort(Comparator.comparingDouble(Body::distance).reversed());

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.getModelViewStack().pushMatrix().identity();
        RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515); // GL_LEQUAL
            RenderSystem.depthMask(true);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            float projScale = projectionMatrix.m11();
            Vector3f[] galactic = null;
            SpaceObject galaxy = space.galaxy();
            for (Body b : bodies) {
                if (b.object() == galaxy) galactic = Galactic.basis(context(space, b, toDimension, q, frustumMatrix, projScale, t, partialTick));
            }
            SkyBackground.render(space.seed(), frustumMatrix, galactic,
                    new Quaternionf((float) -q[0], (float) -q[1], (float) -q[2], (float) q[3]));
            for (Body b : bodies) {
                // la profondeur ne sert qu'à empiler les couches d'un même objet : l'ordre entre objets est celui du tri
                RenderSystem.clear(GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
                RenderContext ctx = context(space, b, toDimension, q, frustumMatrix, projScale, t, partialTick);
                for (RenderObject layer : layers(b.object())) layer.render(ctx);
            }
            RenderSystem.clear(GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            if (ClientKnowledgeState.on()) KnowledgeRenderer.render(space, host, frame, frustumMatrix, projScale, t, bodies);
            else KnowledgeRenderer.clear();
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private static RenderContext context(ClientSpace space, Body b, Transform toDimension, double[] q, Matrix4f view,
            float projScale, double t, float partialTick) {
        Vector3f direction = new Vector3f((float) (b.view()[0] / b.distance()), (float) (b.view()[1] / b.distance()),
                (float) (b.view()[2] / b.distance()));
        Transform local = RailMath.local(b.object(), t);
        double[] body = SkyFrame.normalize(SkyFrame.mul(SkyFrame.conjugate(q), new double[] { local.qx(), local.qy(), local.qz(), local.qw() }));
        Quaternionf rotation = new Quaternionf((float) body[0], (float) body[1], (float) body[2], (float) body[3]);
        Vector3f light = null;
        SpaceObject star = space.mainStar();
        if (star != null && star != b.object()) {
            try {
                double[] s = toDimension.invert(RailMath.relativePosition(space, star, b.object(), t));
                double n = Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
                if (n > 0 && !Double.isInfinite(n)) light = new Vector3f((float) (s[0] / n), (float) (s[1] / n), (float) (s[2] / n));
            } catch (IllegalStateException e) {
                // pas de lumière pour cet objet
            }
        }
        float size = (float) Math.tan(Math.min(b.angularRadius(), Math.toRadians(89))) * projScale;
        return new RenderContext(view, direction, b.angularRadius(), size, rotation, light, partialTick);
    }

    private static List<RenderObject> layers(SpaceObject o) {
        return LAYERS.computeIfAbsent(o.id(), id -> {
            List<RenderObject> list = new ArrayList<>();
            for (Render.Layer layer : o.get(ComponentTypes.RENDER).layers()) {
                RenderObject r = RenderObjects.create(layer);
                if (r != null) list.add(r);
            }
            return list;
        });
    }
}
