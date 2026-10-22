package fr.systamynna.spacemod.client;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.network.SkyExtrasPacket;
import fr.systamynna.spacemod.network.SystemSnapshotPacket;
import fr.systamynna.spacemod.strategic.RailMath;
import fr.systamynna.spacemod.strategic.SpaceObject;
import fr.systamynna.spacemod.strategic.component.ComponentTypes;
import fr.systamynna.spacemod.strategic.component.StellarSystem;
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
