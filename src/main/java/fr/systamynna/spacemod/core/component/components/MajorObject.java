package fr.systamynna.spacemod.core.component.components;

import java.util.List;

import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.component.SpaceComponent;
import net.minecraft.nbt.CompoundTag;

/** Objet majeur (étoile, trou noir, système porteur d'étoiles) visible de très loin : couleur (RGB) et luminosité (relative au Soleil). */
public class MajorObject implements SpaceComponent {
    public static final String ID = "major_object";

    public int color = 0xFFFFFF;
    public double luminosity = 1;

    @Override
    public String id() { return ID; }

    @Override
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("color", color);
        t.putDouble("lum", luminosity);
        return t;
    }

    @Override
    public String describe() { return String.format("color=#%06X, luminosity=%s", color, luminosity); }

    @Override
    public void edit(String args) {
        String[] a = args.trim().split("\\s+");
        if (a.length != 2) throw usage();
        try {
            switch (a[0]) {
                case "color" -> color = Integer.parseInt(a[1].replaceFirst("^#", ""), 16) & 0xFFFFFF;
                case "lum" -> {
                    double l = Double.parseDouble(a[1]);
                    if (!valid(l)) throw new SpaceObjectException("amynnaspacemod.major.bad_luminosity", a[1]);
                    luminosity = l;
                }
                default -> throw usage();
            }
        } catch (NumberFormatException e) {
            throw usage();
        }
    }

    @Override
    public List<String> suggest(String args) { return args.contains(" ") ? List.of() : List.of("color", "lum"); }

    private static SpaceObjectException usage() { return new SpaceObjectException("amynnaspacemod.component.usage", "color <RRGGBB> | lum <luminosity>"); }

    private static boolean valid(double lum) { return lum > 0 && Double.isFinite(lum); }

    public static MajorObject load(CompoundTag t) {
        MajorObject s = new MajorObject();
        s.color = t.getInt("color") & 0xFFFFFF;
        double l = t.getDouble("lum");
        s.luminosity = valid(l) ? l : 1; // donnée corrompue : neutre plutôt que d'échouer à charger l'objet
        return s;
    }
}
