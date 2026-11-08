package fr.systamynna.spacemod.core.render;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;

/**
 * Ombrage d'un corps sphérique de rayon {@code radius} : la face tournée vers les étoiles (jusqu'à 4, voir {@code SpaceRenderer}) reste éclairée, l'autre
 * s'assombrit jusqu'à {@code ambient} ; {@code softness} = largeur du terminateur (en cosinus). Se dessine par-dessus la planète du même porteur.
 */
public final class ShadowRender implements SpaceRenderObject {
    public static final String TYPE = "shadow";
    public static final double DEFAULT_AMBIENT = 0.02, DEFAULT_SOFTNESS = 0.1;

    private final RenderTransform transform;
    private final double radius;
    public final double ambient, softness;

    public ShadowRender(RenderTransform transform, double radius, double ambient, double softness) {
        this.transform = transform;
        this.radius = radius;
        this.ambient = ambient;
        this.softness = softness;
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
        t.putDouble("ambient", ambient);
        t.putDouble("softness", softness);
        return t;
    }

    @Override public String describe() { return "shadow radius=" + radius + ", ambient=" + ambient + ", softness=" + softness + ", " + transform.describe(); }

    @Nullable
    public static ShadowRender load(CompoundTag t) {
        double r = t.getDouble("radius"), a = t.contains("ambient") ? t.getDouble("ambient") : DEFAULT_AMBIENT, s = t.contains("softness") ? t.getDouble("softness") : DEFAULT_SOFTNESS;
        return valid(r, a, s) ? new ShadowRender(RenderTransform.load(t.getCompound("transform")), r, a, s) : null;
    }

    public static boolean valid(double radius, double ambient, double softness) {
        return radius > 0 && Double.isFinite(radius) && ambient >= 0 && ambient <= 1 && softness > 0 && softness <= 1;
    }
}
