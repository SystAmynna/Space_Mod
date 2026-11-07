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

    public GalaxyCloudRender(RenderTransform transform, double radius, int arms, double thickness) {
        this.transform = transform;
        this.radius = radius;
        this.arms = arms;
        this.thickness = thickness;
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
        return t;
    }

    @Override public String describe() { return "galaxy_cloud radius=" + radius + ", arms=" + arms + ", thickness=" + thickness + ", " + transform.describe(); }

    /** {@code null} si les données sont invalides (rayon ≤ 0, bras hors [1, {@link #MAX_ARMS}], épaisseur hors ]0, 1]). */
    @Nullable
    public static GalaxyCloudRender load(CompoundTag t) {
        double r = t.getDouble("radius"), h = t.getDouble("thickness");
        int a = t.getInt("arms");
        return valid(r, a, h) ? new GalaxyCloudRender(RenderTransform.load(t.getCompound("transform")), r, a, h) : null;
    }

    public static boolean valid(double radius, int arms, double thickness) {
        return radius > 0 && Double.isFinite(radius) && arms >= 1 && arms <= MAX_ARMS && thickness > 0 && thickness <= 1;
    }
}
