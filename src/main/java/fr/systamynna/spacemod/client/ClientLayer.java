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
import net.minecraft.nbt.CompoundTag;

/**
 * Couche stratégique du client : réplique (lecture seule) des objets auxquels il est abonné, alimentée par {@link SpaceNet}.
 * Thread client uniquement. Instance distincte de {@link StrategicLayer} car en solo client et serveur partagent la JVM.
 */
public final class ClientLayer {
    public static final ClientLayer INSTANCE = new ClientLayer();

    private final Map<UUID, SpaceObject> objects = new HashMap<>();

    ClientLayer() {}

    /** Ajoute ou remplace l'objet par l'état reçu du serveur. */
    public void apply(UUID uuid, @Nullable UUID parent, CompoundTag data) {
        objects.put(uuid, SpaceObject.load(uuid, parent, data));
    }

    public void drop(UUID uuid) { objects.remove(uuid); }

    public void clear() { objects.clear(); }

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
