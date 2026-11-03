package fr.systamynna.spacemod.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Lecture de l'arbre des objets. Les listings ne chargent rien ({@link #children}) ;
 * {@link #child}/{@link #resolve}, qui servent à cibler un objet, le chargent.
 */
public final class SpaceTree {
    private final StrategicLayer layer;

    SpaceTree(StrategicLayer layer) { this.layer = layer; }

    /** Enfants triés par nom ; {@code null} = objets sans parent. Sans charger : l'instance loadée fait foi, sinon copie du store. */
    public List<SpaceObject> children(@Nullable SpaceObject parent) {
        List<SpaceObject> out = new ArrayList<>();
        // Chaque mutation est flushée aussitôt, la base est donc à jour pour les objets non loadés.
        for (SpaceObject c : parent == null ? layer.store().loadParentless() : layer.store().loadChildren(parent.getUuid())) {
            SpaceObject l = layer.loadedOrNull(c.getUuid());
            out.add(l != null ? l : c);
        }
        out.sort(Comparator.comparing(SpaceObject::getName));
        return out;
    }

    /** Objets sans parent : le root d'abord, puis par nom. */
    public List<SpaceObject> topLevel() {
        List<SpaceObject> out = children(null);
        out.sort(Comparator.comparing((SpaceObject o) -> !layer.isRoot(o)));
        return out;
    }

    public List<SpaceObject> all() {
        List<SpaceObject> out = new ArrayList<>();
        Deque<SpaceObject> todo = new ArrayDeque<>(topLevel());
        while (!todo.isEmpty()) {
            SpaceObject o = todo.pop();
            out.add(o);
            todo.addAll(children(o));
        }
        return out;
    }

    /** Arbre façon {@code tree} : le root d'abord ({@code ⭐}) ; {@code full} ajoute ensuite les arbres hors du root. */
    public List<Component> render(boolean full) { return render(full, o -> true); }

    /** Comme {@link #render(boolean)} mais ne montre que les objets {@code keep} et leurs ancêtres (suffixés « … »). */
    public List<Component> render(boolean full, Predicate<SpaceObject> keep) {
        List<Component> out = new ArrayList<>();
        for (SpaceObject top : topLevel()) {
            boolean root = layer.isRoot(top);
            if ((!root && !full) || !shown(top, keep)) continue;
            out.add(line(root ? "⭐ " : "▶ ", top, keep));
            subtree(top, "", out, keep);
        }
        return out;
    }

    private boolean shown(SpaceObject o, Predicate<SpaceObject> keep) {
        return keep.test(o) || children(o).stream().anyMatch(c -> shown(c, keep));
    }

    /** Préfixe de l'arbre, puis le nom (bleu si loadé), suffixé « … » si seulement ancêtre. */
    private MutableComponent line(String prefix, SpaceObject o, Predicate<SpaceObject> keep) {
        MutableComponent name = Component.literal(o.getName());
        if (layer.isLoaded(o.getUuid())) name.withStyle(ChatFormatting.BLUE);
        MutableComponent out = Component.literal(prefix).append(name);
        return keep.test(o) ? out : out.append(" …");
    }

    private void subtree(SpaceObject o, String prefix, List<Component> out, Predicate<SpaceObject> keep) {
        List<SpaceObject> kids = children(o).stream().filter(k -> shown(k, keep)).toList();
        for (int i = 0; i < kids.size(); i++) {
            boolean last = i == kids.size() - 1;
            out.add(line(prefix + (last ? " └ " : " ├ "), kids.get(i), keep));
            subtree(kids.get(i), prefix + (last ? "   " : " │ "), out, keep);
        }
    }

    /** Adresse : noms depuis la racine, séparés par '/'. */
    public String addressOf(SpaceObject o) {
        Deque<String> names = new ArrayDeque<>();
        for (SpaceObject c = o; c != null; c = c.getParent() == null ? null : layer.peek(c.getParent())) names.addFirst(c.getName());
        return String.join("/", names);
    }

    /** Résout (et charge) l'objet à cette adresse. */
    public SpaceObject resolve(String address) {
        SpaceObject cur = null;
        for (String part : address.split("/", -1)) {
            cur = child(cur, part);
            if (cur == null) throw new SpaceObjectException("amynnaspacemod.spaceobject.unknown_addr", address);
        }
        return cur;
    }

    /** Enfant nommé, chargé ; {@code null} s'il n'existe pas. */
    @Nullable
    SpaceObject child(@Nullable SpaceObject parent, String name) {
        for (SpaceObject c : children(parent)) if (c.getName().equals(name)) return layer.get(c.getUuid()).orElse(null);
        return null;
    }

    /** {@code o}, ses parents, … jusqu'au sommet de son arbre (sans charger). */
    private List<SpaceObject> chain(@Nullable SpaceObject o) {
        List<SpaceObject> out = new ArrayList<>();
        for (SpaceObject c = o; c != null; c = c.getParent() == null ? null : layer.peek(c.getParent())) out.add(c);
        return out;
    }

    /**
     * Convertit {@code t}, exprimé dans le référentiel de {@code from} (celui de ses enfants), vers celui de {@code to}.
     * {@code null} = référentiel absolu, au-dessus des objets sans parent. On ne monte que jusqu'à l'ancêtre commun, pour garder la précision.
     */
    public Transform convert(Transform t, @Nullable SpaceObject from, @Nullable SpaceObject to) {
        List<SpaceObject> up = chain(from), down = chain(to);
        while (!up.isEmpty() && !down.isEmpty() && up.getLast().getUuid().equals(down.getLast().getUuid())) {
            up.removeLast();
            down.removeLast();
        }
        for (SpaceObject o : up) t = t.up(o.transform, o.getScale());
        for (SpaceObject o : down.reversed()) t = t.down(o.transform, o.getScale());
        return t;
    }

    /** Transform de {@code o} dans le référentiel absolu. */
    public Transform worldTransform(SpaceObject o) {
        return convert(o.transform, o.getParent() == null ? null : layer.peek(o.getParent()), null);
    }
}
