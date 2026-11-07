package fr.systamynna.spacemod.core.render;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;

/**
 * Couleur RGBA d'un élément de rendu. R, G, B dans [0, 1] ; A est un multiplicateur d'intensité dans [0, {@link #MAX_A}] (1 = référence, 0 = éteint).
 * Un composant non fini est ramené à 0 par le constructeur : passer par {@link #load} pour retomber sur une valeur par défaut.
 */
public record Rgba(double r, double g, double b, double a) {
    public static final double MAX_A = 4;

    public Rgba {
        r = clamp(r, 1);
        g = clamp(g, 1);
        b = clamp(b, 1);
        a = clamp(a, MAX_A);
    }

    private static double clamp(double v, double max) { return Double.isFinite(v) ? Math.max(0, Math.min(max, v)) : 0; }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putDouble("r", r);
        t.putDouble("g", g);
        t.putDouble("b", b);
        t.putDouble("a", a);
        return t;
    }

    /** Défensif : un champ absent ou non fini prend la valeur de {@code def} (un paquet invalide ne doit pas planter le client). */
    public static Rgba load(CompoundTag t, String key, Rgba def) {
        if (!t.contains(key, 10)) return def;
        CompoundTag c = t.getCompound(key);
        return new Rgba(comp(c, "r", def.r), comp(c, "g", def.g), comp(c, "b", def.b), comp(c, "a", def.a));
    }

    private static double comp(CompoundTag c, String k, double def) {
        if (!c.contains(k)) return def;
        double v = c.getDouble(k);
        return Double.isFinite(v) ? v : def;
    }

    /** {@code RRGGBB} ou {@code RRGGBBAA} (alpha de 00 à FF → 0 à 1), avec ou sans {@code #} ; {@code null} si invalide. */
    @Nullable
    public static Rgba parse(String s) {
        String h = s.startsWith("#") ? s.substring(1) : s;
        if ((h.length() != 6 && h.length() != 8) || !h.chars().allMatch(c -> Character.digit(c, 16) >= 0)) return null;
        long v = Long.parseLong(h, 16);
        if (h.length() == 6) v = v << 8 | 0xFF;
        return new Rgba((v >> 24 & 255) / 255.0, (v >> 16 & 255) / 255.0, (v >> 8 & 255) / 255.0, (v & 255) / 255.0);
    }

    public String describe() { return String.format(java.util.Locale.ROOT, "(%.2f %.2f %.2f %.2f)", r, g, b, a); }
}
