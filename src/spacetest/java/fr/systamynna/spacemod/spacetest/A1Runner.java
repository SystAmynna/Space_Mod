package fr.systamynna.spacemod.spacetest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import org.joml.Vector3d;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Unit;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.Container;

/**
 * Test A1 du plan : DynamicDimensions + Sable. Machine à états pilotée par le tick serveur,
 * lancée par /spacetest a1 run [cycles]. Écrit test-results/A1/a1-run.log.
 *
 * Écarts assumés avec le plan : DynamicDimensions 0.9.1 n'a pas DynamicDimensionProperties,
 * la physique de dimension passe par DimensionPhysicsData.putPhysics ; le DimensionType
 * « vide » est construit en code (pas de datapack).
 */
final class A1Runner {
    private enum Phase { IDLE, CREATE, MEASURE, UNLOAD_WAIT, RELOAD_WAIT, DELETE_WAIT }

    private static final ResourceLocation VOID_ID = ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, "a1_void"); // pas « void » : DynamicDimensions enregistre aussi un type du même id, en conflit avec le type du datapack
    private static final ResourceLocation GRAV_ID = ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, "a1_gravity");
    private static final int MEASURE_TICKS = 200;
    private static final int TIMEOUT_TICKS = 200;
    private static final double MAX_DRIFT = 0.01;
    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);

    private static Phase phase = Phase.IDLE;
    private static int wait;
    private static int timeout;
    private static int cycle;
    private static int cycles;
    private static int baselineLevels;
    private static int failures;
    private static Path logFile;
    static boolean haltWhenDone;
    private static DimBackend backend;

    private static UUID voidSub;
    private static UUID gravSub;
    private static Vector3d voidStart;
    private static Vector3d gravStart;

    private A1Runner() {}

    static String status() {
        return phase == Phase.IDLE ? "A1 inactif" : "A1 cycle " + cycle + "/" + cycles + " phase " + phase;
    }

    static void abort() {
        phase = Phase.IDLE;
        log("ABANDON");
    }

    static String start(MinecraftServer server, int n) {
        if (phase != Phase.IDLE) return "A1 déjà en cours";
        backend = DimBackend.select();
        if (backend.exists(server, VOID_ID) || backend.exists(server, GRAV_ID)) return "Dimensions de test déjà présentes";
        logFile = Path.of(System.getProperty("spacetest.results", "../test-results"), "A1", "a1-run.log");
        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            return "Écriture du log impossible : " + e;
        }
        cycles = n;
        cycle = 1;
        failures = 0;
        baselineLevels = server.levelKeys().size();
        log("DÉBUT A1 " + Instant.now() + " bibliothèque=" + backend.name() + " cycles=" + n + " niveaux_de_base=" + baselineLevels);
        phase = Phase.CREATE;
        wait = 0;
        return null;
    }

    static void tick(MinecraftServer server) {
        if (phase == Phase.IDLE) return;
        if (wait > 0) {
            wait--;
            return;
        }
        try {
            switch (phase) {
                case CREATE -> create(server);
                case MEASURE -> measure(server);
                case UNLOAD_WAIT -> unloadWait(server);
                case RELOAD_WAIT -> reloadWait(server);
                case DELETE_WAIT -> deleteWait(server);
                default -> {}
            }
        } catch (Throwable t) {
            fail("exception en phase " + phase + " : " + t);
            SpaceTestMod.LOGGER.error("A1", t);
            cleanupAndNext(server);
        }
    }

    // --- étapes 2 à 5 ---------------------------------------------------------------------------

    private static void create(MinecraftServer server) {
        log("-- cycle " + cycle + "/" + cycles);
        ChunkGenerator gen = voidGenerator(server);

        // gravité (0,0,0), pression 0, traînée 0 pour `void` ; `gravity` garde la valeur par défaut de Sable
        DimensionPhysicsData.putPhysics(levelKey(VOID_ID), new DimensionPhysics(VOID_ID, 0,
            Optional.of(0f), Optional.of(new org.joml.Vector3f(0, 0, 0)), Optional.of(0.0), Optional.empty(), Optional.empty(), false));

        long t0 = System.nanoTime();
        ServerLevel voidLevel = backend.create(server, VOID_ID, gen, dimensionType(0f));
        ServerLevel gravLevel = backend.create(server, GRAV_ID, voidGenerator(server), dimensionType(0.001f));
        log("création des 2 dimensions : " + ms(t0) + " ms");
        if (voidLevel == null || gravLevel == null) {
            fail("createDynamicDimension a renvoyé null");
            cleanupAndNext(server);
            return;
        }

        for (ServerLevel l : List.of(voidLevel, gravLevel)) {
            ServerSubLevelContainer c = SubLevelContainer.getContainer(l);
            if (c == null || c.physicsSystem() == null) fail("pas de conteneur/physique Sable pour " + l.dimension().location());
        }

        voidSub = assembleMini(voidLevel);
        gravSub = assembleMini(gravLevel);
        voidStart = poseOf(voidLevel, voidSub);
        gravStart = poseOf(gravLevel, gravSub);
        log("sub-level void=" + voidSub + " départ=" + fmt(voidStart));
        log("sub-level gravity=" + gravSub + " départ=" + fmt(gravStart));
        phase = Phase.MEASURE;
        wait = MEASURE_TICKS;
    }

    private static void measure(MinecraftServer server) {
        ServerLevel voidLevel = server.getLevel(levelKey(VOID_ID));
        ServerLevel gravLevel = server.getLevel(levelKey(GRAV_ID));
        double dVoid = poseOf(voidLevel, voidSub).distance(voidStart);
        double dGrav = poseOf(gravLevel, gravSub).distance(gravStart);
        log("déplacement après " + MEASURE_TICKS + " ticks : void=" + dVoid + " gravity=" + dGrav);
        if (dVoid >= MAX_DRIFT) fail("sub-level `void` bouge de " + dVoid + " (>= " + MAX_DRIFT + ")");
        if (dGrav <= 1.0) fail("sub-level `gravity` ne tombe pas (" + dGrav + ")");

        voidLevel.save(null, true, false);
        boolean ok = backend.unload(server, VOID_ID);
        log("unloadDynamicDimension(void) -> " + ok);
        phase = Phase.UNLOAD_WAIT;
        timeout = TIMEOUT_TICKS;
    }

    // --- étape 6 : déchargement, rechargement ---------------------------------------------------

    private static void unloadWait(MinecraftServer server) {
        if (server.getLevel(levelKey(VOID_ID)) != null) {
            if (--timeout <= 0) {
                fail("`void` toujours chargé " + TIMEOUT_TICKS + " ticks après unload");
                cleanupAndNext(server);
            }
            return;
        }
        long t0 = System.nanoTime();
        ServerLevel reloaded = backend
            .load(server, VOID_ID, voidGenerator(server), dimensionType(0f));
        log("loadDynamicDimension(void) : " + ms(t0) + " ms, niveau=" + (reloaded != null));
        phase = Phase.RELOAD_WAIT;
        wait = 40; // laisse à Sable le temps de recharger les sub-levels
    }

    private static void reloadWait(MinecraftServer server) {
        ServerLevel l = server.getLevel(levelKey(VOID_ID));
        boolean found = false;
        if (l != null) {
            ServerSubLevelContainer c = SubLevelContainer.getContainer(l);
            found = c != null && c.getSubLevel(voidSub) != null;
        }
        log("sub-level retrouvé après rechargement : " + found);
        if (!found) fail("sub-level non retrouvé après rechargement de `void`");
        cleanupAndNext(server);
    }

    // --- étape 7 : suppression et contrôle des fuites -------------------------------------------

    private static void cleanupAndNext(MinecraftServer server) {
        for (ResourceLocation id : List.of(VOID_ID, GRAV_ID)) {
            if (server.getLevel(levelKey(id)) != null) {
                boolean ok = backend.delete(server, id);
                log("deleteDynamicDimension(" + id + ") -> " + ok);
            }
        }
        phase = Phase.DELETE_WAIT;
        timeout = TIMEOUT_TICKS;
    }

    private static void deleteWait(MinecraftServer server) {
        boolean pending = server.getLevel(levelKey(VOID_ID)) != null || server.getLevel(levelKey(GRAV_ID)) != null;
        if (pending && --timeout > 0) return;
        if (pending) fail("niveaux toujours chargés après suppression");

        int levels = server.levelKeys().size();
        log("niveaux chargés : " + levels + " (base " + baselineLevels + ")");
        if (levels != baselineLevels) fail("fuite de niveau : " + levels + " au lieu de " + baselineLevels);
        for (ResourceLocation id : List.of(VOID_ID, GRAV_ID)) {
            Path dir = server.getWorldPath(LevelResource.ROOT).resolve("dimensions").resolve(id.getNamespace()).resolve(id.getPath());
            if (Files.exists(dir) && !backend.deletesFiles()) {
                log("fichiers conservés par la bibliothèque, supprimés par le test : " + dir);
                deleteRecursively(dir);
            } else if (Files.exists(dir)) {
                fail("fichiers restants : " + dir);
            }
        }

        if (++cycle > cycles) {
            log("FIN A1 : " + (failures == 0 ? "RÉUSSI" : "ÉCHEC (" + failures + " problème(s))"));
            phase = Phase.IDLE;
            if (haltWhenDone) server.halt(false);
        } else {
            phase = Phase.CREATE;
            wait = 5;
        }
    }

    // --- outils ---------------------------------------------------------------------------------

    /**
     * DynamicDimensions refuse (renvoient null) une instance de DimensionType déjà enregistrée :
     * il faut une instance neuve, et distincte par dimension (d'où ambientLight qui varie).
     */
    static DimensionType dimensionType(float ambientLight) {
        return new DimensionType(OptionalLong.empty(), false, false, false, false, 1.0, false, false, -64, 384, 384,
            BlockTags.INFINIBURN_OVERWORLD, BuiltinDimensionTypes.OVERWORLD_EFFECTS, ambientLight,
            new DimensionType.MonsterSettings(false, false, ConstantInt.of(0), 0));
    }

    private static void deleteRecursively(Path dir) {
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
        } catch (IOException e) {
            fail("suppression de " + dir + " impossible : " + e);
        }
    }

    static ChunkGenerator voidGenerator(MinecraftServer server) {
        var biome = server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID);
        return new FlatLevelSource(new FlatLevelGeneratorSettings(Optional.empty(), biome, List.of()));
    }

    /** Mini S (10x10x1, 5 coffres au-dessus) : environ 100 blocs. Renvoie l'UUID du sub-level. */
    static UUID assembleMini(ServerLevel level) {
        return assembleMini(level, ORIGIN);
    }

    static UUID assembleMini(ServerLevel level, BlockPos origin) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                BlockPos p = origin.offset(x, 0, z);
                level.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
                blocks.add(p);
            }
        }
        for (int i = 0; i < 5; i++) {
            BlockPos p = origin.offset(i * 2, 1, 5);
            level.setBlock(p, Blocks.CHEST.defaultBlockState(), 3);
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof Container chest) chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
            blocks.add(p);
        }
        ServerSubLevel sub = SubLevelAssemblyHelper.assembleBlocks(level, origin, blocks, BoundingBox3i.from(blocks));
        // Aucun joueur dans ces dimensions : sans ticket Sable peut décharger le sub-level.
        SubLevelContainer.getContainer(level).addForceLoadTicket(sub, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
        return sub.getUniqueId();
    }

    static Vector3d poseOf(ServerLevel level, UUID id) {
        ServerSubLevelContainer c = SubLevelContainer.getContainer(level);
        ServerSubLevel s = (ServerSubLevel) c.getSubLevel(id);
        if (s == null) {
            log("conteneur " + level.dimension().location() + " : " + c.getAllSubLevels().size() + " sub-level(s), occupation=" + c.getOccupancy());
        }
        if (s == null) throw new IllegalStateException("sub-level " + id + " introuvable dans " + level.dimension().location());
        return new Vector3d(s.logicalPose().position());
    }

    static ResourceKey<Level> levelKey(ResourceLocation id) {
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    static long ms(long t0) {
        return (System.nanoTime() - t0) / 1_000_000;
    }

    static String fmt(Vector3d v) {
        return String.format("(%.4f, %.4f, %.4f)", v.x, v.y, v.z);
    }

    private static void fail(String msg) {
        failures++;
        log("ÉCHEC : " + msg);
    }

    private static void log(String line) {
        SpaceTestMod.LOGGER.info("[A1] {}", line);
        try {
            Files.writeString(logFile, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            SpaceTestMod.LOGGER.error("[A1] log illisible", e);
        }
    }
}
