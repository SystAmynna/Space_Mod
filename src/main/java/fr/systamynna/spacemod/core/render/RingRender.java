package fr.systamynna.spacemod.core.render;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;

/**
 * Anneau plat (plan y=0 local) : anneau de planète ou disque d'accrétion. {@code radius} = rayon extérieur, {@code inner} = rayon intérieur (km) ;
 * dégradé {@code colorInner} → {@code colorOuter} ; {@code emissive} = brille (intensité de l'alpha des couleurs) au lieu d'une teinte constante ;
 * {@code occluder} = rayon (km) de la sphère de l'astre central, qui cache la partie arrière (0 = aucun) ;
 * {@code thickness} = demi-épaisseur (km) : l'anneau est un volume (dalle |y| ≤ thickness), pas un disque 2D.
 */
public final class RingRender implements SpaceRenderObject {
    public static final String TYPE = "ring";
    /** Demi-épaisseur par défaut, en fraction du rayon extérieur. */
    public static final double DEFAULT_THICKNESS = 0.005;
    public static final Rgba DEFAULT_INNER = new Rgba(0.85, 0.75, 0.6, 1), DEFAULT_OUTER = new Rgba(0.6, 0.55, 0.45, 1);

    private final RenderTransform transform;
    private final double radius;
    public final double inner, opacity, occluder, thickness;
    public final boolean emissive;
    public final Rgba colorInner, colorOuter;

    public RingRender(RenderTransform transform, double radius, double inner, Rgba colorInner, Rgba colorOuter, double opacity, boolean emissive, double occluder, double thickness) {
        this.transform = transform;
        this.radius = radius;
        this.inner = inner;
        this.colorInner = colorInner;
        this.colorOuter = colorOuter;
        this.opacity = opacity;
        this.emissive = emissive;
        this.occluder = occluder;
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
        t.putDouble("inner", inner);
        t.put("colorInner", colorInner.save());
        t.put("colorOuter", colorOuter.save());
        t.putDouble("opacity", opacity);
        t.putBoolean("emissive", emissive);
        t.putDouble("occluder", occluder);
        t.putDouble("thickness", thickness);
        return t;
    }

    @Override
    public String describe() {
        return "ring inner=" + inner + ", outer=" + radius + ", colorInner=" + colorInner.describe() + ", colorOuter=" + colorOuter.describe() + ", opacity=" + opacity
                + ", emissive=" + emissive + ", occluder=" + occluder + ", thickness=" + thickness + ", " + transform.describe();
    }

    @Nullable
    public static RingRender load(CompoundTag t) {
        double r = t.getDouble("radius"), i = t.getDouble("inner"), o = t.contains("opacity") ? t.getDouble("opacity") : 1, oc = t.getDouble("occluder"), th = t.contains("thickness") ? t.getDouble("thickness") : DEFAULT_THICKNESS * r;
        return valid(r, i, o, oc, th) ? new RingRender(RenderTransform.load(t.getCompound("transform")), r, i, Rgba.load(t, "colorInner", DEFAULT_INNER), Rgba.load(t, "colorOuter", DEFAULT_OUTER),
                o, t.getBoolean("emissive"), oc, th) : null;
    }

    public static boolean valid(double outer, double inner, double opacity, double occluder, double thickness) {
        return Double.isFinite(outer) && inner > 0 && inner < outer && opacity > 0 && opacity <= 1 && occluder >= 0 && occluder < outer && thickness > 0 && thickness < outer;
    }
}
