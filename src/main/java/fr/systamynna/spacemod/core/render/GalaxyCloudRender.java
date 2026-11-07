package fr.systamynna.spacemod.core.render;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;

/**
 * Nuage d'une galaxie : volume (disque spiral, bulbe) dessiné par un shader à ray-marching. Les étoiles aléatoires sont un autre élément de rendu
 * ({@link GalaxyStarsRender}), utilisable avec ou sans lui. La graine du dessin n'est pas stockée : elle vient de l'UUID du SpaceObject porteur,
 * donc survit à la réplication.
 */
public final class GalaxyCloudRender implements SpaceRenderObject {
    public static final String TYPE = "galaxy_cloud";
    public static final int MAX_ARMS = 8;

    private final RenderTransform transform;
    private final double radius;
    /** Demi-épaisseur du disque, en fraction du rayon. */
    public final double thickness;
    public final int arms;
    /** Dégradé de couleur du nuage : du centre (bulbe) au bord (bras). L'alpha est un multiplicateur d'intensité. */
    public final Rgba core, edge;

    public static final Rgba DEFAULT_CORE = new Rgba(1.0, 0.72, 0.42, 1), DEFAULT_EDGE = new Rgba(0.50, 0.62, 1.0, 1);

    public GalaxyCloudRender(RenderTransform transform, double radius, int arms, double thickness) { this(transform, radius, arms, thickness, DEFAULT_CORE, DEFAULT_EDGE); }

    public GalaxyCloudRender(RenderTransform transform, double radius, int arms, double thickness, Rgba core, Rgba edge) {
        this.transform = transform;
        this.radius = radius;
        this.arms = arms;
        this.thickness = thickness;
        this.core = core;
        this.edge = edge;
    }

    @Override public String type() { return TYPE; }

    @Override public RenderTransform transform() { return transform; }

    @Override public double radius() { return radius; }

    @Override public List<String> assets() { return List.of(); }

    @Override
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("type", TYPE);
        t.put("transform", transform.save());
        t.putDouble("radius", radius);
        t.putInt("arms", arms);
        t.putDouble("thickness", thickness);
        t.put("core", core.save());
        t.put("edge", edge.save());
        return t;
    }

    @Override public String describe() { return "galaxy_cloud radius=" + radius + ", arms=" + arms + ", thickness=" + thickness + ", core=" + core.describe() + ", edge=" + edge.describe() + ", " + transform.describe(); }

    /** {@code null} si les données sont invalides (rayon ≤ 0, bras hors [1, {@link #MAX_ARMS}], épaisseur hors ]0, 1]). */
    @Nullable
    public static GalaxyCloudRender load(CompoundTag t) {
        double r = t.getDouble("radius"), h = t.getDouble("thickness");
        int a = t.getInt("arms");
        return valid(r, a, h) ? new GalaxyCloudRender(RenderTransform.load(t.getCompound("transform")), r, a, h,
                Rgba.load(t, "core", DEFAULT_CORE), Rgba.load(t, "edge", DEFAULT_EDGE)) : null;
    }

    public static boolean valid(double radius, int arms, double thickness) {
        return radius > 0 && Double.isFinite(radius) && arms >= 1 && arms <= MAX_ARMS && thickness > 0 && thickness <= 1;
    }
}
