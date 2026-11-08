package fr.systamynna.spacemod.core.render;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;

/**
 * Atmosphère : coquille diffusante entre la surface ({@code inner}, km) et {@code radius} (km), éclairée par les étoiles du ciel (voir {@code SpaceRenderer}).
 * {@code day} = couleur de la diffusion sous une étoile haute, {@code sunset} = couleur au terminateur / en contre-jour ; l'alpha des couleurs est une intensité.
 * {@code density} multiplie l'épaisseur optique (0 exclu).
 */
public final class AtmosphereRender implements SpaceRenderObject {
    public static final String TYPE = "atmosphere";
    public static final double MAX_DENSITY = 10;
    public static final Rgba DEFAULT_DAY = new Rgba(0.30, 0.55, 1.0, 1), DEFAULT_SUNSET = new Rgba(1.0, 0.42, 0.16, 1);

    private final RenderTransform transform;
    private final double radius;
    public final double inner, density;
    public final Rgba day, sunset;

    public AtmosphereRender(RenderTransform transform, double radius, double inner, Rgba day, Rgba sunset, double density) {
        this.transform = transform;
        this.radius = radius;
        this.inner = inner;
        this.day = day;
        this.sunset = sunset;
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
        t.putDouble("inner", inner);
        t.put("day", day.save());
        t.put("sunset", sunset.save());
        t.putDouble("density", density);
        return t;
    }

    @Override
    public String describe() {
        return "atmosphere radius=" + radius + ", inner=" + inner + ", day=" + day.describe() + ", sunset=" + sunset.describe() + ", density=" + density + ", " + transform.describe();
    }

    @Nullable
    public static AtmosphereRender load(CompoundTag t) {
        double r = t.getDouble("radius"), i = t.getDouble("inner"), d = t.contains("density") ? t.getDouble("density") : 1;
        return valid(r, i, d) ? new AtmosphereRender(RenderTransform.load(t.getCompound("transform")), r, i, Rgba.load(t, "day", DEFAULT_DAY), Rgba.load(t, "sunset", DEFAULT_SUNSET), d) : null;
    }

    public static boolean valid(double radius, double inner, double density) {
        return Double.isFinite(radius) && inner > 0 && inner < radius && density > 0 && density <= MAX_DENSITY;
    }
}
