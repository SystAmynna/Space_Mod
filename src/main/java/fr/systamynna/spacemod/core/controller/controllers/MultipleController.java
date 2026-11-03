package fr.systamynna.spacemod.core.controller.controllers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.Transform;
import fr.systamynna.spacemod.core.controller.SpaceController;
import fr.systamynna.spacemod.core.controller.SpaceControllers;
import net.minecraft.nbt.CompoundTag;

/** Contient des contrôleurs nommés dont un seul est actif ; relaie l'actif. Aucun actif : l'objet ne bouge pas. */
public class MultipleController implements SpaceController {
    public static final String ID = "multiple";

    private final Map<String, SpaceController> controllers = new LinkedHashMap<>();
    @Nullable
    private String active;

    @Override
    public String id() { return ID; }

    @Nullable
    public SpaceController active() { return active == null ? null : controllers.get(active); }

    @Nullable
    public String activeName() { return active; }

    public SpaceController get(String name) {
        SpaceController c = controllers.get(name);
        if (c == null) throw new SpaceObjectException("amynnaspacemod.controller.no_entry", name);
        return c;
    }

    /** Ajoute (ou remplace) un contrôleur nommé. Les {@code MultipleController} imbriqués sont refusés. */
    public void put(String name, SpaceController c) {
        if (c instanceof MultipleController) throw new SpaceObjectException("amynnaspacemod.controller.nested");
        controllers.put(name, c);
    }

    public void remove(String name) {
        get(name);
        controllers.remove(name);
        if (name.equals(active)) active = null;
    }

    public void use(String name) {
        get(name);
        active = name;
    }

    @Override
    public void apply(double ticks, Transform out) {
        SpaceController c = active();
        if (c != null) c.apply(ticks, out);
    }

    @Override
    public void resume(Transform t, double savedTicks, double nowTicks) {
        SpaceController c = active();
        if (c != null) c.resume(t, savedTicks, nowTicks);
    }

    @Override
    public String describe() {
        List<String> parts = new ArrayList<>();
        controllers.forEach((n, c) -> parts.add((n.equals(active) ? "*" : "") + n + "=" + c.id() + "{" + c.describe() + "}"));
        return "active=" + active + ", [" + String.join(", ", parts) + "]";
    }

    @Override
    public void edit(String args) {
        String[] a = args.trim().split("\\s+", 3);
        switch (a[0]) {
            case "add" -> {
                if (a.length != 3) throw usage();
                put(a[1], SpaceControllers.create(a[2]));
            }
            case "remove" -> {
                if (a.length != 2) throw usage();
                remove(a[1]);
            }
            case "use" -> {
                if (a.length != 2) throw usage();
                use(a[1]);
            }
            case "in" -> {
                if (a.length != 3) throw usage();
                get(a[1]).edit(a[2]);
            }
            default -> throw usage();
        }
    }

    @Override
    public List<String> suggest(String args) {
        String[] a = args.split(" ", -1);
        if (a.length == 1) return List.of("add", "remove", "use", "in");
        if (a.length == 2 && !a[0].equals("add")) return List.copyOf(controllers.keySet());
        if (a.length == 3 && a[0].equals("add")) return SpaceControllers.ids().stream().filter(i -> !i.equals(ID)).toList();
        if (a[0].equals("in") && a.length >= 3) {
            SpaceController c = controllers.get(a[1]);
            return c == null ? List.of() : c.suggest(String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length)));
        }
        return List.of();
    }

    private static SpaceObjectException usage() {
        return new SpaceObjectException("amynnaspacemod.controller.usage", "add <name> <type> | remove <name> | use <name> | in <name> <args…> (~ = keep value)");
    }

    @Override
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        if (active != null) t.putString("active", active);
        CompoundTag all = new CompoundTag();
        controllers.forEach((n, c) -> all.put(n, SpaceControllers.save(c)));
        t.put("controllers", all);
        return t;
    }

    public static MultipleController load(CompoundTag t) {
        MultipleController m = new MultipleController();
        CompoundTag all = t.getCompound("controllers");
        for (String n : all.getAllKeys()) {
            SpaceController c = SpaceControllers.load(all.getCompound(n));
            if (c != null && !(c instanceof MultipleController)) m.controllers.put(n, c);
        }
        if (t.contains("active") && m.controllers.containsKey(t.getString("active"))) m.active = t.getString("active");
        return m;
    }
}
