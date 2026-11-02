package fr.systamynna.spacemod.core.component.components;

import java.util.List;

import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.component.SpaceComponent;
import net.minecraft.nbt.CompoundTag;

/** Nom propre (espaces autorisés) et description. */
public class Identity implements SpaceComponent {
    public static final String ID = "identity";

    public String properName;
    public String description = "";

    public Identity(String properName) { this.properName = properName; }

    @Override
    public String id() { return ID; }

    @Override
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", properName);
        t.putString("description", description);
        return t;
    }

    @Override
    public String describe() { return "name=\"" + properName + "\", description=\"" + description + "\""; }

    @Override
    public void edit(String args) {
        String[] a = args.split(" ", 2);
        if (a.length < 2) throw usage();
        switch (a[0]) {
            case "name" -> properName = a[1];
            case "desc" -> description = a[1];
            default -> throw usage();
        }
    }

    @Override
    public List<String> suggest(String args) { return args.contains(" ") ? List.of() : List.of("name", "desc"); }

    private static SpaceObjectException usage() { return new SpaceObjectException("amynnaspacemod.component.usage", "name <name> | desc <description>"); }

    public static Identity load(CompoundTag t) {
        Identity i = new Identity(t.getString("name"));
        i.description = t.getString("description");
        return i;
    }
}
