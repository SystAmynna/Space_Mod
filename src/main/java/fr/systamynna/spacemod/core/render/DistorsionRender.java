package fr.systamynna.spacemod.core.render;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;

/**
 * Trou noir / trou blanc : ombre de l'horizon et lentille gravitationnelle sur tout ce qui est dessiné derrière (anneau d'Einstein), y compris son
 * disque d'accrétion ({@link RingRender}) ; observateur qui plonge : l'univers se replie en bulle derrière lui puis s'éteint.
 * {@code radius} = rayon de l'horizon ; {@code strength} = rayon d'Einstein en rayons apparents, signé : {@code > 0} trou noir (ombre noire),
 * {@code < 0} trou blanc (déformation inverse, ombre blanche) ; {@code extent} = portée de la déformation en rayons apparents.
 */
public final class DistorsionRender implements SpaceRenderObject {
    public static final String TYPE = "distorsion";
    public static final double DEFAULT_STRENGTH = 2.6, DEFAULT_EXTENT = 10, MAX_STRENGTH = 10, MAX_EXTENT = 100;

    private final RenderTransform transform;
    private final double radius;
    public final double strength, extent;

    public DistorsionRender(RenderTransform transform, double radius, double strength, double extent) {
        this.transform = transform;
        this.radius = radius;
        this.strength = strength;
        this.extent = extent;
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
        t.putDouble("strength", strength);
        t.putDouble("extent", extent);
        return t;
    }

    @Override public String describe() { return "distorsion radius=" + radius + ", strength=" + strength + ", extent=" + extent + ", " + transform.describe(); }

    @Nullable
    public static DistorsionRender load(CompoundTag t) {
        double r = t.getDouble("radius"), s = t.contains("strength") ? t.getDouble("strength") : DEFAULT_STRENGTH, e = t.contains("extent") ? t.getDouble("extent") : DEFAULT_EXTENT;
        return valid(r, s, e) ? new DistorsionRender(RenderTransform.load(t.getCompound("transform")), r, s, e) : null;
    }

    public static boolean valid(double radius, double strength, double extent) {
        return radius > 0 && Double.isFinite(radius) && strength != 0 && Math.abs(strength) <= MAX_STRENGTH && extent > 1 && extent <= MAX_EXTENT;
    }
}
