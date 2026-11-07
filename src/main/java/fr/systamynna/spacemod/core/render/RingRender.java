package fr.systamynna.spacemod.core.render;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;

/**
 * Anneau plat (plan y=0 local) : anneau de planète ou disque d'accrétion. {@code radius} = rayon extérieur, {@code inner} = rayon intérieur (km) ;
 * dégradé {@code colorInner} → {@code colorOuter} ; {@code emissive} = brille (intensité de l'alpha des couleurs) au lieu d'une teinte constante ;
 * {@code occluder} = rayon (km) de la sphère de l'astre central, qui cache la partie arrière (0 = aucun).
 */
public final class RingRender implements SpaceRenderObject {
    public static final String TYPE = "ring";
    public static final Rgba DEFAULT_INNER = new Rgba(0.85, 0.75, 0.6, 1), DEFAULT_OUTER = new Rgba(0.6, 0.55, 0.45, 1);

    private final RenderTransform transform;
    private final double radius;
    public final double inner, opacity, occluder;
    public final boolean emissive;
    public final Rgba colorInner, colorOuter;

    public RingRender(RenderTransform transform, double radius, double inner, Rgba colorInner, Rgba colorOuter, double opacity, boolean emissive, double occluder) {
        this.transform = transform;
        this.radius = radius;
        this.inner = inner;
        this.colorInner = colorInner;
        this.colorOuter = colorOuter;
        this.opacity = opacity;
        this.emissive = emissive;
        this.occluder = occluder;
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
        t.put("colorInner", colorInner.save());
        t.put("colorOuter", colorOuter.save());
        t.putDouble("opacity", opacity);
        t.putBoolean("emissive", emissive);
        t.putDouble("occluder", occluder);
        return t;
    }

    @Override
    public String describe() {
        return "ring inner=" + inner + ", outer=" + radius + ", colorInner=" + colorInner.describe() + ", colorOuter=" + colorOuter.describe() + ", opacity=" + opacity
                + ", emissive=" + emissive + ", occluder=" + occluder + ", " + transform.describe();
    }

    @Nullable
    public static RingRender load(CompoundTag t) {
        double r = t.getDouble("radius"), i = t.getDouble("inner"), o = t.contains("opacity") ? t.getDouble("opacity") : 1, oc = t.getDouble("occluder");
        return valid(r, i, o, oc) ? new RingRender(RenderTransform.load(t.getCompound("transform")), r, i, Rgba.load(t, "colorInner", DEFAULT_INNER), Rgba.load(t, "colorOuter", DEFAULT_OUTER),
                o, t.getBoolean("emissive"), oc) : null;
    }

    public static boolean valid(double outer, double inner, double opacity, double occluder) {
        return Double.isFinite(outer) && inner > 0 && inner < outer && opacity > 0 && opacity <= 1 && occluder >= 0 && occluder < outer;
    }
}
