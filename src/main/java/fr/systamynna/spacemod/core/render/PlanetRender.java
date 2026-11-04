package fr.systamynna.spacemod.core.render;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;

/** Planète : sphère texturée. */
public final class PlanetRender implements SpaceRenderObject {
    public static final String TYPE = "planet";

    private final RenderTransform transform;
    public final String texture;
    private final double radius;

    public PlanetRender(RenderTransform transform, String texture, double radius) {
        this.transform = transform;
        this.texture = texture;
        this.radius = radius;
    }

    @Override public String type() { return TYPE; }

    @Override public RenderTransform transform() { return transform; }

    @Override public double radius() { return radius; }

    @Override public List<String> assets() { return List.of(texture); }

    @Override
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("type", TYPE);
        t.put("transform", transform.save());
        t.putString("texture", texture);
        t.putDouble("radius", radius);
        return t;
    }

    @Override public String describe() { return "planet texture=" + texture + ", radius=" + radius + ", " + transform.describe(); }

    /** {@code null} si les données sont invalides (rayon ≤ 0 ou non fini). */
    @Nullable
    public static PlanetRender load(CompoundTag t) {
        double r = t.getDouble("radius");
        if (!(r > 0) || !Double.isFinite(r)) return null;
        return new PlanetRender(RenderTransform.load(t.getCompound("transform")), t.getString("texture"), r);
    }
}
