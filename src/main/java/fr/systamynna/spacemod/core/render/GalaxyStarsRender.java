package fr.systamynna.spacemod.core.render;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;

/**
 * Étoiles aléatoires d'une galaxie, dessinées par un shader sans ray-marching (une décision d'existence par étoile, jamais par pixel). Utilisable
 * avec ou sans le nuage ({@link GalaxyCloudRender}). Leur densité suit le disque spiral (mêmes {@code thickness} et {@code arms} que le nuage).
 * La graine vient de l'UUID du SpaceObject porteur.
 */
public final class GalaxyStarsRender implements SpaceRenderObject {
    public static final String TYPE = "galaxy_stars";

    private final RenderTransform transform;
    private final double radius;
    /** Demi-épaisseur du disque, en fraction du rayon. */
    public final double thickness;
    public final int arms;
    /** Multiplicateur du nombre d'étoiles (1 = densité de référence). */
    public final double density;
    public static final double MAX_DENSITY = 20;

    public GalaxyStarsRender(RenderTransform transform, double radius, int arms, double thickness, double density) {
        this.transform = transform;
        this.radius = radius;
        this.arms = arms;
        this.thickness = thickness;
        this.density = density;
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
        t.putDouble("density", density);
        return t;
    }

    @Override public String describe() { return "galaxy_stars radius=" + radius + ", arms=" + arms + ", thickness=" + thickness + ", density=" + density + ", " + transform.describe(); }

    /** {@code null} si les données sont invalides (rayon ≤ 0, bras hors [1, {@link #MAX_ARMS}], épaisseur hors ]0, 1]). */
    @Nullable
    public static GalaxyStarsRender load(CompoundTag t) {
        double r = t.getDouble("radius"), h = t.getDouble("thickness"), d = t.contains("density") ? t.getDouble("density") : 1;
        int a = t.getInt("arms");
        return valid(r, a, h, d) ? new GalaxyStarsRender(RenderTransform.load(t.getCompound("transform")), r, a, h, d) : null;
    }

    public static boolean valid(double radius, int arms, double thickness, double density) {
        return GalaxyCloudRender.valid(radius, arms, thickness) && density > 0 && density <= MAX_DENSITY;
    }
}
