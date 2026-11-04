package fr.systamynna.spacemod.core.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.component.SpaceComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Composant de rendu d'un SpaceObject : les {@link SpaceRenderObject} à afficher dans le ciel d'un observateur. */
public class SpaceRender implements SpaceComponent {
    public static final String ID = "render";

    public final List<SpaceRenderObject> objects = new ArrayList<>();

    @Override
    public String id() { return ID; }

    @Override
    public CompoundTag save() {
        ListTag l = new ListTag();
        for (SpaceRenderObject o : objects) l.add(o.save());
        CompoundTag t = new CompoundTag();
        t.put("objects", l);
        return t;
    }

    @Override
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < objects.size(); i++) sb.append(i == 0 ? "" : "; ").append('#').append(i).append(' ').append(objects.get(i).describe());
        return sb.length() == 0 ? "(empty)" : sb.toString();
    }

    @Override
    public void edit(String args) {
        String[] a = args.trim().split("\\s+");
        switch (a[0]) {
            case "add" -> {
                if (a.length < 2) throw usage();
                objects.add(SpaceRenderObjects.create(a[1], List.of(Arrays.copyOfRange(a, 2, a.length))));
            }
            case "remove" -> {
                if (a.length != 2) throw usage();
                objects.remove(index(a[1]));
            }
            case "clear" -> objects.clear();
            case "transform" -> {
                if (a.length < 3) throw usage();
                objects.get(index(a[1])).transform().editAll(String.join(" ", Arrays.copyOfRange(a, 2, a.length)));
            }
            default -> throw usage();
        }
    }

    private int index(String s) {
        try {
            int i = Integer.parseInt(s);
            if (i >= 0 && i < objects.size()) return i;
        } catch (NumberFormatException e) {
            // usage ci-dessous
        }
        throw usage();
    }

    @Override
    public List<String> suggest(String args) {
        String[] w = args.split(" ", -1);
        if (w.length == 1) return List.of("add", "remove", "clear", "transform");
        if (w[0].equals("add") && w.length == 2) return SpaceRenderObjects.ids();
        if (w[0].equals("transform") && w.length >= 3) return RenderTransform.suggest(String.join(" ", Arrays.copyOfRange(w, 2, w.length)));
        return List.of();
    }

    private static SpaceObjectException usage() {
        return new SpaceObjectException("amynnaspacemod.component.usage", "add <type> <args> | remove <index> | clear | transform <index> pos|rot|scale <a> <b> <c> ...");
    }

    public static SpaceRender load(CompoundTag t) {
        SpaceRender r = new SpaceRender();
        for (Tag e : t.getList("objects", Tag.TAG_COMPOUND)) {
            SpaceRenderObject o = SpaceRenderObjects.load((CompoundTag) e);
            if (o != null) r.objects.add(o);
        }
        return r;
    }
}
