package fr.systamynna.spacemod.core.render;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;

/**
 * Disque d'accrétion (plan y=0 local) : dalle de gaz et de nuages procéduraux (graine = UUID du porteur), toujours émissive, en rotation képlérienne.
 * {@code radius} = rayon extérieur, {@code inner} = rayon intérieur (km) ; dégradé {@code colorInner} → {@code colorOuter} (alpha = intensité) ;
 * {@code thickness} = demi-épaisseur (km) ; {@code speed} = vitesse angulaire (rad/s d'horloge spatiale) au bord extérieur, plus rapide vers le centre (∝ r^-1,5), signe = sens.
 */
public final class AccretionDiskRender implements SpaceRenderObject {
    public static final String TYPE = "accretion_disk";
    /** Demi-épaisseur par défaut, en fraction du rayon extérieur. */
    public static final double DEFAULT_THICKNESS = 0.02, MAX_SPEED = 100;
    public static final Rgba DEFAULT_INNER = new Rgba(1.0, 0.85, 0.65, 2.5), DEFAULT_OUTER = new Rgba(0.8, 0.35, 0.12, 0.8);

    private final RenderTransform transform;
    private final double radius;
    public final double inner, opacity, thickness, speed;
    public final Rgba colorInner, colorOuter;

    public AccretionDiskRender(RenderTransform transform, double radius, double inner, Rgba colorInner, Rgba colorOuter, double opacity, double thickness, double speed) {
        this.transform = transform;
        this.radius = radius;
        this.inner = inner;
        this.colorInner = colorInner;
        this.colorOuter = colorOuter;
        this.opacity = opacity;
        this.thickness = thickness;
        this.speed = speed;
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
        t.putDouble("thickness", thickness);
        t.putDouble("speed", speed);
        return t;
    }

    @Override
    public String describe() {
        return "accretion_disk inner=" + inner + ", outer=" + radius + ", colorInner=" + colorInner.describe() + ", colorOuter=" + colorOuter.describe() + ", opacity=" + opacity
                + ", thickness=" + thickness + ", speed=" + speed + ", " + transform.describe();
    }

    @Nullable
    public static AccretionDiskRender load(CompoundTag t) {
        double r = t.getDouble("radius"), i = t.getDouble("inner"), o = t.contains("opacity") ? t.getDouble("opacity") : 1, th = t.contains("thickness") ? t.getDouble("thickness") : DEFAULT_THICKNESS * r,
                s = t.getDouble("speed");
        return valid(r, i, o, th, s) ? new AccretionDiskRender(RenderTransform.load(t.getCompound("transform")), r, i, Rgba.load(t, "colorInner", DEFAULT_INNER), Rgba.load(t, "colorOuter", DEFAULT_OUTER), o, th, s) : null;
    }

    public static boolean valid(double outer, double inner, double opacity, double thickness, double speed) {
        return Double.isFinite(outer) && inner > 0 && inner < outer && opacity > 0 && opacity <= 1 && thickness > 0 && thickness < outer && Math.abs(speed) <= MAX_SPEED; // NaN échoue
    }
}
