package fr.systamynna.spacemod.client;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.network.ShipPosePacket;
import fr.systamynna.spacemod.network.SkyExtrasPacket;
import fr.systamynna.spacemod.network.SystemSnapshotPacket;
import fr.systamynna.spacemod.strategic.RailMath;
import fr.systamynna.spacemod.strategic.SpaceObject;
import fr.systamynna.spacemod.strategic.component.ComponentTypes;
import fr.systamynna.spacemod.strategic.component.Pilotable;
import fr.systamynna.spacemod.strategic.component.StellarSystem;
import fr.systamynna.spacemod.strategic.component.Transform;
import net.minecraft.nbt.CompoundTag;

/**
 * Réplique client du système de l'hôte, telle que {@link SystemSnapshotPacket} l'a envoyée : objets immuables et ancre
 * de l'horloge spatiale. Un nouveau paquet remplace tout. Aucune classe client de Minecraft : chargeable partout.
 */
public final class ClientSpace implements RailMath.Lookup {
    private static volatile ClientSpace current;

    private final UUID hostId;
    private final long anchorGameTick;
    private final double anchorSpaceTicks;
    private final double rate;
    private volatile int seed;
    private final Map<UUID, SpaceObject> objects = new ConcurrentHashMap<>();
    private final List<SpaceObject> renderables = new CopyOnWriteArrayList<>();
    /** Dernière pose reçue de chaque vaisseau, d'où {@link #extrapolate} part. */
    private final Map<UUID, ShipPosePacket> poses = new ConcurrentHashMap<>();
    /** Au-delà de ce nombre de ticks sans nouveau paquet, la position cesse d'être extrapolée. */
    private static final int MAX_EXTRAPOLATION_TICKS = 20;
    @Nullable
    private final SpaceObject mainStar;

    private ClientSpace(SystemSnapshotPacket p) {
        hostId = p.hostId();
        anchorGameTick = p.anchorGameTick();
        anchorSpaceTicks = p.anchorSpaceTicks();
        rate = p.rate();
        for (CompoundTag tag : p.objects()) {
            SpaceObject o = SpaceObject.load(tag);
            objects.put(o.id(), o);
        }
        SpaceObject star = null;
        for (SpaceObject o = objects.get(hostId); o != null; o = o.parent() == null ? null : objects.get(o.parent())) {
            StellarSystem system = o.get(ComponentTypes.STELLAR_SYSTEM);
            if (system != null) {
                star = system.mainStar() == null ? null : objects.get(system.mainStar());
                break;
            }
        }
        mainStar = star;
        for (SpaceObject o : objects.values()) {
            if (o.id().equals(hostId) || o.isDestroyed() || !o.has(ComponentTypes.RENDER)) continue;
            renderables.add(o);
        }
    }

    /** Remplace la réplique ; un paquet illisible est ignoré (la précédente est conservée). */
    public static void set(SystemSnapshotPacket packet) {
        try {
            current = new ClientSpace(packet);
        } catch (RuntimeException e) {
            AmynnaSpaceMod.LOGGER.warn("Snapshot de système illisible, ignoré", e);
        }
    }

    /** Ajoute le décor lointain à la réplique courante ; sans snapshot préalable (ou illisible), il est ignoré. */
    public static void addExtras(SkyExtrasPacket packet) {
        ClientSpace space = current;
        if (space == null) return;
        try {
            space.seed = packet.seed();
            for (CompoundTag tag : packet.objects()) {
                SpaceObject o = SpaceObject.load(tag);
                space.objects.put(o.id(), o);
                if (!o.isDestroyed() && o.has(ComponentTypes.RENDER)) space.renderables.add(o);
            }
        } catch (RuntimeException e) {
            AmynnaSpaceMod.LOGGER.warn("Décor du ciel illisible, ignoré", e);
        }
    }

    /**
     * Applique la pose d'un vaisseau ; un changement de parent reconstruit l'objet (le parent n'est pas modifiable). Un
     * vaisseau absent du snapshot est ignoré : la création ou la suppression d'un vaisseau renvoie un snapshot complet.
     */
    public static void applyPose(ShipPosePacket pose) {
        ClientSpace space = current;
        SpaceObject ship = space == null ? null : space.objects.get(pose.id());
        if (ship == null) return;
        if (!Objects.equals(ship.parent(), pose.parent())) {
            CompoundTag tag = ship.save();
            if (pose.parent() == null) tag.remove("parent");
            else tag.putUUID("parent", pose.parent());
            SpaceObject moved = SpaceObject.load(tag);
            space.objects.put(moved.id(), moved);
            int index = space.renderables.indexOf(ship);
            if (index >= 0) space.renderables.set(index, moved);
            ship = moved;
        }
        space.poses.put(pose.id(), pose);
        ship.put(new Transform(pose.tx(), pose.ty(), pose.tz(), pose.qx(), pose.qy(), pose.qz(), pose.qw(), 1, 1, 1));
        ship.controller(new Pilotable(pose.vx(), pose.vy(), pose.vz()));
    }

    /** Avance les vaisseaux de leur vitesse depuis leur dernière pose, pour un mouvement continu entre deux paquets. */
    public void extrapolate(double gameTime) {
        for (ShipPosePacket pose : poses.values()) {
            SpaceObject ship = objects.get(pose.id());
            if (ship == null) continue;
            double dt = Math.max(0, Math.min(MAX_EXTRAPOLATION_TICKS, gameTime - pose.gameTick()));
            ship.put(new Transform(pose.tx() + pose.vx() * dt, pose.ty() + pose.vy() * dt, pose.tz() + pose.vz() * dt,
                    pose.qx(), pose.qy(), pose.qz(), pose.qw(), 1, 1, 1));
        }
    }

    /** Graine du motif de fond de la dimension. */
    public int seed() {
        return seed;
    }

    /** Réplique courante, ou {@code null} si aucun snapshot n'a été reçu. */
    public static @Nullable ClientSpace get() {
        return current;
    }

    public UUID hostId() {
        return hostId;
    }

    /** L'hôte, ou {@code null} s'il manque au snapshot. */
    public @Nullable SpaceObject host() {
        return objects.get(hostId);
    }

    /** Astre principal du système de l'hôte, ou {@code null}. */
    public @Nullable SpaceObject mainStar() {
        return mainStar;
    }

    /** Galaxie de l'hôte : racine de l'arbre en remontant les parents, ou {@code null} si le snapshot n'en contient pas. */
    public @Nullable SpaceObject galaxy() {
        SpaceObject o = objects.get(hostId);
        for (int depth = 0; o != null && o.parent() != null && depth < 64; depth++) o = objects.get(o.parent());
        return o;
    }

    /** Objets à dessiner : porteurs d'un {@code Render}, hôte exclu. */
    public List<SpaceObject> renderables() {
        return renderables;
    }

    @Override
    public @Nullable SpaceObject find(UUID objectId) {
        return objects.get(objectId);
    }

    /** Temps spatial au temps de jeu donné (fractionnaire), même formule que {@code SpaceClock.spaceTime}. */
    public double spaceTime(double gameTime) {
        return anchorSpaceTicks + (gameTime - anchorGameTick) * rate;
    }
}
