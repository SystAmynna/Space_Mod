package fr.systamynna.spacemod.core.render;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;

/**
 * Étoile lumineuse dessinée par un shader (aucune texture) : de loin un disque éblouissant à bloom et fines stries, de près (selon sa taille apparente)
 * une surface de plasma animée (granules) émissive.
 */
public final class StarRender implements SpaceRenderObject {
    public static final String TYPE = "star";
    public static final double MAX_SPEED = 10, MAX_GRANULATION = 10, MAX_EMISSIVE = 10, DEFAULT_EMISSIVE = 1.5;
    public static final Rgba DEFAULT_HOT = new Rgba(1.0, 0.86, 0.45, 1), DEFAULT_COOL = new Rgba(1.0, 0.40, 0.05, 1);

    private final RenderTransform transform;
    private final double radius;
    /** Couleur des zones chaudes (granules) et des zones froides ; l'alpha est un multiplicateur d'intensité. */
    public final Rgba hot, cool;
    /** Vitesse d'animation du plasma (0 = figé) et nombre de granules (0 = surface lisse). */
    public final double speed, granulation;
    /** Intensité du plasma vu de près (multiplicateur, 0 = plasma sans rayonnement). */
    public final double emissive;

    public StarRender(RenderTransform transform, double radius, Rgba hot, Rgba cool, double speed, double granulation, double emissive) {
        this.transform = transform;
        this.radius = radius;
        this.hot = hot;
        this.cool = cool;
        this.speed = speed;
        this.granulation = granulation;
        this.emissive = emissive;
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
        t.put("hot", hot.save());
        t.put("cool", cool.save());
        t.putDouble("speed", speed);
        t.putDouble("granulation", granulation);
        t.putDouble("emissive", emissive);
        return t;
    }

    @Override
    public String describe() { return "star radius=" + radius + ", hot=" + hot.describe() + ", cool=" + cool.describe() + ", speed=" + speed + ", granulation=" + granulation + ", emissive=" + emissive + ", " + transform.describe(); }

    /** {@code null} si les données sont invalides. */
    @Nullable
    public static StarRender load(CompoundTag t) {
        double r = t.getDouble("radius"), s = t.contains("speed") ? t.getDouble("speed") : 1, g = t.contains("granulation") ? t.getDouble("granulation") : 1,
                e = t.contains("emissive") ? t.getDouble("emissive") : DEFAULT_EMISSIVE;
        Rgba hot = Rgba.load(t, "hot", DEFAULT_HOT);
        return valid(r, s, g, e) ? new StarRender(RenderTransform.load(t.getCompound("transform")), r, hot, Rgba.load(t, "cool", coolFor(hot)), s, g, e) : null;
    }

    /** Couleur froide par défaut d'une étoile de couleur chaude {@code hot} : celle d'origine pour la teinte par défaut, sinon la même teinte assombrie (la surface suit la teinte de l'étoile). */
    public static Rgba coolFor(Rgba hot) { return hot.equals(DEFAULT_HOT) ? DEFAULT_COOL : hot.cooler(); }

    public static boolean valid(double radius, double speed, double granulation, double emissive) {
        return radius > 0 && Double.isFinite(radius) && speed >= 0 && speed <= MAX_SPEED && granulation >= 0 && granulation <= MAX_GRANULATION
                && emissive >= 0 && emissive <= MAX_EMISSIVE;
    }
}
