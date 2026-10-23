package fr.systamynna.spacemod.ship;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import fr.systamynna.spacemod.strategic.Archetypes;
import fr.systamynna.spacemod.strategic.ExclusionResolver;
import fr.systamynna.spacemod.strategic.RailMath;
import fr.systamynna.spacemod.strategic.SphereExclusion;
import fr.systamynna.spacemod.strategic.SpaceObject;
import fr.systamynna.spacemod.strategic.StrategicLayer;
import fr.systamynna.spacemod.strategic.component.ComponentTypes;
import fr.systamynna.spacemod.strategic.component.ExclusionVolume;
import fr.systamynna.spacemod.strategic.component.Pilotable;
import fr.systamynna.spacemod.strategic.component.Transform;

/**
 * Un tick de vie d'un vaisseau : vol, contacts avec les volumes d'exclusion, puis choix du parent. Ne dépend que de la
 * couche (pas du serveur), donc testable en mémoire.
 * shortcut: un vaisseau hors de tout système n'a ni obstacles ni re-parentage ; la vitesse n'est pas convertie au
 * changement de parent (l'écart entre repères voisins est négligeable devant la vitesse maximale).
 */
public final class ShipSimulation {
    /** Moteur de contacts ; remplaçable par un adaptateur Sable / Rapier sans toucher au reste. */
    public static volatile ExclusionResolver RESOLVER = new SphereExclusion();

    /** Vaisseaux dont S est verrouillé (voir {@link ShipFlight.State#reverseLocked()}). */
    private static final Set<UUID> REVERSE_LOCKED = new HashSet<>();

    /** Classe utilitaire. */
    private ShipSimulation() {}

    /** Oublie l'état de commande d'un vaisseau (fin de pilotage, suppression). */
    public static void forget(UUID shipId) {
        REVERSE_LOCKED.remove(shipId);
    }

    /**
     * Avance le vaisseau d'un tick.
     *
     * @param spaceTime temps spatial courant
     * @param checkParent {@code true} pour rechercher aussi l'astre le plus proche
     * @param hysteresis fraction dont un astre doit être plus proche pour devenir le parent
     * @return {@code true} si le vaisseau bouge encore (vitesse non nulle) après ce tick
     */
    public static boolean tick(StrategicLayer layer, SpaceObject ship, ShipFlight.Input input, ShipFlight.Limits limits,
            double spaceTime, boolean checkParent, double hysteresis) {
        Pilotable pilot = ship.controller() instanceof Pilotable p ? p : Pilotable.AT_REST;
        double[] v = { pilot.vx(), pilot.vy(), pilot.vz() };
        Transform pose = ship.transform();
        boolean moving = ShipFlight.norm(v) > 0;
        if (!moving && input.equals(ShipFlight.Input.ZERO)) return false;

        ShipFlight.State state = ShipFlight.step(new ShipFlight.State(pose, v, REVERSE_LOCKED.contains(ship.id())), input, limits);
        if (state.reverseLocked()) REVERSE_LOCKED.add(ship.id());
        else REVERSE_LOCKED.remove(ship.id());

        double[] position = state.pose().translation();
        double[] velocity = state.velocity();
        SpaceObject system = layer.systemOf(ship.id());
        if (system != null) {
            List<ExclusionResolver.Obstacle> obstacles = obstacles(layer, system, ship, pose, spaceTime);
            ExclusionVolume own = ship.get(ComponentTypes.EXCLUSION_VOLUME);
            double selfRadius = own == null ? 0 : own.radius();
            ExclusionResolver.Result result = RESOLVER.resolve(pose.translation(), position, velocity, selfRadius, obstacles);
            position = result.position();
            velocity = result.velocity();
        }
        Transform next = state.pose().withTranslation(position[0], position[1], position[2]);
        ship.put(next);
        ship.controller(new Pilotable(velocity[0], velocity[1], velocity[2]));

        if (checkParent && system != null) reparentToNearest(layer, ship, system, spaceTime, hysteresis);
        return ShipFlight.norm(velocity) > 0;
    }

    /** Volumes qui repoussent ce vaisseau, centrés dans le référentiel de son parent. */
    private static List<ExclusionResolver.Obstacle> obstacles(StrategicLayer layer, SpaceObject system, SpaceObject ship,
            Transform pose, double spaceTime) {
        boolean large = ship.get(ComponentTypes.IDENTITY).archetype().equals(Archetypes.SHIP);
        List<ExclusionResolver.Obstacle> obstacles = new ArrayList<>();
        for (SpaceObject body : bodies(layer, system, ship)) {
            ExclusionVolume zone = body.get(ComponentTypes.EXCLUSION_VOLUME);
            if (zone == null || body.isDestroyed()) continue;
            // une navette ignore les zones réservées aux grands vaisseaux
            if (zone.scope() == ExclusionVolume.Scope.LARGE_ONLY && !large) continue;
            double[] rel = RailMath.relativePosition(layer, ship, body, spaceTime);
            double[] at = pose.translation();
            obstacles.add(new ExclusionResolver.Obstacle(zone.shape(), new double[] { at[0] - rel[0], at[1] - rel[1], at[2] - rel[2] }));
        }
        return obstacles;
    }

    /** Corps chargés du système, sans le vaisseau ni ses descendants. */
    private static List<SpaceObject> bodies(StrategicLayer layer, SpaceObject system, SpaceObject ship) {
        List<SpaceObject> bodies = new ArrayList<>();
        Deque<SpaceObject> queue = new ArrayDeque<>();
        queue.add(system);
        while (!queue.isEmpty()) {
            SpaceObject o = queue.poll();
            if (o != system) bodies.add(o);
            for (UUID child : o.children()) {
                if (child.equals(ship.id())) continue;
                SpaceObject c = layer.find(child);
                if (c != null) queue.add(c);
            }
        }
        return bodies;
    }

    private static void reparentToNearest(StrategicLayer layer, SpaceObject ship, SpaceObject system, double spaceTime,
            double hysteresis) {
        Map<UUID, Double> distances = new HashMap<>();
        for (SpaceObject body : bodies(layer, system, ship)) {
            // seuls les astres sont candidats : ni les vaisseaux, ni les objets détruits ou sans zone d'exclusion
            if (body.isDestroyed() || !body.has(ComponentTypes.EXCLUSION_VOLUME) || ShipRegistry.isShip(body)) continue;
            double[] rel = RailMath.relativePosition(layer, ship, body, spaceTime);
            distances.put(body.id(), Math.sqrt(rel[0] * rel[0] + rel[1] * rel[1] + rel[2] * rel[2]));
        }
        @Nullable UUID next = ShipNavigator.chooseParent(ship.parent(), distances, hysteresis);
        if (next != null) layer.reparent(ship.id(), next, spaceTime);
    }
}
