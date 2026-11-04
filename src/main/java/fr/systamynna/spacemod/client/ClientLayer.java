package fr.systamynna.spacemod.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.core.SpaceNet;
import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.StrategicLayer;
import fr.systamynna.spacemod.core.clock.SpaceClock;
import net.minecraft.nbt.CompoundTag;

/**
 * Couche stratégique du client : réplique (lecture seule) des objets auxquels il est abonné, alimentée par {@link SpaceNet}.
 * Thread client uniquement. Instance distincte de {@link StrategicLayer} car en solo client et serveur partagent la JVM.
 */
public final class ClientLayer {
    public static final ClientLayer INSTANCE = new ClientLayer();

    /** Observateur des changements de la couche (le rendu y déclare ses SpaceRender). */
    public interface Listener {
        void applied(SpaceObject o);

        void dropped(UUID uuid);

        void cleared();
    }

    private final Map<UUID, SpaceObject> objects = new HashMap<>();
    private final List<Listener> listeners = new ArrayList<>();

    ClientLayer() {}

    public void addListener(Listener l) { listeners.add(l); }

    /** Ajoute ou remplace l'objet par l'état reçu du serveur. */
    public void apply(UUID uuid, @Nullable UUID parent, CompoundTag data) {
        SpaceObject o;
        try { // diagnostic NONE, retirable
            o = SpaceObject.load(uuid, parent, data);
            o.resume(SpaceClock.CLIENT.now() / 50); // le transform reçu est daté : le contrôleur le corrige
        } catch (RuntimeException e) {
            fr.systamynna.spacemod.AmynnaSpaceMod.LOGGER.error("ClientLayer.apply : objet {} illisible, ignoré", uuid, e);
            return;
        }
        objects.put(uuid, o);
        for (Listener l : listeners) l.applied(o);
    }

    /** Recalcule la pose des objets à contrôleur à l'instant {@code ticks} (peut être fractionnaire, pour le rendu). */
    public void step(double ticks) { for (SpaceObject o : objects.values()) o.step(ticks); }

    public void drop(UUID uuid) {
        objects.remove(uuid);
        for (Listener l : listeners) l.dropped(uuid);
    }

    public void clear() {
        objects.clear();
        for (Listener l : listeners) l.cleared();
    }

    public boolean has(UUID uuid) { return objects.containsKey(uuid); }

    @Nullable
    public SpaceObject get(UUID uuid) { return objects.get(uuid); }

    // shortcut: parcours complet à chaque appel, ajouter un index parent→enfants si le client reçoit beaucoup d'objets.
    /** Enfants connus du client, triés par nom. */
    public List<SpaceObject> children(UUID parent) {
        List<SpaceObject> out = new ArrayList<>();
        for (SpaceObject o : objects.values()) if (parent.equals(o.getParent())) out.add(o);
        out.sort(Comparator.comparing(SpaceObject::getName));
        return out;
    }

    /** Ancêtres connus du client, du plus proche au plus lointain ; s'arrête au premier absent. */
    public List<SpaceObject> ancestors(UUID uuid) {
        List<SpaceObject> out = new ArrayList<>();
        SpaceObject o = objects.get(uuid);
        for (o = o == null || o.getParent() == null ? null : objects.get(o.getParent()); o != null;
                o = o.getParent() == null ? null : objects.get(o.getParent())) out.add(o);
        return out;
    }
}
