package fr.systamynna.spacemod.strategic.component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import fr.systamynna.spacemod.strategic.component.hostedPropriety.PortPoint;
import org.jetbrains.annotations.Nullable;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * L'objet peut avoir une dimension persistante : seuls les objets qui portent ce composant en ont une. Il contient la
 * clé de la dimension, ses paramètres physiques, la pose de son rendu dans le système stellaire, le rendu du ciel et
 * ses portes ({@link PortPoint}), ainsi que l'état transitoire de la dimension ({@link State}, non sauvé). La
 * dimension est créée au premier accès (voir {@code DimensionManager}) : tant qu'elle ne l'est pas, la clé est {@code null}.
 */
public final class HostedDimension implements Component {
    /** Rendu de ciel par défaut : le système de l'hôte, vu depuis la dimension. {@code amynnaspacemod:black} = ciel noir. */
    public static final ResourceLocation DEFAULT_RENDER = ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, "system");

    private final @Nullable ResourceLocation dimension;
    private final Properties properties;
    private final Transform placement;
    private final ResourceLocation render;
    private final List<PortPoint> ports;
    private final State state;

    /**
     * @param dimension clé de la dimension hébergée, ou {@code null} si elle n'est pas encore créée ; unique dans la couche
     *        (une dimension n'a qu'un hôte)
     * @param properties paramètres physiques de la dimension
     * @param placement position (translation) du rendu dans le système stellaire et rotation relative à la direction 0 de l'objet
     * @param render identifiant du rendu de ciel vu de l'intérieur ; {@link #DEFAULT_RENDER} = ciel du système
     * @param ports portes de la dimension, copiées de façon immuable ; au moins une
     */
    public HostedDimension(@Nullable ResourceLocation dimension, Properties properties, Transform placement,
            ResourceLocation render, List<PortPoint> ports) {
        this(dimension, properties, placement, render, ports, new State());
    }

    private HostedDimension(@Nullable ResourceLocation dimension, Properties properties, Transform placement,
            ResourceLocation render, List<PortPoint> ports, State state) {
        if (ports.isEmpty()) throw new IllegalArgumentException("une dimension a au moins un port");
        this.dimension = dimension;
        this.properties = properties;
        this.placement = placement;
        this.render = render;
        this.ports = List.copyOf(ports);
        this.state = state;
    }

    /** Dimension à créer, avec placement neutre, ciel noir et un port « principal » par défaut. */
    public HostedDimension(@Nullable ResourceLocation dimension, Properties properties) {
        this(dimension, properties, Transform.ORIGIN, DEFAULT_RENDER, List.of(PortPoint.main(PortPoint.DEFAULT_RADIUS)));
    }

    /** Dimension déjà créée, aux paramètres de l'espace. */
    public HostedDimension(ResourceLocation dimension) {
        this(dimension, Properties.SPACE);
    }

    /** Clé de la dimension, ou {@code null} si elle n'est pas encore créée. */
    public @Nullable ResourceLocation dimension() {
        return dimension;
    }

    /** Paramètres physiques de la dimension. */
    public Properties properties() {
        return properties;
    }

    /** Pose du rendu dans le système stellaire : translation, et rotation relative à la direction 0 de l'objet. */
    public Transform placement() {
        return placement;
    }

    /** Identifiant du rendu de ciel vu de l'intérieur. */
    public ResourceLocation render() {
        return render;
    }

    /** Portes de la dimension. */
    public List<PortPoint> ports() {
        return ports;
    }

    /** État transitoire de la dimension, partagé par les copies de ce composant. */
    public State state() {
        return state;
    }

    /** Copie avec une autre clé de dimension ; l'état est partagé. */
    public HostedDimension withDimension(@Nullable ResourceLocation key) {
        return new HostedDimension(key, properties, placement, render, ports, state);
    }

    /** Copie avec un autre rendu de ciel ; l'état est partagé. */
    public HostedDimension withRender(ResourceLocation id) {
        return new HostedDimension(dimension, properties, placement, id, ports, state);
    }

    /** Port de ce nom, ou {@code null}. */
    public @Nullable PortPoint port(String name) {
        return ports.stream().filter(p -> p.name().equals(name)).findFirst().orElse(null);
    }

    /**
     * Rotation du ciel vu depuis un port : celle de {@link #placement()} suivie de celle du port (produit de Hamilton,
     * la rotation du port s'applique d'abord).
     *
     * @return quaternion {@code {x, y, z, w}}, non normalisé
     */
    public double[] skyRotation(PortPoint port) {
        double ax = placement.qx(), ay = placement.qy(), az = placement.qz(), aw = placement.qw();
        double bx = port.qx(), by = port.qy(), bz = port.qz(), bw = port.qw();
        return new double[] {
                aw * bx + ax * bw + ay * bz - az * by,
                aw * by - ax * bz + ay * bw + az * bx,
                aw * bz + ax * by - ay * bx + az * bw,
                aw * bw - ax * bx - ay * by - az * bz };
    }

    /** État de la dimension, tenu par le {@code DimensionManager} ; jamais sauvé : au redémarrage tout est inactif. */
    public static final class State {
        private boolean active;
        private final Set<UUID> players = new HashSet<>();
        private long lastExitTick;

        /** La dimension est-elle chargée ? */
        public synchronized boolean active() {
            return active;
        }

        /** Marque la dimension chargée ou non. */
        public synchronized void setActive(boolean active) {
            this.active = active;
        }

        /** Nombre de joueurs dans la dimension. */
        public synchronized int playerCount() {
            return players.size();
        }

        /** Un joueur entre. */
        public synchronized void enter(UUID player) {
            players.add(player);
        }

        /** Un joueur sort ; si c'était le dernier, la date de sortie est notée. */
        public synchronized void leave(UUID player, long gameTime) {
            if (players.remove(player) && players.isEmpty()) lastExitTick = gameTime;
        }

        /** Remplace les joueurs présents (resynchronisation avec le niveau réel). */
        public synchronized void resync(Set<UUID> present, long gameTime) {
            players.clear();
            players.addAll(present);
            lastExitTick = gameTime;
        }

        /** Date (temps de jeu) à laquelle le dernier joueur est sorti, ou du réveil si aucun n'est jamais entré. */
        public synchronized long lastExitTick() {
            return lastExitTick;
        }

        /** Note la date de référence de l'inactivité (réveil). */
        public synchronized void touch(long gameTime) {
            lastExitTick = gameTime;
        }
    }

    /**
     * Paramètres physiques d'une dimension.
     *
     * @param gx gravité de base, en X
     * @param gy idem en Y
     * @param gz idem en Z
     * @param pressure pression de base
     * @param drag traînée universelle
     * @param nx nord magnétique, en X
     * @param ny idem en Y
     * @param nz idem en Z
     */
    public record Properties(float gx, float gy, float gz, double pressure, float drag, float nx, float ny, float nz) {
        /** L'espace : ni gravité, ni pression, ni traînée. */
        public static final Properties SPACE = new Properties(0, 0, 0, 0, 0, 0, 0, 1);

        /** Écrit les paramètres en NBT. */
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putFloat("gx", gx);
            tag.putFloat("gy", gy);
            tag.putFloat("gz", gz);
            tag.putDouble("pressure", pressure);
            tag.putFloat("drag", drag);
            tag.putFloat("nx", nx);
            tag.putFloat("ny", ny);
            tag.putFloat("nz", nz);
            return tag;
        }

        /** Relit des paramètres écrits par {@link #save()}. */
        static Properties load(CompoundTag tag) {
            return new Properties(tag.getFloat("gx"), tag.getFloat("gy"), tag.getFloat("gz"), tag.getDouble("pressure"),
                    tag.getFloat("drag"), tag.getFloat("nx"), tag.getFloat("ny"), tag.getFloat("nz"));
        }
    }

    /** Identifiant du type de ce composant dans le registre {@link ComponentTypes}. */
    @Override
    public ResourceLocation typeId() {
        return ComponentTypes.HOSTED_DIMENSION.id();
    }

    /** Écrit le composant en NBT ; relu à l'identique par {@link #load}. La clé est absente tant que la dimension n'existe pas. */
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (dimension != null) tag.putString("dimension", dimension.toString());
        tag.put("properties", properties.save());
        tag.put("placement", placement.save());
        tag.putString("render", render.toString());
        ListTag list = new ListTag();
        for (PortPoint port : ports) list.add(port.save());
        tag.put("ports", list);
        return tag;
    }

    /** Relit un composant écrit par {@link #save()} ; les champs absents (ancien composant) prennent leurs valeurs par défaut. */
    public static HostedDimension load(CompoundTag tag) {
        ResourceLocation key = tag.contains("dimension") ? ResourceLocation.parse(tag.getString("dimension")) : null;
        Properties props = tag.contains("properties") ? Properties.load(tag.getCompound("properties")) : Properties.SPACE;
        Transform placement = tag.contains("placement") ? Transform.load(tag.getCompound("placement")) : Transform.ORIGIN;
        ResourceLocation render = tag.contains("render") ? ResourceLocation.parse(tag.getString("render")) : DEFAULT_RENDER;
        List<PortPoint> ports = tag.getList("ports", Tag.TAG_COMPOUND).stream().map(t -> PortPoint.load((CompoundTag) t)).toList();
        if (ports.isEmpty()) ports = List.of(PortPoint.main(PortPoint.DEFAULT_RADIUS));
        return new HostedDimension(key, props, placement, render, ports);
    }

    /** Égalité sur les données sauvées ; l'état transitoire n'en fait pas partie. */
    @Override
    public boolean equals(Object o) {
        return o instanceof HostedDimension h && Objects.equals(dimension, h.dimension) && properties.equals(h.properties)
                && placement.equals(h.placement) && render.equals(h.render) && ports.equals(h.ports);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, properties, placement, render, ports);
    }
}
