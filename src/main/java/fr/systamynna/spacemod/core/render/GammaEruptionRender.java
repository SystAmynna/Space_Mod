package fr.systamynna.spacemod.core.render;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;

/**
 * Deux jets de matière incandescente aux pôles (axe Y local, à orienter avec la rotation du transform), animés par un shader.
 * {@code radius} = longueur d'un jet depuis le centre ; {@code width} = demi-largeur à la pointe en fraction de la longueur ;
 * dégradé {@code colorA} (base) → {@code colorB} (pointe) ; {@code seed} s'ajoute à la graine tirée de l'UUID du porteur.
 * Pulsar : {@code spin} = rotation (rad/s) autour de l'axe Y local, {@code tilt} = inclinaison (°) des jets sur cet axe (effet phare), {@code curve} = courbure en spirale des jets [0, 1] ; tout à 0 = jets droits fixes.
 */
public final class GammaEruptionRender implements SpaceRenderObject {
    public static final String TYPE = "gamma_eruption";
    public static final double MAX_SPEED = 20, MAX_SPIN = 5000; // pulsar le plus rapide connu ≈ 4 500 rad/s
    public static final Rgba DEFAULT_A = new Rgba(0.75, 0.85, 1.0, 1.5), DEFAULT_B = new Rgba(0.35, 0.25, 1.0, 1);

    private final RenderTransform transform;
    private final double radius;
    public final double width, speed, spin, tilt, curve;
    public final int seed;
    public final Rgba colorA, colorB;

    public GammaEruptionRender(RenderTransform transform, double radius, double width, Rgba colorA, Rgba colorB, double speed, int seed, double spin, double tilt, double curve) {
        this.transform = transform;
        this.radius = radius;
        this.width = width;
        this.colorA = colorA;
        this.colorB = colorB;
        this.speed = speed;
        this.seed = seed;
        this.spin = spin;
        this.tilt = tilt;
        this.curve = curve;
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
        t.putDouble("width", width);
        t.put("colorA", colorA.save());
        t.put("colorB", colorB.save());
        t.putDouble("speed", speed);
        t.putInt("seed", seed);
        t.putDouble("spin", spin);
        t.putDouble("tilt", tilt);
        t.putDouble("curve", curve);
        return t;
    }

    @Override
    public String describe() { return "gamma_eruption length=" + radius + ", width=" + width + ", colorA=" + colorA.describe() + ", colorB=" + colorB.describe() + ", speed=" + speed + ", seed=" + seed + ", spin=" + spin + ", tilt=" + tilt + ", curve=" + curve + ", " + transform.describe(); }

    @Nullable
    public static GammaEruptionRender load(CompoundTag t) {
        double r = t.getDouble("radius"), w = t.contains("width") ? t.getDouble("width") : 0.05, s = t.contains("speed") ? t.getDouble("speed") : 1, sp = t.getDouble("spin"), ti = t.getDouble("tilt"), cu = t.getDouble("curve");
        return valid(r, w, s, sp, ti, cu) ? new GammaEruptionRender(RenderTransform.load(t.getCompound("transform")), r, w, Rgba.load(t, "colorA", DEFAULT_A), Rgba.load(t, "colorB", DEFAULT_B), s, t.getInt("seed"), sp, ti, cu) : null;
    }

    public static boolean valid(double radius, double width, double speed, double spin, double tilt, double curve) {
        return radius > 0 && Double.isFinite(radius) && width > 0 && width <= 1 && speed >= 0 && speed <= MAX_SPEED
                && Math.abs(spin) <= MAX_SPIN && tilt >= 0 && tilt <= 90 && curve >= 0 && curve <= 1; // NaN échoue toutes les comparaisons
    }
}
