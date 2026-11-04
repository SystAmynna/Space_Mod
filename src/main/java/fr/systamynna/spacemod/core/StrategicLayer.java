package fr.systamynna.spacemod.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.core.clock.SpaceClock;
import fr.systamynna.spacemod.core.component.SpaceComponent;
import fr.systamynna.spacemod.core.controller.SpaceController;
import fr.systamynna.spacemod.core.provider.ProviderType;
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
    private static final double TICK_MS = 50;
    private final SpaceTree tree = new SpaceTree(this);
    private SpaceListener listener = SpaceListener.NONE;

    public void setListener(SpaceListener listener) { this.listener = listener; }

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
            modified(r.getUuid());
            flush();
            setRoot(r);
        }
        AmynnaSpaceMod.LOGGER.info("Amynna's Space Mod chargé !");
    }

    /** Marque l'objet à écrire au prochain {@link #flush()} et le réplique tout de suite aux abonnés. */
    private void modified(UUID uuid) {
        dirty.add(uuid);
        SpaceObject o = loaded.get(uuid);
        if (o != null) listener.updated(o);
    }

    /**
     * Écrit les objets modifiés. Appelé par la sauvegarde automatique, l'arrêt du serveur et les changements d'arbre ;
     * les éditions de propriétés attendent l'un de ces moments (la réplication, elle, est immédiate : voir {@link #modified}).
     */
    public void flush() {
        if (store == null || dirty.isEmpty()) return;
        List<SpaceObject> saved = dirty.stream().map(loaded::get).filter(java.util.Objects::nonNull).toList();
        store.save(saved);
        dirty.clear();
        AmynnaSpaceMod.LOGGER.info("FLUSHED to the DB !");
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
            if (o != null) {
                loaded.put(uuid, o);
                o.resume(SpaceClock.SERVER.now() / TICK_MS);
                listener.updated(o);
            }
        }
        return Optional.ofNullable(o);
    }

    public SpaceObject getOrThrow(UUID uuid) {
        return get(uuid).orElseThrow(() -> new SpaceObjectException("amynnaspacemod.spaceobject.unknown_uuid", uuid));
    }

    public boolean isRoot(SpaceObject o) {
        return o.getUuid().equals(rootUUID);
    }

    /** Outil de lecture de l'arbre. */
    public SpaceTree tree() { return tree; }

    SpaceStore store() { return store; }

    @Nullable
    SpaceObject loadedOrNull(UUID uuid) { return loaded.get(uuid); }

    /** Lecture sans charger : l'instance loadée, sinon une copie transitoire du store. */
    SpaceObject peek(UUID uuid) {
        SpaceObject o = loaded.get(uuid);
        return o != null ? o : store.load(uuid).orElseThrow(() -> new SpaceObjectException("amynnaspacemod.spaceobject.unknown_uuid", uuid));
    }

    public boolean isLoaded(UUID uuid) { return loaded.containsKey(uuid); }

    /** Objets loadés (ordre non spécifié). */
    public java.util.Collection<SpaceObject> loadedObjects() { return List.copyOf(loaded.values()); }

    public void load(UUID uuid) { getOrThrow(uuid); }

    /**
     * Retire de la mémoire (flush avant si dirty). Si {@code recursive}, les descendants loadés partent aussi ;
     * sinon, un objet qui a des descendants loadés n'est pas unload et le résultat est {@code false}.
     */
    public boolean unload(UUID uuid, boolean recursive) {
        if (!loaded.containsKey(uuid)) throw new SpaceObjectException("amynnaspacemod.spaceobject.not_loaded", uuid);
        List<UUID> victims = new ArrayList<>(List.of(uuid));
        for (SpaceObject o : loaded.values()) if (!o.getUuid().equals(uuid) && isDescendant(o, uuid)) victims.add(o.getUuid());
        if (victims.size() > 1 && !recursive) return false;
        if (victims.stream().anyMatch(dirty::contains)) flush();
        victims.forEach(loaded::remove);
        victims.forEach(listener::dropped);
        return true;
    }

    private boolean isDescendant(SpaceObject o, UUID ancestor) {
        for (UUID p = o.getParent(); p != null; p = peek(p).getParent()) if (p.equals(ancestor)) return true;
        return false;
    }

    public SpaceObject add(String name, @Nullable SpaceObject parent) {
        if (!NAME.matcher(name).matches()) throw new SpaceObjectException("amynnaspacemod.spaceobject.bad_name", name);
        if (tree.child(parent, name) != null) throw new SpaceObjectException("amynnaspacemod.spaceobject.name_taken", name);
        SpaceObject o = new SpaceObject(UUID.randomUUID(), parent == null ? null : parent.getUuid(), name);
        loaded.put(o.getUuid(), o);
        modified(o.getUuid());
        flush();
        return o;
    }

    public void setParent(SpaceObject o, @Nullable SpaceObject newParent) { setParent(o, newParent, false); }

    /** {@code newParent} null = détacher (objet sans parent). {@code transpose} : garde la position absolue en recalculant le transform. */
    public void setParent(SpaceObject o, @Nullable SpaceObject newParent, boolean transpose) {
        if (isRoot(o)) throw new SpaceObjectException("amynnaspacemod.spaceobject.root");
        for (SpaceObject c = newParent; c != null; c = c.getParent() == null ? null : peek(c.getParent()))
            if (c.getUuid().equals(o.getUuid())) throw new SpaceObjectException("amynnaspacemod.spaceobject.cycle");
        SpaceObject same = tree.child(newParent, o.getName());
        if (same != null && !same.getUuid().equals(o.getUuid())) throw new SpaceObjectException("amynnaspacemod.spaceobject.name_taken", o.getName());
        if (transpose) o.transform.set(tree.convert(o.transform, o.getScale(), o.getParent() == null ? null : peek(o.getParent()), newParent));
        o.setParent(newParent == null ? null : newParent.getUuid());
        modified(o.getUuid());
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
        List<SpaceObject> kids = tree.children(o).stream().map(k -> getOrThrow(k.getUuid())).toList();
        if (recursive) {
            for (SpaceObject k : kids) remove(k, true);
        } else {
            UUID up = o.getParent();
            SpaceObject upObj = up == null ? null : getOrThrow(up);
            for (SpaceObject k : kids) {
                SpaceObject same = tree.child(upObj, k.getName());
                if (same != null && !same.getUuid().equals(o.getUuid())) throw new SpaceObjectException("amynnaspacemod.spaceobject.name_taken", k.getName());
            }
            // Deux enfants ne peuvent pas se heurter entre eux : ils avaient des noms distincts chez la cible.
            for (SpaceObject k : kids) { k.setParent(up); modified(k.getUuid()); }
            flush();
        }
        store.delete(o.getUuid());
        loaded.remove(o.getUuid());
        dirty.remove(o.getUuid());
        listener.removed(o.getUuid());
    }

    // --- provider ---

    /** Provider choisi pour ce monde ; {@code NONE} par défaut. */
    public ProviderType getProvider() {
        try {
            return store.loadSetting("provider").map(ProviderType::valueOf).orElse(ProviderType.NONE);
        } catch (IllegalArgumentException e) { // valeur obsolète (ex. ancien LOCAL)
            return ProviderType.NONE;
        }
    }

    public void setProvider(ProviderType p) { store.saveSetting("provider", p.name()); }

    public enum UpsertResult { CREATED, UPDATED, UNCHANGED }

    /**
     * Crée l'enfant {@code name} de {@code parent} depuis {@code data} (format de {@link SpaceObject#save}), ou aligne l'existant dessus.
     * Une création est écrite aussitôt ; une modification au prochain {@link #flush()}.
     */
    public UpsertResult upsert(@Nullable SpaceObject parent, String name, CompoundTag data) {
        if (!NAME.matcher(name).matches()) throw new SpaceObjectException("amynnaspacemod.spaceobject.bad_name", name);
        UUID parentId = parent == null ? null : parent.getUuid();
        data.putString("name", name);
        SpaceObject existing = tree.child(parent, name);
        if (existing == null) {
            SpaceObject o = SpaceObject.load(UUID.randomUUID(), parentId, data);
            loaded.put(o.getUuid(), o);
            o.resume(SpaceClock.SERVER.now() / TICK_MS);
            modified(o.getUuid());
            flush(); // l'arbre se lit dans le store : l'enfant doit y être pour que les suivants le trouvent
            return UpsertResult.CREATED;
        }
        if (!existing.assign(SpaceObject.load(existing.getUuid(), parentId, data))) return UpsertResult.UNCHANGED;
        modified(existing.getUuid());
        return UpsertResult.UPDATED;
    }

    // --- composants ---

    public void addComponent(UUID uuid, SpaceComponent c) {
        SpaceObject o = getOrThrow(uuid);
        if (o.getComponent(c.getClass()).isPresent()) throw new SpaceObjectException("amynnaspacemod.component.already", c.id());
        o.putComponent(c);
        modified(uuid);
    }

    public void removeComponent(UUID uuid, Class<? extends SpaceComponent> type) {
        if (!getOrThrow(uuid).removeComponent(type)) throw new SpaceObjectException("amynnaspacemod.component.missing", type.getSimpleName());
        modified(uuid);
    }

    /** Load l'objet, applique l'édition, puis le marque dirty et le réplique (écriture au prochain flush). */
    public void editComponent(UUID uuid, Class<? extends SpaceComponent> type, java.util.function.Consumer<SpaceComponent> edit) {
        SpaceComponent c = getOrThrow(uuid).getComponent(type).orElseThrow(() -> new SpaceObjectException("amynnaspacemod.component.missing", type.getSimpleName()));
        try {
            edit.accept(c);
        } finally {
            modified(uuid);
        }
    }

    // --- transform / échelle ---

    /** Édite le transform de l'objet, puis le marque dirty et le réplique. */
    public void editTransform(UUID uuid, java.util.function.Consumer<Transform> edit) {
        SpaceObject o = getOrThrow(uuid);
        try {
            edit.accept(o.transform);
        } finally {
            modified(uuid);
        }
    }

    public void setScale(UUID uuid, double scale) {
        getOrThrow(uuid).setScale(scale);
        modified(uuid);
    }

    // --- contrôleur ---

    /** {@code null} retire le contrôleur : l'objet reste à sa dernière pose. */
    public void setController(UUID uuid, @Nullable SpaceController c) {
        getOrThrow(uuid).setController(c);
        modified(uuid);
    }

    public void editController(UUID uuid, java.util.function.Consumer<SpaceController> edit) {
        SpaceController c = getOrThrow(uuid).getController();
        if (c == null) throw new SpaceObjectException("amynnaspacemod.controller.none");
        try {
            edit.accept(c);
        } finally {
            modified(uuid);
        }
    }

    /**
     * Recalcule la pose des objets loadés qui ont un contrôleur, à l'instant {@code ticks}.
     * N'est ni persisté ni répliqué : le client recalcule la même pose avec son horloge.
     */
    public void tickControllers(double ticks) {
        for (SpaceObject o : loaded.values()) o.step(ticks);
    }
}
