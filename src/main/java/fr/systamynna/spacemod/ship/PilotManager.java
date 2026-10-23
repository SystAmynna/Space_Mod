package fr.systamynna.spacemod.ship;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fr.systamynna.spacemod.network.PilotInputPacket;
import fr.systamynna.spacemod.network.PilotStatePacket;
import fr.systamynna.spacemod.strategic.SpaceClock;
import fr.systamynna.spacemod.strategic.SpaceObject;
import fr.systamynna.spacemod.strategic.StrategicLayer;
import fr.systamynna.spacemod.strategic.component.Pilotable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Qui pilote quoi. Un joueur pilote un vaisseau s'il est dans sa dimension ; le serveur ne fait confiance qu'aux
 * commandes d'un pilote enregistré, les borne, et libère le pilotage si le client se tait, si le joueur quitte la
 * dimension du vaisseau, meurt, se déconnecte ou si le vaisseau disparaît. Le vaisseau libéré garde sa vitesse (dérive).
 */
public final class PilotManager {
    /** Sans nouvelle commande depuis ce nombre de ticks, la commande retombe à zéro. */
    private static final int INPUT_TIMEOUT_TICKS = 60;
    /** Sans nouvelle commande depuis ce nombre de ticks, le pilotage est libéré. */
    private static final int RELEASE_TIMEOUT_TICKS = 200;

    private static final class Pilot {
        final UUID ship;
        long lastInputTick;
        boolean silenced;

        Pilot(UUID ship, long now) {
            this.ship = ship;
            this.lastInputTick = now;
        }
    }

    /** Pilote de chaque joueur. */
    private static final Map<UUID, Pilot> PILOTS = new ConcurrentHashMap<>();

    /** Classe utilitaire. */
    private PilotManager() {}

    /**
     * Fait de ce joueur le pilote du vaisseau.
     *
     * @throws IllegalArgumentException si l'objet n'est pas pilotable, si le joueur n'est pas dans la dimension du vaisseau
     *         ou si un autre joueur le pilote déjà
     */
    public static void start(ServerPlayer player, SpaceObject ship) {
        if (!(ship.controller() instanceof Pilotable) || !ShipRegistry.isShip(ship)) {
            throw new IllegalArgumentException("Cet objet n'est pas un vaisseau pilotable");
        }
        SpaceObject host = StrategicLayer.get().hostOf(player.level().dimension());
        if (host == null || !host.id().equals(ship.id())) {
            throw new IllegalArgumentException(player.getGameProfile().getName() + " doit être dans le vaisseau (sa dimension)");
        }
        Pilot current = PILOTS.get(player.getUUID());
        if (current != null && current.ship.equals(ship.id())) return;
        if (ShipManager.isPiloted(ship.id())) throw new IllegalArgumentException("Ce vaisseau a déjà un pilote");
        if (current != null) stop(player.server, player.getUUID());
        PILOTS.put(player.getUUID(), new Pilot(ship.id(), SpaceClock.gameTime(player.server)));
        ShipManager.control(ship.id(), ShipFlight.Input.ZERO);
        PacketDistributor.sendToPlayer(player, new PilotStatePacket(true, ship.id()));
    }

    /** Commandes reçues d'un client : ignorées si le joueur n'est pas pilote, bornées sinon. */
    public static void onInput(ServerPlayer player, PilotInputPacket p) {
        Pilot pilot = PILOTS.get(player.getUUID());
        if (pilot == null) return;
        if ((p.flags() & PilotInputPacket.RELEASE) != 0) {
            stop(player.server, player.getUUID());
            return;
        }
        pilot.lastInputTick = SpaceClock.gameTime(player.server);
        pilot.silenced = false;
        ShipManager.control(pilot.ship, new ShipFlight.Input(p.throttle() / 127.0, p.roll() / 127.0, p.yaw() / 127.0,
                p.pitch() / 127.0, (p.flags() & PilotInputPacket.BRAKE) != 0).clamp());
    }

    /** Libère le pilotage de ce joueur (sans effet s'il ne pilote pas) ; le vaisseau dérive. */
    public static void stop(MinecraftServer server, UUID playerId) {
        Pilot pilot = PILOTS.remove(playerId);
        if (pilot == null) return;
        ShipManager.release(pilot.ship);
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) PacketDistributor.sendToPlayer(player, new PilotStatePacket(false, pilot.ship));
    }

    /** Libère le pilote de ce vaisseau, s'il en a un (suppression du vaisseau). */
    public static void stopShip(MinecraftServer server, UUID shipId) {
        for (Map.Entry<UUID, Pilot> e : new ArrayList<>(PILOTS.entrySet())) {
            if (e.getValue().ship.equals(shipId)) stop(server, e.getKey());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PILOTS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        StrategicLayer layer = StrategicLayer.get();
        long now = SpaceClock.gameTime(server);
        for (Map.Entry<UUID, Pilot> e : new ArrayList<>(PILOTS.entrySet())) {
            Pilot pilot = e.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            SpaceObject host = player == null ? null : layer.hostOf(player.level().dimension());
            if (player == null || !player.isAlive() || host == null || !host.id().equals(pilot.ship)
                    || now - pilot.lastInputTick > RELEASE_TIMEOUT_TICKS) {
                stop(server, e.getKey());
            } else if (!pilot.silenced && now - pilot.lastInputTick > INPUT_TIMEOUT_TICKS) {
                pilot.silenced = true;
                ShipManager.control(pilot.ship, ShipFlight.Input.ZERO);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player.server, player.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PILOTS.clear();
    }
}
