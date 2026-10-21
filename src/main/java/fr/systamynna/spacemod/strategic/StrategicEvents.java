package fr.systamynna.spacemod.strategic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.strategic.store.DriverLoader;
import fr.systamynna.spacemod.strategic.store.JdbcStrategicStore;
import fr.systamynna.spacemod.strategic.store.StrategicStoreException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Cycle de vie de la couche stratégique : chargée avec le monde, sauvée à chaque sauvegarde et à l'arrêt, avec
 * déchargement périodique des systèmes que plus personne n'occupe.
 */
public final class StrategicEvents {
    /** Période, en ticks, du ramassage des unités inoccupées. */
    private static final int UNLOAD_CHECK_PERIOD = 100;
    /** Dossier des pilotes JDBC d'autres SGBD, dans le dossier de configuration. */
    private static final String DRIVERS_FOLDER = AmynnaSpaceMod.MODID + "/drivers";

    /** Classe utilitaire : les gestionnaires d'événements sont statiques. */
    private StrategicEvents() {}

    /**
     * Ouvre la base selon la configuration, charge la couche avant le démarrage du serveur, puis la rend accessible par
     * {@code StrategicLayer.get()}.
     *
     * @throws StrategicStoreException si la base ne peut pas être ouverte : le serveur ne démarre pas sur une couche absente
     */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        String url = SpaceConfig.DATABASE_URL.get().replace(SpaceConfig.WORLD_PLACEHOLDER, world.toString());
        createSqliteFolder(url);
        Driver driver = loadDriver();
        JdbcStrategicStore store = JdbcStrategicStore.open(url, SpaceConfig.DATABASE_USER.get(),
                SpaceConfig.DATABASE_PASSWORD.get(), SpaceConfig.TABLE_PREFIX.get(), driver);
        StrategicLayer layer = new StrategicLayer(store);
        StrategicLayer.bind(layer);
        AmynnaSpaceMod.LOGGER.info("Couche stratégique chargée : {} objet(s) résident(s), {} dimension(s) hébergée(s)",
                layer.loadedObjectCount(), layer.hostedDimensions().size());
    }

    /**
     * Sauvegarde de la couche au rythme de la sauvegarde vanilla. L'événement se déclenche pour chaque dimension ;
     * on ne sauve qu'une fois, avec l'Overworld.
     */
    @SubscribeEvent
    public static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            saveActive();
        }
    }

    /**
     * Date les accès à la couche à chaque tick, et décharge périodiquement les unités que personne n'occupe depuis le
     * délai configuré.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        StrategicLayer layer = StrategicLayer.get();
        long gameTime = SpaceClock.gameTime(event.getServer());
        layer.tick(gameTime);
        if (gameTime % UNLOAD_CHECK_PERIOD != 0) return;
        int unloaded = layer.unloadIdleUnits(SpaceConfig.UNLOAD_DELAY_TICKS.get(), occupiedUnits(event.getServer(), layer));
        if (unloaded > 0) AmynnaSpaceMod.LOGGER.debug("Couche stratégique : {} système(s) déchargé(s)", unloaded);
    }

    /** Sauvegarde finale (objets modifiés et horloge) : le serveur s'arrête, tout changement non écrit serait perdu. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        saveActive();
    }

    /** Ferme la base et oublie la couche une fois le serveur arrêté, pour qu'un accès tardif échoue franchement. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        StrategicLayer layer = StrategicLayer.get();
        StrategicLayer.unbind();
        layer.close();
    }

    /** Écrit la couche active et journalise le nombre d'objets réécrits (rien n'est journalisé si rien n'a changé). */
    private static void saveActive() {
        StrategicLayer layer = StrategicLayer.get();
        int written = layer.save();
        if (written > 0) AmynnaSpaceMod.LOGGER.debug("Couche stratégique sauvée : {} objet(s) écrit(s)", written);
    }

    /** Unités (systèmes) où se trouve au moins un joueur, d'après la dimension où il est, sans rien charger. */
    private static Set<UUID> occupiedUnits(MinecraftServer server, StrategicLayer layer) {
        Set<UUID> occupied = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID unit = layer.unitOfDimension(player.level().dimension().location());
            if (unit != null) occupied.add(unit);
        }
        return occupied;
    }

    /**
     * Pilote du SGBD configuré, ou {@code null} pour SQLite (intégré) ou un pilote déjà sur le chemin de classes.
     *
     * @throws StrategicStoreException si le jar demandé sort du dossier des pilotes, ou si le pilote est introuvable
     */
    @org.jetbrains.annotations.Nullable
    private static Driver loadDriver() {
        String driverClass = SpaceConfig.DRIVER_CLASS.get();
        if (driverClass.isBlank()) return null;
        String jarName = SpaceConfig.DRIVER_JAR.get();
        Path jar = jarName.isBlank() ? null : FMLPaths.CONFIGDIR.get().resolve(DRIVERS_FOLDER).resolve(jarName).normalize();
        Path folder = FMLPaths.CONFIGDIR.get().resolve(DRIVERS_FOLDER).normalize();
        // le nom du jar ne doit pas sortir du dossier des pilotes
        if (jar != null && !jar.startsWith(folder)) throw new StrategicStoreException("driverJar hors de " + folder + " : " + jarName);
        return DriverLoader.load(driverClass, jar);
    }

    /**
     * Pour une base SQLite dans un fichier, crée le dossier qui doit la contenir (SQLite ne le crée pas). Sans effet
     * pour un autre SGBD ou une base en mémoire.
     */
    private static void createSqliteFolder(String url) {
        String prefix = "jdbc:sqlite:";
        if (!url.startsWith(prefix) || url.contains(":memory:")) return;
        try {
            Path parent = Path.of(url.substring(prefix.length())).toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
        } catch (java.io.IOException e) {
            throw new StrategicStoreException("Création du dossier de la base : " + url, e);
        }
    }
}
