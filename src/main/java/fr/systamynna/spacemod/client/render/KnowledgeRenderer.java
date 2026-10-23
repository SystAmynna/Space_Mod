package fr.systamynna.spacemod.client.render;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import fr.systamynna.spacemod.client.ClientSpace;
import fr.systamynna.spacemod.strategic.Archetypes;
import fr.systamynna.spacemod.strategic.RailMath;
import fr.systamynna.spacemod.strategic.SkyFrame;
import fr.systamynna.spacemod.strategic.SpaceObject;
import fr.systamynna.spacemod.strategic.component.ComponentTypes;
import fr.systamynna.spacemod.strategic.component.RailFollower;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

/**
 * Repères de l'état « connaissance de l'espace », dessinés dans le ciel après les astres : un anneau autour de chaque astre
 * du système (couleur selon sa nature), deux anneaux concentriques autour des étoiles des autres systèmes, les rails des
 * orbites en pointillés, et l'astre visé (son nom est affiché par {@code KnowledgeLabelEvents}). Les tailles sont en pixels,
 * converties en angles avec la projection, donc indépendantes de la résolution.
 * shortcut v1 : pas de découpe des pointillés derrière les anneaux, pas de fondu des petites orbites, rail de l'hôte non tracé.
 */
public final class KnowledgeRenderer {
    /** Demi-angle du cône de visée, en degrés : un astre à moins de cet angle du regard est « visé ». */
    private static final double LOOK_CONE_DEG = 5;
    private static final int RING_SEGMENTS = 64;
    private static final int STAR_RING_SEGMENTS = 16;
    private static final int RAIL_SAMPLES = 128;
    private static final double BODY_RING_FACTOR = 1.6;
    private static final double BODY_RING_MIN_PX = 10;
    private static final double MIN_RAIL_PX = 6;
    /** Hauteur de référence des tailles en pixels, comme dans l'écran 1080p. */
    private static final double REFERENCE_HEIGHT = 1080;

    /** Astre visé au dernier rendu : nom et distance (mètres), et l'instant (ns) du rendu pour détecter une valeur périmée. */
    public record Looked(String name, double distance, long nanoTime) {}

    /** Au-delà de ce délai (ns) sans rendu du ciel, l'astre visé est périmé (le joueur a quitté la dimension de l'espace). */
    private static final long STALE_NANOS = 500_000_000L;

    private static volatile Looked looked;

    /** Classe utilitaire. */
    private KnowledgeRenderer() {}

    /** Astre visé au dernier rendu du ciel, ou {@code null}. */
    public static @Nullable Looked looked() {
        Looked l = looked;
        return l != null && System.nanoTime() - l.nanoTime() < STALE_NANOS ? l : null;
    }

    /** Oublie l'astre visé (état désactivé, ciel absent). */
    public static void clear() {
        looked = null;
    }

    /**
     * Dessine les repères. Appelé par {@link SkyRenderer} dans son état de rendu (projection et vue du ciel déjà posées).
     *
     * @param bodies astres du ciel, avec leur direction dans le repère de la dimension
     */
    static void render(ClientSpace space, SpaceObject host, SkyFrame frame, Matrix4f view, float projScale, double t,
            List<SkyRenderer.Body> bodies) {
        double halfHeight = Math.max(1, Minecraft.getInstance().getWindow().getHeight()) / 2.0;
        SpaceObject hostSystem = systemOf(space, host);
        Vector3f look = Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.lineWidth(1.5f);
        BufferBuilder rings = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        double bestDot = Math.cos(Math.toRadians(LOOK_CONE_DEG));
        Looked best = null;
        for (SkyRenderer.Body b : bodies) {
            Kind kind = kind(space, b.object());
            if (kind == null) continue;
            Vector3f dir = new Vector3f((float) (b.view()[0] / b.distance()), (float) (b.view()[1] / b.distance()),
                    (float) (b.view()[2] / b.distance()));
            boolean otherSystem = kind == Kind.STAR && systemOf(space, b.object()) != hostSystem;
            if (otherSystem) {
                ring(rings, view, dir, angle(6, projScale, halfHeight), STAR_RING_SEGMENTS, kind, 0.6f);
                ring(rings, view, dir, angle(9, projScale, halfHeight), STAR_RING_SEGMENTS, kind, 0.6f);
            } else {
                double radius = Math.max(b.angularRadius(), angle(BODY_RING_MIN_PX, projScale, halfHeight)) * BODY_RING_FACTOR;
                ring(rings, view, dir, Math.min(radius, 1.4), RING_SEGMENTS, kind, 0.85f);
            }
            double dot = dir.dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = new Looked(b.object().get(ComponentTypes.IDENTITY).name(), b.distance(), System.nanoTime());
            }
        }
        looked = best;
        draw(rings);

        BufferBuilder rails = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (SkyRenderer.Body b : bodies) {
            if (systemOf(space, b.object()) != hostSystem || !(b.object().controller() instanceof RailFollower rail)
                    || rail.orbit() == null) {
                continue;
            }
            SpaceObject parent = b.object().parent() == null ? null : space.find(b.object().parent());
            if (parent == null || parent == host) continue;
            try {
                rail(rails, view, frame, space, host, parent, rail.orbit(), t, projScale, halfHeight);
            } catch (IllegalStateException e) {
                // objet hors de l'arbre connu du client : pas de rail
            }
        }
        draw(rails);

        RenderSystem.lineWidth(1f);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void draw(BufferBuilder buffer) {
        MeshData mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    /** Angle (radians) que couvre {@code px} pixels de l'écran de référence. */
    private static double angle(double px, float projScale, double halfHeight) {
        return Math.atan(px * (halfHeight / (REFERENCE_HEIGHT / 2)) / (projScale * halfHeight));
    }

    /** Rail en pointillés : l'orbite échantillonnée sur un tour, un segment sur deux. */
    private static void rail(BufferBuilder buffer, Matrix4f view, SkyFrame frame, ClientSpace space, SpaceObject host,
            SpaceObject parent, RailFollower.Orbit orbit, double t, float projScale, double halfHeight) {
        double[] center = RailMath.relativePosition(space, parent, host, t);
        double distance = Math.sqrt(center[0] * center[0] + center[1] * center[1] + center[2] * center[2]);
        // orbite trop petite à l'écran : rien à tracer
        double size = orbit.radius() / Math.max(distance, orbit.radius() * 0.01) * projScale * halfHeight;
        if (size < MIN_RAIL_PX) return;
        Vector3f previous = null;
        for (int k = 0; k <= RAIL_SAMPLES; k++) {
            double[] offset = RailMath.orbitOffset(orbit, t + (double) k * orbit.period() / RAIL_SAMPLES);
            double[] v = frame.view(new double[] { center[0] + offset[0], center[1] + offset[1], center[2] + offset[2] });
            double n = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            if (!(n > 0) || Double.isInfinite(n)) {
                previous = null;
                continue;
            }
            Vector3f dir = new Vector3f((float) (v[0] / n), (float) (v[1] / n), (float) (v[2] / n));
            // un segment sur deux, et pas de segment qui traverserait l'œil
            if (previous != null && k % 2 == 0 && previous.dot(dir) > -0.9995f) {
                vertex(buffer, view, previous, 0.8f, 0.8f, 0.9f, 0.4f);
                vertex(buffer, view, dir, 0.8f, 0.8f, 0.9f, 0.4f);
            }
            previous = dir;
        }
    }

    /** Cercle d'ouverture angulaire {@code radius} autour de {@code dir}, en segments de droite. */
    private static void ring(BufferBuilder buffer, Matrix4f view, Vector3f dir, double radius, int segments, Kind kind, float alpha) {
        Vector3f u = (Math.abs(dir.y) < 0.99f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).cross(dir).normalize();
        Vector3f v = new Vector3f(dir).cross(u);
        float c = (float) Math.cos(radius);
        float s = (float) Math.sin(radius);
        Vector3f first = null;
        Vector3f previous = null;
        for (int i = 0; i <= segments; i++) {
            double a = 2 * Math.PI * i / segments;
            Vector3f p = new Vector3f(dir).mul(c).fma((float) Math.cos(a) * s, u).fma((float) Math.sin(a) * s, v);
            if (previous != null) {
                vertex(buffer, view, previous, kind.r, kind.g, kind.b, alpha);
                vertex(buffer, view, p, kind.r, kind.g, kind.b, alpha);
            }
            if (first == null) first = p;
            previous = p;
        }
    }

    private static void vertex(BufferBuilder buffer, Matrix4f view, Vector3f dir, float r, float g, float b, float a) {
        buffer.addVertex(view, dir.x * RenderContext.SKY_DISTANCE, dir.y * RenderContext.SKY_DISTANCE,
                dir.z * RenderContext.SKY_DISTANCE).setColor(r, g, b, a);
    }

    private enum Kind {
        STAR(1f, 0.85f, 0.3f), PLANET(0.3f, 0.8f, 1f), MOON(0.75f, 0.75f, 0.75f), SHIP(0.4f, 1f, 0.5f);

        final float r;
        final float g;
        final float b;

        Kind(float r, float g, float b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    /** Nature d'un astre, ou {@code null} s'il n'a pas de repère (galaxie, nuage, mégastructure…). */
    private static @Nullable Kind kind(ClientSpace space, SpaceObject o) {
        String archetype = o.get(ComponentTypes.IDENTITY).archetype();
        return switch (archetype) {
            case Archetypes.STAR -> Kind.STAR;
            case Archetypes.SHIP, Archetypes.SHUTTLE -> Kind.SHIP;
            case Archetypes.PLANET -> {
                SpaceObject parent = o.parent() == null ? null : space.find(o.parent());
                yield parent != null && Archetypes.PLANET.equals(parent.get(ComponentTypes.IDENTITY).archetype()) ? Kind.MOON : Kind.PLANET;
            }
            default -> null;
        };
    }

    /** Système stellaire de l'objet (lui compris), ou {@code null}. */
    private static @Nullable SpaceObject systemOf(ClientSpace space, SpaceObject o) {
        for (int depth = 0; o != null && depth < 64; depth++) {
            if (o.has(ComponentTypes.STELLAR_SYSTEM)) return o;
            o = o.parent() == null ? null : space.find(o.parent());
        }
        return null;
    }
}
