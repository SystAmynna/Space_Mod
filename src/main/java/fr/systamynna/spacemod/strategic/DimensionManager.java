package fr.systamynna.spacemod.strategic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import fr.systamynna.dynamicdimensions.api.DynamicDimensionProperties;
import fr.systamynna.dynamicdimensions.api.DynamicDimensionRegistry;
import fr.systamynna.dynamicdimensions.api.PlayerRemover;
import fr.systamynna.dynamicdimensions.api.event.DynamicDimensionLoadCallback;
import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.network.SkyContextPacket;
import fr.systamynna.spacemod.network.SkyExtrasPacket;
import fr.systamynna.spacemod.network.SystemSnapshotPacket;
import fr.systamynna.spacemod.strategic.component.ComponentTypes;
import fr.systamynna.spacemod.strategic.component.HostedDimension;
import fr.systamynna.spacemod.strategic.component.Pilotable;
import fr.systamynna.spacemod.strategic.component.hostedPropriety.PortPoint;
import fr.systamynna.spacemod.strategic.component.Render;
import fr.systamynna.spacemod.strategic.component.StellarSystem;
import fr.systamynna.spacemod.strategic.component.Transform;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Gère les dimensions des objets : création au premier réveil, entrée des joueurs, suivi de leur présence et fermeture
 * des dimensions restées vides. Un volume de déclenchement ordonne le réveil, un volume logique le transport ; toutes
 * les {@code DIMENSION_CHECK_PERIOD_TICKS} ticks, une dimension sans joueur depuis plus de
 * {@code DIMENSION_IDLE_DELAY_TICKS} est fermée.
 */
public final class DimensionManager {
    /** Identifiant à mettre dans le champ {@code effects} du type de dimension ; enregistré côté client par {@code ClientEvents}. */
    public static final ResourceLocation SPACE_EFFECTS = ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, "space");
    /** Dernier port visé par chaque joueur, lu à l'arrivée dans la dimension. */
    private static final Map<UUID, String> PORTS = new ConcurrentHashMap<>();

    /** Nom du port par lequel le joueur est entré (vide si inconnu). */
    public static String portOf(UUID player) {
        return PORTS.getOrDefault(player, "");
    }
    /** Dimensions réveillées, par clé : références vers les {@link HostedDimension} des objets, dont l'état est partagé. */
    private static final Map<ResourceLocation, HostedDimension> AWAKE = new ConcurrentHashMap<>();

    /** Classe utilitaire. */
    private DimensionManager() {}

    /** {@code HostedDimension} de l'objet qui héberge cette dimension, ou {@code null} si ce n'est pas une dimension d'objet. */
    @Nullable
    public static HostedDimension hostedOf(ResourceKey<Level> dimension) {
        SpaceObject host = StrategicLayer.get().hostOf(dimension);
        return host == null ? null : host.get(ComponentTypes.HOSTED_DIMENSION);
    }

    /** Dimensions réveillées et leur {@code HostedDimension}. */
    public static Map<ResourceLocation, HostedDimension> awake() {
        return Map.copyOf(AWAKE);
    }

    /**
     * Réveille la dimension de l'objet : la crée si besoin, la recharge si elle n'est pas en mémoire.
     *
     * @throws IllegalArgumentException si l'objet n'héberge pas de dimension
     * @throws IllegalStateException si la dimension ne peut être créée ou chargée
     */
    public static ServerLevel wake(MinecraftServer server, SpaceObject object) {
        HostedDimension hosted = object.get(ComponentTypes.HOSTED_DIMENSION);
        if (hosted == null) throw new IllegalArgumentException("Cet objet n'héberge pas de dimension");
        DynamicDimensionRegistry registry = DynamicDimensionRegistry.from(server);
        ServerLevel level;
        if (hosted.dimension() == null) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID,
                    object.id().toString().replace("-", ""));
            level = registry.createDynamicDimension(id, voidGenerator(server), dimensionType(), properties(hosted.properties()));
            if (level == null) throw new IllegalStateException("Dimension non créée : " + id);
            hosted = hosted.withDimension(id);
            object.put(hosted);
        } else {
            level = server.getLevel(ResourceKey.create(Registries.DIMENSION, hosted.dimension()));
            if (level == null) {
                level = registry.loadDynamicDimension(hosted.dimension(), voidGenerator(server), dimensionType(),
                        properties(hosted.properties()));
            }
            if (level == null) throw new IllegalStateException("Dimension non chargée : " + hosted.dimension());
        }
        track(hosted, level, SpaceClock.gameTime(server));
        return level;
    }

    /** Marque la dimension active et resynchronise ses joueurs avec le niveau réel, sauf si elle l'est déjà. */
    private static void track(HostedDimension hosted, ServerLevel level, long gameTime) {
        if (AWAKE.containsKey(hosted.dimension())) return;
        Set<UUID> present = new HashSet<>();
        for (ServerPlayer p : level.players()) present.add(p.getUUID());
        hosted.state().resync(present, gameTime);
        hosted.state().setActive(true);
        AWAKE.put(hosted.dimension(), hosted);
    }

    /**
     * Fait entrer un joueur dans la dimension d'un objet, par un port. Réveille la dimension si besoin.
     *
     * @param portName nom du port, ou {@code null} pour le premier
     * @param offset position du visiteur moins celle du port dans le référentiel du parent, ou {@code null} : arrivée sur
     *        l'origine du port
     * @return la dimension atteinte
     * @throws IllegalArgumentException si l'objet n'héberge pas de dimension ou si le port n'existe pas
     * @throws IllegalStateException si la dimension ne peut être créée
     */
    public static ServerLevel enter(ServerPlayer player, SpaceObject object, @Nullable String portName,
            double @Nullable [] offset) {
        HostedDimension hosted = object.get(ComponentTypes.HOSTED_DIMENSION);
        if (hosted == null) throw new IllegalArgumentException("Cet objet n'héberge pas de dimension");
        PortPoint port = portName == null ? hosted.ports().get(0) : hosted.port(portName);
        if (port == null) throw new IllegalArgumentException("Port inconnu : " + portName);
        ServerLevel level = wake(player.server, object);
        PORTS.put(player.getUUID(), port.name());
        boolean sameLevel = player.level() == level;
        double[] at = arrival(hosted, port, offset);
        player.teleportTo(level, at[0], at[1], at[2], player.getYRot(), player.getXRot());
        if (sameLevel) sync(player);
        return level;
    }

    /** Entrée sur l'origine du port. */
    public static ServerLevel enter(ServerPlayer player, SpaceObject object, @Nullable String portName) {
        return enter(player, object, portName, null);
    }

    /**
     * Position d'arrivée dans la dimension : origine du port plus le décalage du visiteur, remis dans le repère du ciel
     * (rotation du placement composée avec celle du port), pour que la vue du modèle et celle de la structure concordent.
     * Pas de borne en hauteur.
     */
    static double[] arrival(HostedDimension hosted, PortPoint port, double @Nullable [] offset) {
        double[] origin = { port.dx(), port.dy(), port.dz() };
        if (offset == null) return origin;
        double[] q = hosted.skyRotation(port);
        // shortcut: parent et dimension à la même échelle (1 unité = 1 bloc), à revoir avec les shapes ; spin de l'hôte ignoré
        double[] v = new Transform(0, 0, 0, q[0], q[1], q[2], q[3], 1, 1, 1).invert(offset);
        return new double[] { origin[0] + v[0], origin[1] + v[1], origin[2] + v[2] };
    }

    /**
     * Supprime la dimension de l'objet, s'il en a une créée ; les joueurs présents sont évacués vers leur point d'apparition.
     * La suppression effective peut avoir lieu au tick suivant.
     */
    public static void delete(MinecraftServer server, SpaceObject object) {
        HostedDimension hosted = object.get(ComponentTypes.HOSTED_DIMENSION);
        if (hosted == null || hosted.dimension() == null) return;
        DynamicDimensionRegistry.from(server).deleteDynamicDimension(hosted.dimension(), PlayerRemover.DEFAULT);
        AWAKE.remove(hosted.dimension());
        hosted.state().setActive(false);
    }

    /** Renvoie au joueur le contexte de ciel et le système de la dimension où il se trouve, si c'est celle d'un objet. */
    public static void resync(ServerPlayer player) {
        sync(player);
    }

    /** Envoie au joueur le contexte de ciel de la dimension où il se trouve, si c'est celle d'un objet. */
    private static void sync(ServerPlayer player) {
        SpaceObject object = StrategicLayer.get().hostOf(player.level().dimension());
        if (object == null) return;
        HostedDimension hosted = object.get(ComponentTypes.HOSTED_DIMENSION);
        PortPoint port = hosted.port(PORTS.getOrDefault(player.getUUID(), ""));
        if (port == null) port = hosted.ports().get(0);
        Render render = object.get(ComponentTypes.RENDER);
        double[] q = hosted.skyRotation(port);
        PacketDistributor.sendToPlayer(player, new SkyContextPacket(object.id(),
                object.get(ComponentTypes.IDENTITY).name(), hosted.render(),
                render == null ? 0 : render.apparentRadius(), port.name(), port.x(), port.y(), port.z(),
                q[0], q[1], q[2], q[3]));
        sendSnapshot(player, object);
        sendSkyExtras(player, object);
    }

    private static final double LIGHT_YEAR = 9.4607e15;

    /**
     * Envoie le décor lointain : graine du motif de fond (celle de la dimension), autres galaxies, et étoiles à moins de
     * {@link SpaceConfig#STAR_RADIUS_LY} de l'hôte (avec leur système).
     * shortcut: balayage de tous les systèmes résidents, à remplacer par un index spatial au-delà de quelques dizaines de milliers.
     */
    private static void sendSkyExtras(ServerPlayer player, SpaceObject host) {
        StrategicLayer layer = StrategicLayer.get();
        double t = layer.clock().spaceTime(SpaceClock.gameTime(player.server));
        double max = SpaceConfig.STAR_RADIUS_LY.get() * LIGHT_YEAR;
        SpaceObject hostSystem = layer.systemOf(host.id());
        SpaceObject hostGalaxy = layer.galaxyOf(host.id());
        List<CompoundTag> objects = new ArrayList<>();
        for (SpaceObject g : layer.galaxies()) if (g != hostGalaxy && g.has(ComponentTypes.RENDER)) objects.add(g.save());
        for (SpaceObject o : layer.loadedObjects()) {
            StellarSystem s = o.get(ComponentTypes.STELLAR_SYSTEM);
            if (s == null || s.mainStar() == null || o == hostSystem) continue;
            double[] d = RailMath.relativePosition(layer, o, host, t);
            if (Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]) > max) continue;
            SpaceObject star = layer.find(s.mainStar());
            if (star == null || !star.has(ComponentTypes.RENDER)) continue;
            objects.add(o.save());
            objects.add(star.save());
        }
        PacketDistributor.sendToPlayer(player, new SkyExtrasPacket(player.level().dimension().location().toString().hashCode(), objects));
    }

    /** Envoie le système de l'hôte (ses ancêtres et les descendants de son système) et l'ancre de l'horloge spatiale. */
    private static void sendSnapshot(ServerPlayer player, SpaceObject host) {
        StrategicLayer layer = StrategicLayer.get();
        Map<UUID, CompoundTag> objects = new LinkedHashMap<>();
        for (SpaceObject o = host; o != null; o = o.parent() == null ? null : layer.find(o.parent())) objects.put(o.id(), o.save());
        SpaceObject system = layer.systemOf(host.id());
        Deque<SpaceObject> queue = new ArrayDeque<>();
        if (system != null) queue.add(system);
        while (!queue.isEmpty()) {
            SpaceObject o = queue.poll();
            objects.putIfAbsent(o.id(), o.save());
            for (UUID child : o.children()) {
                SpaceObject c = layer.find(child);
                if (c != null && !objects.containsKey(c.id())) queue.add(c);
            }
        }
        SpaceClock clock = layer.clock();
        PacketDistributor.sendToPlayer(player, new SystemSnapshotPacket(host.id(), clock.anchorGameTick(),
                clock.anchorSpaceTicks(), clock.rate(), new ArrayList<>(objects.values())));
    }

    private static void joined(ServerPlayer player, ResourceKey<Level> dimension) {
        HostedDimension hosted = hostedOf(dimension);
        if (hosted == null) return;
        hosted.state().enter(player.getUUID());
        hosted.state().setActive(true);
        if (hosted.dimension() != null) AWAKE.putIfAbsent(hosted.dimension(), hosted);
    }

    private static void left(ServerPlayer player, ResourceKey<Level> dimension) {
        HostedDimension hosted = hostedOf(dimension);
        if (hosted != null) hosted.state().leave(player.getUUID(), SpaceClock.gameTime(player.server));
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        left(player, event.getFrom());
        joined(player, event.getTo());
        sync(player);
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        joined(player, player.level().dimension());
        sync(player);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) left(player, player.level().dimension());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = SpaceClock.gameTime(event.getServer());
        if (now % SpaceConfig.DIMENSION_CHECK_PERIOD_TICKS.get() != 0) return;
        checkVisitors(event.getServer());
        closeIdle(event.getServer(), now);
    }

    /** Ferme les dimensions sans joueur depuis plus que le délai configuré. */
    private static void closeIdle(MinecraftServer server, long now) {
        DynamicDimensionRegistry registry = DynamicDimensionRegistry.from(server);
        long delay = SpaceConfig.DIMENSION_IDLE_DELAY_TICKS.get();
        AWAKE.entrySet().removeIf(e -> {
            HostedDimension.State state = e.getValue().state();
            if (state.playerCount() > 0 || now - state.lastExitTick() <= delay) return false;
            registry.unloadDynamicDimension(e.getKey(), null);
            state.setActive(false);
            AmynnaSpaceMod.LOGGER.debug("Dimension {} fermée : inoccupée depuis {} ticks", e.getKey(), now - state.lastExitTick());
            return true;
        });
    }

    /** Teste chaque objet piloté chargé contre les ports des hôtes chargés. */
    private static void checkVisitors(MinecraftServer server) {
        StrategicLayer layer = StrategicLayer.get();
        List<SpaceObject> loaded = layer.loadedObjects();
        for (SpaceObject visitor : loaded) {
            if (!(visitor.controller() instanceof Pilotable)) continue;
            for (SpaceObject host : loaded) {
                if (host != visitor && host.has(ComponentTypes.HOSTED_DIMENSION)) checkVolumes(server, layer, visitor, host);
            }
        }
    }

    /**
     * Réveille la dimension de {@code host} si le visiteur est dans le volume de déclenchement d'un de ses ports, et
     * poste un {@link PortEntryEvent} s'il est dans le volume logique.
     */
    private static void checkVolumes(MinecraftServer server, StrategicLayer layer, SpaceObject visitor, SpaceObject host) {
        double ticks = layer.clock().spaceTime(SpaceClock.gameTime(server));
        // shortcut: port = hôte + décalage local sans rotation ni échelle, à corriger avec les shapes des volumes
        double[] rel = RailMath.relativePosition(layer, visitor, host, ticks);
        for (PortPoint port : host.get(ComponentTypes.HOSTED_DIMENSION).ports()) {
            // visiteur moins hôte, moins le décalage local du port : position du visiteur relative au port
            double[] offset = { rel[0] - port.x(), rel[1] - port.y(), rel[2] - port.z() };
            double distance = Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1] + offset[2] * offset[2]);
            if (distance > port.trigger().radius()) continue;
            wake(server, host);
            if (distance <= port.logical().radius()) NeoForge.EVENT_BUS.post(new PortEntryEvent(visitor, host, port, offset));
        }
    }

    /** À appeler une fois au démarrage du mod : la bibliothèque redemande alors les dimensions d'objets au chargement du monde. */
    public static void registerRestore() {
        DynamicDimensionLoadCallback.register((server, loader) -> {
            StrategicLayer layer = StrategicLayer.get();
            for (ResourceLocation id : layer.hostedDimensions()) {
                HostedDimension hosted = layer.hostOf(id).get(ComponentTypes.HOSTED_DIMENSION);
                loader.loadDynamicDimension(id, voidGenerator(server), dimensionType(), properties(hosted.properties()), server);
            }
        });
    }

    /** Instance neuve à chaque appel : la bibliothèque refuse un {@code DimensionType} déjà enregistré. */
    private static DimensionType dimensionType() {
        return new DimensionType(OptionalLong.empty(), false, false, false, false, 1.0, false, false, -64, 384, 384,
                BlockTags.INFINIBURN_OVERWORLD, SPACE_EFFECTS, 0.1f,
                new DimensionType.MonsterSettings(false, false, ConstantInt.of(0), 0));
    }

    private static ChunkGenerator voidGenerator(MinecraftServer server) {
        var biome = server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID);
        return new FlatLevelSource(new FlatLevelGeneratorSettings(Optional.empty(), biome, List.of()));
    }

    /** Paramètres de la bibliothèque de dimensions, tirés du composant. */
    private static DynamicDimensionProperties properties(HostedDimension.Properties p) {
        return new DynamicDimensionProperties() {
            @Override public int priority() { return 100; }
            @Override public Vector3f baseGravity() { return new Vector3f(p.gx(), p.gy(), p.gz()); }
            @Override public double basePressure() { return p.pressure(); }
            @Override public float universalDrag() { return p.drag(); }
            @Override public Vector3f magneticNorth() { return new Vector3f(p.nx(), p.ny(), p.nz()); }
        };
    }
}
