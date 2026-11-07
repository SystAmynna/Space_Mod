package fr.systamynna.spacemod.core.render;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;

/** Sphère apparente monochrome d'un trou noir. Observateur à l'intérieur : tout le ciel prend sa couleur. Voir {@link DistorsionRender} pour la lentille. */
public final class BlackHoleRender implements SpaceRenderObject {
    public static final String TYPE = "black_hole";
    public static final Rgba DEFAULT_COLOR = new Rgba(0, 0, 0, 1);

    private final RenderTransform transform;
    private final double radius;
    public final Rgba color;

    public BlackHoleRender(RenderTransform transform, double radius, Rgba color) {
        this.transform = transform;
        this.radius = radius;
        this.color = color;
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
        t.put("color", color.save());
        return t;
    }

    @Override public String describe() { return "black_hole radius=" + radius + ", color=" + color.describe() + ", " + transform.describe(); }

    @Nullable
    public static BlackHoleRender load(CompoundTag t) {
        double r = t.getDouble("radius");
        return r > 0 && Double.isFinite(r) ? new BlackHoleRender(RenderTransform.load(t.getCompound("transform")), r, Rgba.load(t, "color", DEFAULT_COLOR)) : null;
    }
}
