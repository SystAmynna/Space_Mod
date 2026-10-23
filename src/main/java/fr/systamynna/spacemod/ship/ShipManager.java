package fr.systamynna.spacemod.ship;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import fr.systamynna.spacemod.strategic.Archetypes;
import fr.systamynna.spacemod.strategic.DimensionManager;
import fr.systamynna.spacemod.strategic.SpaceConfig;
import fr.systamynna.spacemod.strategic.SpaceObject;
import fr.systamynna.spacemod.strategic.StrategicLayer;
import fr.systamynna.spacemod.strategic.component.ComponentTypes;
import fr.systamynna.spacemod.strategic.component.ExclusionVolume;
import fr.systamynna.spacemod.strategic.SpaceClock;
import fr.systamynna.spacemod.strategic.component.Pilotable;
import fr.systamynna.spacemod.strategic.component.Transform;
import fr.systamynna.spacemod.strategic.component.hostedPropriety.PortPoint;
import fr.systamynna.spacemod.network.ShipPosePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Création et suppression des vaisseaux (le cœur, {@code spawn} et {@code despawn}, ne dépend que de la couche), et
 * simulation à chaque tick des vaisseaux actifs : ceux qu'un pilote commande et ceux qui dérivent encore.
 */
public final class ShipManager {
    /** Période, en ticks, d'envoi de la pose aux joueurs qui ne sont pas à bord. */
    private static final int OBSERVER_PERIOD_TICKS = 5;
    /** Vaisseaux à simuler : pilotés, ou de vitesse non nulle. Retrouvés au démarrage dans l'index des noms. */
    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();
    /** Dernière commande de chaque vaisseau piloté ; absent = sans pilote. */
    private static final Map<UUID, ShipFlight.Input> INPUTS = new ConcurrentHashMap<>();

    /** Classe utilitaire. */
    private ShipManager() {}

    /** Ajoute le vaisseau aux vaisseaux simulés à chaque tick. */
    public static void activate(UUID shipId) {
        ACTIVE.add(shipId);
    }

    /** Commande du pilote pour le prochain tick ; active le vaisseau. */
    public static void control(UUID shipId, ShipFlight.Input input) {
        INPUTS.put(shipId, input);
        ACTIVE.add(shipId);
    }

    /** Le vaisseau n'a plus de pilote : il garde sa vitesse et dérive. */
    public static void release(UUID shipId) {
        INPUTS.remove(shipId);
        ShipSimulation.forget(shipId);
    }

    /** {@code true} si un pilote commande ce vaisseau. */
    public static boolean isPiloted(UUID shipId) {
        return INPUTS.containsKey(shipId);
    }

    /** Remplit les vaisseaux actifs : ceux de l'index qui ont une vitesse non nulle. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        StrategicLayer layer = StrategicLayer.get();
        for (String name : ShipRegistry.names(layer)) {
            SpaceObject ship = ShipRegistry.find(layer, name);
            if (ship != null && ship.controller() instanceof Pilotable p && (p.vx() != 0 || p.vy() != 0 || p.vz() != 0)) {
                ACTIVE.add(ship.id());
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        INPUTS.clear();
    }

    /** Un tick de vol pour chaque vaisseau actif ; un vaisseau immobile et sans pilote sort de la liste. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        StrategicLayer layer = StrategicLayer.get();
        long gameTime = SpaceClock.gameTime(event.getServer());
        double spaceTime = layer.clock().spaceTime(gameTime);
        boolean checkParent = gameTime % SpaceConfig.SHIP_PARENT_CHECK_PERIOD_TICKS.get() == 0;
        ShipFlight.Limits limits = ShipFlight.Limits.fromConfig();
        double hysteresis = SpaceConfig.SHIP_PARENT_HYSTERESIS.get();
        List<Observer> observers = null;
        for (UUID id : new ArrayList<>(ACTIVE)) {
            // jamais de référence gardée d'un tick à l'autre : l'unité du vaisseau a pu être déchargée
            SpaceObject ship = layer.find(id);
            if (ship == null) {
                ACTIVE.remove(id);
                INPUTS.remove(id);
                continue;
            }
            boolean moving = ShipSimulation.tick(layer, ship, INPUTS.getOrDefault(id, ShipFlight.Input.ZERO), limits, spaceTime,
                    checkParent, hysteresis);
            if (observers == null) observers = observers(event.getServer(), layer);
            broadcast(ship, observers, layer, gameTime, !moving);
            if (!moving && !INPUTS.containsKey(id)) ACTIVE.remove(id);
        }
    }

    /** Joueur et objet dont il occupe la dimension. */
    private record Observer(ServerPlayer player, UUID host, @Nullable UUID system) {}

    private static List<Observer> observers(MinecraftServer server, StrategicLayer layer) {
        List<Observer> observers = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SpaceObject host = layer.hostOf(player.level().dimension());
            if (host != null) observers.add(new Observer(player, host.id(), systemId(layer, host.id())));
        }
        return observers;
    }

    /**
     * Envoie la pose du vaisseau : à chaque tick à ses occupants, tous les {@value #OBSERVER_PERIOD_TICKS} ticks (et à
     * l'arrêt, pour que le dernier point soit exact) aux joueurs des autres objets du même système.
     */
    private static void broadcast(SpaceObject ship, List<Observer> observers, StrategicLayer layer, long gameTime, boolean force) {
        if (observers.isEmpty()) return;
        UUID system = systemId(layer, ship.id());
        Transform t = ship.transform();
        Pilotable p = ship.controller() instanceof Pilotable pilotable ? pilotable : Pilotable.AT_REST;
        ShipPosePacket packet = new ShipPosePacket(ship.id(), ship.parent(), t.tx(), t.ty(), t.tz(), t.qx(), t.qy(), t.qz(), t.qw(),
                p.vx(), p.vy(), p.vz(), gameTime);
        for (Observer o : observers) {
            boolean aboard = o.host().equals(ship.id());
            boolean sameSystem = system != null && system.equals(o.system());
            if (aboard || (sameSystem && (force || gameTime % OBSERVER_PERIOD_TICKS == 0))) {
                PacketDistributor.sendToPlayer(o.player(), packet);
            }
        }
    }

    /**
     * Crée un vaisseau à côté de {@code other}, qui devient son parent : il est posé sur l'axe X du parent, juste hors du
     * volume d'exclusion de {@code other}. La couche est sauvée tout de suite pour que l'index des noms et la base concordent.
     *
     * @param margin distance entre les deux volumes d'exclusion
     * @throws IllegalArgumentException si le nom est invalide ou pris, ou si {@code other} est un cadre, détruit ou sans exclusion
     */
    public static SpaceObject spawn(StrategicLayer layer, String name, SpaceObject other, boolean large, double margin) {
        if (!ShipRegistry.validName(name)) throw new IllegalArgumentException("Nom invalide (A-Z a-z 0-9 _ . + -, 48 max) : " + name);
        UUID id = ShipRegistry.uuid(name);
        if (layer.find(id) != null) throw new IllegalArgumentException("Un vaisseau porte déjà ce nom : " + name);
        ExclusionVolume otherZone = other.get(ComponentTypes.EXCLUSION_VOLUME);
        if (other.has(ComponentTypes.FRAME) || other.isDestroyed() || otherZone == null) {
            throw new IllegalArgumentException("Il faut un astre ou un vaisseau (avec zone d'exclusion), pas un cadre ni un objet détruit");
        }
        SpaceObject ship = Archetypes.ship(id, name, Transform.ORIGIN, PortPoint.DEFAULT_RADIUS, large);
        double shipRadius = ship.get(ComponentTypes.EXCLUSION_VOLUME).radius();
        // un rang de plus par enfant déjà présent, pour que deux vaisseaux créés au même endroit ne se chevauchent pas
        double distance = otherZone.radius() + shipRadius + margin + other.children().size() * 2 * shipRadius;
        ship.put(Transform.at(distance, 0, 0));
        layer.addObject(other.id(), ship);
        ShipRegistry.add(layer, name);
        layer.save();
        return ship;
    }

    /**
     * Supprime le vaisseau de ce nom (base et index compris). Nettoie l'index même si l'objet a disparu.
     *
     * @throws IllegalArgumentException si le vaisseau est inconnu ou a des enfants
     */
    public static void despawn(StrategicLayer layer, String name) {
        SpaceObject ship = ShipRegistry.find(layer, name);
        if (ship == null) {
            ShipRegistry.remove(layer, name);
            throw new IllegalArgumentException("Vaisseau inconnu : " + name);
        }
        layer.remove(ship.id());
        ShipRegistry.remove(layer, name);
        ACTIVE.remove(ship.id());
        release(ship.id());
    }

    /** Variante serveur de {@link #spawn} : marge de la configuration, puis les joueurs du système reçoivent le nouveau vaisseau. */
    public static SpaceObject create(MinecraftServer server, String name, SpaceObject other, boolean large) {
        StrategicLayer layer = StrategicLayer.get();
        SpaceObject ship = spawn(layer, name, other, large, SpaceConfig.SHIP_SPAWN_MARGIN.get());
        resyncSystemOf(server, layer, ship.id());
        return ship;
    }

    /** Variante serveur de {@link #despawn} : la dimension du vaisseau est supprimée, ses occupants évacués, les joueurs du système resynchronisés. */
    public static void remove(MinecraftServer server, String name) {
        StrategicLayer layer = StrategicLayer.get();
        SpaceObject ship = ShipRegistry.find(layer, name);
        if (ship == null) {
            despawn(layer, name);
            return;
        }
        if (!ship.children().isEmpty()) throw new IllegalArgumentException("Le vaisseau a des enfants : " + name);
        UUID parent = Objects.requireNonNull(ship.parent());
        PilotManager.stopShip(server, ship.id());
        DimensionManager.delete(server, ship);
        despawn(layer, name);
        resyncSystemOf(server, layer, parent);
    }

    /** Renvoie le système à chaque joueur dont la dimension est celle d'un objet du même système que {@code objectId}. */
    private static void resyncSystemOf(MinecraftServer server, StrategicLayer layer, UUID objectId) {
        UUID system = systemId(layer, objectId);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SpaceObject host = layer.hostOf(player.level().dimension());
            if (host != null && Objects.equals(system, systemId(layer, host.id()))) DimensionManager.resync(player);
        }
    }

    @Nullable
    private static UUID systemId(StrategicLayer layer, UUID objectId) {
        SpaceObject system = layer.systemOf(objectId);
        return system == null ? null : system.id();
    }
}
