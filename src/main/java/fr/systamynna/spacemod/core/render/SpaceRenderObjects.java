package fr.systamynna.spacemod.core.render;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.core.SpaceObjectException;
import net.minecraft.nbt.CompoundTag;

/** Registre des types de {@link SpaceRenderObject} : l'unique endroit (avec son drawer client) à compléter pour en ajouter un. */
public final class SpaceRenderObjects {
    private record Entry(Function<CompoundTag, SpaceRenderObject> load, Function<List<String>, SpaceRenderObject> create, String usage) {}

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    static {
        ENTRIES.put(PlanetRender.TYPE, new Entry(PlanetRender::load, a -> {
            if (a.size() != 2) throw usage(PlanetRender.TYPE);
            double r;
            try { r = Double.parseDouble(a.get(1)); } catch (NumberFormatException e) { throw usage(PlanetRender.TYPE); }
            if (!(r > 0) || !Double.isFinite(r)) throw usage(PlanetRender.TYPE);
            return new PlanetRender(new RenderTransform(), a.get(0), r);
        }, "planet <texture> <radius>"));
        ENTRIES.put(GalaxyCloudRender.TYPE, new Entry(GalaxyCloudRender::load, a -> {
            if (a.size() > 5) throw usage(GalaxyCloudRender.TYPE);
            double[] v = galaxyArgs(GalaxyCloudRender.TYPE, a.subList(0, Math.min(3, a.size())), 3);
            Rgba core = a.size() > 3 ? Rgba.parse(a.get(3)) : GalaxyCloudRender.DEFAULT_CORE, edge = a.size() > 4 ? Rgba.parse(a.get(4)) : GalaxyCloudRender.DEFAULT_EDGE;
            if (core == null || edge == null || !GalaxyCloudRender.valid(v[0], (int) v[1], v[2])) throw usage(GalaxyCloudRender.TYPE);
            return new GalaxyCloudRender(new RenderTransform(), v[0], (int) v[1], v[2], core, edge);
        }, "galaxy_cloud <radius> [arms] [thickness] [core RRGGBB[AA]] [edge RRGGBB[AA]]"));
        ENTRIES.put(GalaxyStarsRender.TYPE, new Entry(GalaxyStarsRender::load, a -> {
            double[] v = galaxyArgs(GalaxyStarsRender.TYPE, a, 4);
            if (!GalaxyStarsRender.valid(v[0], (int) v[1], v[2], v[3])) throw usage(GalaxyStarsRender.TYPE);
            return new GalaxyStarsRender(new RenderTransform(), v[0], (int) v[1], v[2], v[3]);
        }, "galaxy_stars <radius> [arms] [thickness] [density]"));
        ENTRIES.put(StarRender.TYPE, new Entry(StarRender::load, a -> {
            String u = StarRender.TYPE;
            if (a.isEmpty() || a.size() > 6) throw usage(u);
            double r = num(u, a, 0, 0), s = num(u, a, 3, 1), g = num(u, a, 4, 1), e = num(u, a, 5, StarRender.DEFAULT_EMISSIVE);
            if (!StarRender.valid(r, s, g, e)) throw usage(u);
            Rgba hot = rgba(u, a, 1, StarRender.DEFAULT_HOT);
            return new StarRender(new RenderTransform(), r, hot, rgba(u, a, 2, StarRender.coolFor(hot)), s, g, e);
        }, "star <radius> [hot RRGGBB[AA]] [cool RRGGBB[AA]] [speed] [granulation] [emissive]"));
        ENTRIES.put(BlackHoleRender.TYPE, new Entry(BlackHoleRender::load, a -> {
            String u = BlackHoleRender.TYPE;
            if (a.isEmpty() || a.size() > 2) throw usage(u);
            double r = num(u, a, 0, 0);
            if (!(r > 0) || !Double.isFinite(r)) throw usage(u);
            return new BlackHoleRender(new RenderTransform(), r, rgba(u, a, 1, BlackHoleRender.DEFAULT_COLOR));
        }, "black_hole <radius> [color RRGGBB[AA]]"));
        ENTRIES.put(DistorsionRender.TYPE, new Entry(DistorsionRender::load, a -> {
            String u = DistorsionRender.TYPE;
            if (a.isEmpty() || a.size() > 3) throw usage(u);
            double r = num(u, a, 0, 0), s = num(u, a, 1, DistorsionRender.DEFAULT_STRENGTH), e = num(u, a, 2, DistorsionRender.DEFAULT_EXTENT);
            if (!DistorsionRender.valid(r, s, e)) throw usage(u);
            return new DistorsionRender(new RenderTransform(), r, s, e);
        }, "distorsion <horizon radius> [strength, < 0 = white hole] [extent]"));
        ENTRIES.put(GammaEruptionRender.TYPE, new Entry(GammaEruptionRender::load, a -> {
            String u = GammaEruptionRender.TYPE;
            if (a.isEmpty() || a.size() > 9) throw usage(u);
            double r = num(u, a, 0, 0), w = num(u, a, 1, 0.05), s = num(u, a, 4, 1), seed = num(u, a, 5, 0), sp = num(u, a, 6, 0), ti = num(u, a, 7, 0), cu = num(u, a, 8, 0);
            if (!GammaEruptionRender.valid(r, w, s, sp, ti, cu) || seed != Math.rint(seed) || Math.abs(seed) > Integer.MAX_VALUE) throw usage(u);
            return new GammaEruptionRender(new RenderTransform(), r, w, rgba(u, a, 2, GammaEruptionRender.DEFAULT_A), rgba(u, a, 3, GammaEruptionRender.DEFAULT_B), s, (int) seed, sp, ti, cu);
        }, "gamma_eruption <length> [width] [colorA RRGGBB[AA]] [colorB RRGGBB[AA]] [speed] [seed] [spin rad/s] [tilt deg] [curve 0..1]"));
        ENTRIES.put(RingRender.TYPE, new Entry(RingRender::load, a -> {
            String u = RingRender.TYPE;
            if (a.size() < 2 || a.size() > 8) throw usage(u);
            double inner = num(u, a, 0, 0), outer = num(u, a, 1, 0), op = num(u, a, 4, 1), oc = num(u, a, 6, 0), th = num(u, a, 7, RingRender.DEFAULT_THICKNESS * outer);
            if (a.size() > 5 && !a.get(5).equals("true") && !a.get(5).equals("false")) throw usage(u);
            if (!RingRender.valid(outer, inner, op, oc, th)) throw usage(u);
            return new RingRender(new RenderTransform(), outer, inner, rgba(u, a, 2, RingRender.DEFAULT_INNER), rgba(u, a, 3, RingRender.DEFAULT_OUTER), op, a.size() > 5 && a.get(5).equals("true"), oc, th);
        }, "ring <inner> <outer> [colorInner RRGGBB[AA]] [colorOuter RRGGBB[AA]] [opacity] [emissive true|false] [occluder] [thickness]"));
        ENTRIES.put(ShadowRender.TYPE, new Entry(ShadowRender::load, a -> {
            String u = ShadowRender.TYPE;
            if (a.isEmpty() || a.size() > 3) throw usage(u);
            double r = num(u, a, 0, 0), am = num(u, a, 1, ShadowRender.DEFAULT_AMBIENT), s = num(u, a, 2, ShadowRender.DEFAULT_SOFTNESS);
            if (!ShadowRender.valid(r, am, s)) throw usage(u);
            return new ShadowRender(new RenderTransform(), r, am, s);
        }, "shadow <radius> [ambient 0..1] [softness]"));
        ENTRIES.put(AtmosphereRender.TYPE, new Entry(AtmosphereRender::load, a -> {
            String u = AtmosphereRender.TYPE;
            if (a.size() < 2 || a.size() > 5) throw usage(u);
            double r = num(u, a, 0, 0), i = num(u, a, 1, 0), d = num(u, a, 4, 1);
            if (!AtmosphereRender.valid(r, i, d)) throw usage(u);
            return new AtmosphereRender(new RenderTransform(), r, i, rgba(u, a, 2, AtmosphereRender.DEFAULT_DAY), rgba(u, a, 3, AtmosphereRender.DEFAULT_SUNSET), d);
        }, "atmosphere <radius> <surface radius> [day RRGGBB[AA]] [sunset RRGGBB[AA]] [density]"));
    }

    /** Argument numérique {@code i} (défaut {@code def} s'il est absent) ; usage affiché si ce n'est pas un nombre. */
    private static double num(String type, List<String> a, int i, double def) {
        if (i >= a.size()) return def;
        try { return Double.parseDouble(a.get(i)); } catch (NumberFormatException e) { throw usage(type); }
    }

    private static Rgba rgba(String type, List<String> a, int i, Rgba def) {
        if (i >= a.size()) return def;
        Rgba c = Rgba.parse(a.get(i));
        if (c == null) throw usage(type);
        return c;
    }

    /** {@code <radius> [arms] [thickness] [density]} → {radius, arms, thickness, density} avec les défauts 2, 0,04 et 1 ; au plus {@code max} arguments. */
    private static double[] galaxyArgs(String type, List<String> a, int max) {
        if (a.isEmpty() || a.size() > max) throw usage(type);
        double[] v = {0, 2, 0.04, 1};
        try {
            for (int i = 0; i < a.size(); i++) v[i] = Double.parseDouble(a.get(i));
        } catch (NumberFormatException e) { throw usage(type); }
        if (v[1] != Math.rint(v[1])) throw usage(type); // arms entier
        return v;
    }

    private SpaceRenderObjects() {}

    public static List<String> ids() { return List.copyOf(ENTRIES.keySet()); }

    /** {@code null} si le type est inconnu ou les données invalides. */
    @Nullable
    public static SpaceRenderObject load(CompoundTag t) {
        Entry e = ENTRIES.get(t.getString("type"));
        return e == null ? null : e.load().apply(t);
    }

    public static SpaceRenderObject create(String type, List<String> args) {
        Entry e = ENTRIES.get(type);
        if (e == null) throw new SpaceObjectException("amynnaspacemod.component.usage", "add " + String.join("|", ids()) + " ...");
        return e.create().apply(args);
    }

    private static SpaceObjectException usage(String type) { return new SpaceObjectException("amynnaspacemod.component.usage", "add " + ENTRIES.get(type).usage()); }
}
