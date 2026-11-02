package fr.systamynna.spacemod.core;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.core.store.SpaceStore;
import fr.systamynna.spacemod.core.store.SpaceStores;

/**
 * Cette classe décrit la couche stratégique du mod, appartient au serveur.
 */
public class StrategicLayer {

    /**
     * UUID de la racine, de l'univers.
     */
    @javax.annotation.Nullable
    private UUID rootUUID;
    /**
     * Tous les objets spatiaux loadé
     */
    private final Map<UUID, SpaceObject> loaded = new HashMap<>();
    /**
     * Tous les objets spatiaux qui ont besoin d'être sauvegardés
     */
    private final Set<UUID> dirty = new HashSet<>();
    /** Persistance, ouverte pendant la vie du serveur. */
    private SpaceStore store;

    public void start(Path worldDir) {
        start(SpaceStores.open(worldDir));
    }

    public void start(SpaceStore store) {
        this.store = store;
        UUID id = store.loadRootUuid().orElse(null);
        if (id != null) {
            SpaceObject r = get(id).orElse(null);
            if (r == null) {
                AmynnaSpaceMod.LOGGER.warn("Le root {} ne correspond à aucun objet : root supprimé.", id);
                store.saveRootUuid(null);
            } else if (r.getParent() != null) {
                AmynnaSpaceMod.LOGGER.warn("Le root {} ({}) a un parent : il n'est plus root (l'objet est conservé).", id, r.getName());
                store.saveRootUuid(null);
            } else {
                rootUUID = id;
            }
        } else if (store.loadParentless().isEmpty()) { // base neuve
            SpaceObject r = new SpaceObject(UUID.randomUUID(), null, SpaceObject.ROOT_NAME);
            loaded.put(r.getUuid(), r);
            dirty.add(r.getUuid());
            flush();
            setRoot(r);
        }
        AmynnaSpaceMod.LOGGER.info("Amynna's Space Mod chargé !");
    }

    /** Écrit les objets modifiés. */
    public void flush() {
        if (store == null || dirty.isEmpty()) return;
        store.save(dirty.stream().map(loaded::get).filter(java.util.Objects::nonNull).toList());
        dirty.clear();
    }

    public void stop() {
        if (store == null) return;
        flush();
        store.close();
        store = null;
        loaded.clear();
        dirty.clear();
        rootUUID = null;
        AmynnaSpaceMod.LOGGER.info("Amynna's Space Mod arrêté !");
    }

    // --- arbre (thread serveur uniquement) ---

    private static final java.util.regex.Pattern NAME = java.util.regex.Pattern.compile("[a-z0-9_]+");

    public Optional<SpaceObject> get(UUID uuid) {
        SpaceObject o = loaded.get(uuid);
        if (o == null) {
            o = store.load(uuid).orElse(null);
            if (o != null) loaded.put(uuid, o);
        }
        return Optional.ofNullable(o);
    }

    public SpaceObject getOrThrow(UUID uuid) {
        return get(uuid).orElseThrow(() -> new SpaceObjectException("amynnaspacemod.spaceobject.unknown_uuid", uuid));
    }

    /** Enfants triés par nom ; {@code null} = objets sans parent. */
    public List<SpaceObject> children(@Nullable SpaceObject parent) {
        List<SpaceObject> out = new ArrayList<>();
        // L'instance en mémoire fait foi ; chaque mutation est flushée aussitôt, la base est donc à jour.
        for (SpaceObject c : parent == null ? store.loadParentless() : store.loadChildren(parent.getUuid()))
            out.add(loaded.computeIfAbsent(c.getUuid(), k -> c));
        out.sort(Comparator.comparing(SpaceObject::getName));
        return out;
    }

    public boolean isRoot(SpaceObject o) {
        return o.getUuid().equals(rootUUID);
    }

    /** Objets sans parent : le root d'abord, puis par nom. */
    public List<SpaceObject> topLevel() {
        List<SpaceObject> out = children(null);
        out.sort(Comparator.comparing((SpaceObject o) -> !isRoot(o)));
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
    public List<String> tree(boolean full) {
        List<String> out = new ArrayList<>();
        for (SpaceObject top : topLevel()) {
            boolean root = isRoot(top);
            if (!root && !full) continue;
            out.add(root ? "⭐ " + top.getName() : "▶ " + top.getName());
            subtree(top, "", out);
        }
        return out;
    }

    private void subtree(SpaceObject o, String prefix, List<String> out) {
        List<SpaceObject> kids = children(o);
        for (int i = 0; i < kids.size(); i++) {
            boolean last = i == kids.size() - 1;
            out.add(prefix + (last ? " └ " : " ├ ") + kids.get(i).getName());
            subtree(kids.get(i), prefix + (last ? "   " : " │ "), out);
        }
    }

    /** Adresse : noms depuis la racine, séparés par '/'. */
    public String addressOf(SpaceObject o) {
        Deque<String> names = new ArrayDeque<>();
        for (SpaceObject c = o; c != null; c = c.getParent() == null ? null : getOrThrow(c.getParent())) names.addFirst(c.getName());
        return String.join("/", names);
    }

    public SpaceObject resolve(String address) {
        String[] parts = address.split("/", -1);
        SpaceObject cur = null;
        for (String part : parts) {
            cur = child(cur, part);
            if (cur == null) throw new SpaceObjectException("amynnaspacemod.spaceobject.unknown_addr", address);
        }
        return cur;
    }

    private SpaceObject child(@Nullable SpaceObject parent, String name) {
        for (SpaceObject c : children(parent)) if (c.getName().equals(name)) return c;
        return null;
    }

    public SpaceObject add(String name, @Nullable SpaceObject parent) {
        if (!NAME.matcher(name).matches()) throw new SpaceObjectException("amynnaspacemod.spaceobject.bad_name", name);
        if (child(parent, name) != null) throw new SpaceObjectException("amynnaspacemod.spaceobject.name_taken", name);
        SpaceObject o = new SpaceObject(UUID.randomUUID(), parent == null ? null : parent.getUuid(), name);
        loaded.put(o.getUuid(), o);
        dirty.add(o.getUuid());
        flush();
        return o;
    }

    /** {@code newParent} null = détacher (objet sans parent). */
    public void setParent(SpaceObject o, @Nullable SpaceObject newParent) {
        if (isRoot(o)) throw new SpaceObjectException("amynnaspacemod.spaceobject.root");
        for (SpaceObject c = newParent; c != null; c = c.getParent() == null ? null : getOrThrow(c.getParent()))
            if (c.getUuid().equals(o.getUuid())) throw new SpaceObjectException("amynnaspacemod.spaceobject.cycle");
        SpaceObject same = child(newParent, o.getName());
        if (same != null && !same.getUuid().equals(o.getUuid())) throw new SpaceObjectException("amynnaspacemod.spaceobject.name_taken", o.getName());
        o.setParent(newParent == null ? null : newParent.getUuid());
        dirty.add(o.getUuid());
        flush();
    }

    /** Désigne le root (doit être sans parent) et le persiste. */
    public void setRoot(SpaceObject o) {
        if (o.getParent() != null) throw new SpaceObjectException("amynnaspacemod.spaceobject.not_top_level", o.getName());
        store.saveRootUuid(o.getUuid());
        rootUUID = o.getUuid();
    }

    /** {@code recursive} : supprime tout le sous-arbre ; sinon les enfants directs passent chez le parent de la cible. */
    public void remove(SpaceObject o, boolean recursive) {
        if (isRoot(o)) throw new SpaceObjectException("amynnaspacemod.spaceobject.root");
        List<SpaceObject> kids = children(o);
        if (recursive) {
            for (SpaceObject k : kids) remove(k, true);
        } else {
            UUID up = o.getParent();
            SpaceObject upObj = up == null ? null : getOrThrow(up);
            for (SpaceObject k : kids) {
                SpaceObject same = child(upObj, k.getName());
                if (same != null && !same.getUuid().equals(o.getUuid())) throw new SpaceObjectException("amynnaspacemod.spaceobject.name_taken", k.getName());
            }
            // Deux enfants ne peuvent pas se heurter entre eux : ils avaient des noms distincts chez la cible.
            for (SpaceObject k : kids) { k.setParent(up); dirty.add(k.getUuid()); }
            flush();
        }
        store.delete(o.getUuid());
        loaded.remove(o.getUuid());
        dirty.remove(o.getUuid());
    }
}
