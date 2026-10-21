package fr.systamynna.spacemod.spacetest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.Blocks;

/** Test A3 (manuel) : saut de pose d'un sub-level portant un joueur. Commandes dans SpaceTestCommands. */
final class A3Server {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, "a3");
    private static final Path LOG = Path.of(System.getProperty("spacetest.results", "../test-results"), "A3", "server-a3.log");

    private record Scheduled(long tick, Runnable run) {}

    private static final List<Scheduled> QUEUE = new ArrayList<>();
    private static ServerSubLevel sub;
    private static long now;

    private A3Server() {}

    static void registerPhysics() {
        DimensionPhysicsData.putPhysics(A1Runner.levelKey(ID), new DimensionPhysics(ID, 0,
            Optional.of(0f), Optional.of(new org.joml.Vector3f(0, 0, 0)), Optional.of(0.0), Optional.empty(), Optional.empty(), false));
    }

    /** Crée la dimension, une Mini avec sol, murs et coffre, et y téléporte le joueur. */
    static String setup(ServerPlayer player) {
        ServerLevel level = DimDispatch.create(player.server, ID);
        if (SubLevelContainer.getContainer(level).getAllSubLevels().isEmpty()) {
            BlockPos o = new BlockPos(0, 64, 0);
            for (int x = 0; x < 10; x++) {
                for (int z = 0; z < 10; z++) {
                    level.setBlock(o.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
                    boolean wall = x == 0 || z == 0 || x == 9 || z == 9;
                    for (int y = 1; y <= 3 && wall; y++) level.setBlock(o.offset(x, y, z), Blocks.GLASS.defaultBlockState(), 3);
                }
            }
            level.setBlock(o.offset(5, 1, 5), Blocks.CHEST.defaultBlockState(), 3);
            List<BlockPos> blocks = new ArrayList<>();
            for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) for (int y = 0; y <= 3; y++) {
                BlockPos p = o.offset(x, y, z);
                if (!level.getBlockState(p).isAir()) blocks.add(p);
            }
            sub = dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level, o, blocks,
                dev.ryanhcode.sable.companion.math.BoundingBox3i.from(blocks));
            SubLevelContainer.getContainer(level).addForceLoadTicket(sub, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
        } else {
            sub = SubLevelContainer.getContainer(level).getAllSubLevels().get(0);
        }
        Vector3d p = new Vector3d(sub.logicalPose().position());
        player.teleportTo(level, p.x, p.y + 2, p.z, 0f, 0f);
        log("setup : sub-level " + sub.getUniqueId() + " pose=" + A1Runner.fmt(p));
        return "Mini prête, tu es dessus. Marche un peu, puis lance les commandes /spacetest a3 ...";
    }

    private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> PRELOAD =
        net.minecraft.server.level.TicketType.create("spacemod_preload", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 600);
    private static final double CARRY_THRESHOLD = Double.parseDouble(System.getProperty("spacetest.carry.threshold", "400"));
    private static final int PRELOAD_RADIUS = 8;       // chunks autour de la destination
    private static final int PRELOAD_CHECK_RADIUS = 6; // zone qui doit être pleinement chargée avant le saut
    private static final int PRELOAD_TIMEOUT_TICKS = 600;

    /**
     * Protocole de téléportation : la destination est chargée AVANT de téléporter. Un ticket de chargement est posé
     * autour de la pose d'arrivée ; la téléportation n'a lieu que lorsque les chunks voisins sont chargés (ou après
     * 30 s, avec un avertissement dans le journal).
     */
    static String teleport(double dx, double dy, double dz, boolean resetVelocity, ServerPlayer observer) {
        if (sub == null || sub.isRemoved()) return "Fais d'abord /spacetest a3 setup";
        ServerLevel level = sub.getLevel();
        Vector3d dest = new Vector3d(sub.logicalPose().position()).add(dx, dy, dz);
        net.minecraft.world.level.ChunkPos c = new net.minecraft.world.level.ChunkPos(BlockPos.containing(dest.x, dest.y, dest.z));
        level.getChunkSource().addRegionTicket(PRELOAD, c, PRELOAD_RADIUS, c);
        long start = now;
        if (destinationReady(level, c)) {
            log("préchargement : destination déjà chargée (chunk " + c + ")");
            return doTeleport(dx, dy, dz, resetVelocity, observer);
        }
        QUEUE.add(new Scheduled(now + 1, () -> waitForDestination(level, c, start, dx, dy, dz, resetVelocity, observer)));
        return "Chargement de la destination (chunk " + c + ")…";
    }

    private static void waitForDestination(ServerLevel level, net.minecraft.world.level.ChunkPos c, long start, double dx, double dy, double dz, boolean reset, ServerPlayer observer) {
        boolean ready = destinationReady(level, c);
        boolean timeout = now - start > PRELOAD_TIMEOUT_TICKS;
        if (!ready && !timeout) {
            QUEUE.add(new Scheduled(now + 1, () -> waitForDestination(level, c, start, dx, dy, dz, reset, observer)));
            return;
        }
        log(String.format("préchargement : destination %s après %d ticks (%.1f s)", ready ? "chargée" : "NON chargée (délai dépassé, téléportation quand même)", now - start, (now - start) / 20.0));
        doTeleport(dx, dy, dz, reset, observer);
    }

    private static boolean destinationReady(ServerLevel level, net.minecraft.world.level.ChunkPos c) {
        for (int x = -PRELOAD_CHECK_RADIUS; x <= PRELOAD_CHECK_RADIUS; x++) {
            for (int z = -PRELOAD_CHECK_RADIUS; z <= PRELOAD_CHECK_RADIUS; z++) {
                if (level.getChunkSource().getChunkNow(c.x + x, c.z + z) == null) return false;
            }
        }
        return true;
    }

    private static String doTeleport(double dx, double dy, double dz, boolean resetVelocity, ServerPlayer observer) {
        if (sub == null || sub.isRemoved()) return "Fais d'abord /spacetest a3 setup";
        Vector3d before = new Vector3d(sub.logicalPose().position());
        Vector3d vBefore = new Vector3d(RigidBodyHandle.of(sub).getLinearVelocity());
        Vector3d rel = relative(observer);
        SubLevelPhysicsSystem sys = SubLevelPhysicsSystem.get(sub.getLevel());
        sys.getPipeline().teleport(sub, new Vector3d(before).add(dx, dy, dz), new Quaterniond(sub.logicalPose().orientation()));
        if (resetVelocity) sys.getPipeline().resetVelocity(sub);
        log(String.format("TELEPORT tick=%d d=(%.1f %.1f %.1f) reset=%b avant=%s v_avant=%s rel_joueur_avant=%s", now, dx, dy, dz, resetVelocity,
            A1Runner.fmt(before), A1Runner.fmt(vBefore), A1Runner.fmt(rel)));
        // Version restaurée (déplacement par le mod dès 400 blocs, au même tick, contrôle et correction à +4 ticks pour toutes
        // les distances). Comportement imparfait connu : voir le rapport A3 (téléportation en avance/en retard, éjections).
        if (observer != null) {
            Vector3d expected = new Vector3d(observer.getX() + dx, observer.getY() + dy, observer.getZ() + dz);
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) >= CARRY_THRESHOLD) {
                observer.teleportTo((ServerLevel) observer.level(), expected.x, expected.y, expected.z, observer.getYRot(), observer.getXRot());
                log(String.format("déplacement du joueur par le mod (distance >= %.0f) vers %s", CARRY_THRESHOLD, A1Runner.fmt(expected)));
            }
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) >= CARRY_THRESHOLD) {
                observer.teleportTo((ServerLevel) observer.level(), expected.x, expected.y, expected.z, observer.getYRot(), observer.getXRot());
                log(String.format("déplacement du joueur par le mod (distance >= %.0f) vers %s", CARRY_THRESHOLD, A1Runner.fmt(expected)));
            }
            QUEUE.add(new Scheduled(now + 4, () -> {
                double gap = new Vector3d(observer.getX(), observer.getY(), observer.getZ()).distance(expected);
                if (gap > 2.0) {
                    log(String.format("contrôle +4 ticks : joueur hors position (écart %.1f blocs, position brute %.1f %.1f %.1f) : correction vers %s", gap,
                        observer.getX(), observer.getY(), observer.getZ(), A1Runner.fmt(expected)));
                    observer.teleportTo((ServerLevel) observer.level(), expected.x, expected.y, expected.z, observer.getYRot(), observer.getXRot());
                } else {
                    log(String.format("contrôle +4 ticks : joueur à la bonne position (écart %.2f bloc)", gap));
                }
            }));
        }
        // la relève après coup se fait 2 ticks plus tard
        QUEUE.add(new Scheduled(now + 2, () -> log(String.format("  après (+2 ticks) : pose=%s v=%s rel_joueur=%s", A1Runner.fmt(new Vector3d(sub.logicalPose().position())),
            A1Runner.fmt(new Vector3d(RigidBodyHandle.of(sub).getLinearVelocity())), A1Runner.fmt(relative(observer))))));
        return "Téléportation de " + dx + " " + dy + " " + dz;
    }

    /**
     * Déplacement par sérialisation : le sub-level est sérialisé avec une pose décalée, retiré, rechargé dans la même
     * dimension ; le joueur est téléporté du même vecteur que la Mini.
     */
    static String teleportBySerialization(double dx, double dy, double dz, ServerPlayer player) {
        if (sub == null || sub.isRemoved()) return "Fais d'abord /spacetest a3 setup";
        ServerLevel level = sub.getLevel();
        Vector3d rawBefore = new Vector3d(player.getX(), player.getY(), player.getZ());
        Vector3d poseBefore = new Vector3d(sub.logicalPose().position());
        Vector3d vel = new Vector3d(RigidBodyHandle.of(sub).getLinearVelocity());
        long t0 = System.nanoTime();
        var data = dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer.toData(sub, java.util.List.of());
        var tag = data.fullTag();
        for (String key : new String[] { "x", "y", "z" }) {
            double d = key.equals("x") ? dx : key.equals("y") ? dy : dz;
            var pos = tag.getCompound("pose").getCompound("position");
            pos.putDouble(key, pos.getDouble(key) + d);
            var bounds = tag.getCompound("world_bounds");
            bounds.putDouble("min" + key.toUpperCase(), bounds.getDouble("min" + key.toUpperCase()) + d);
            bounds.putDouble("max" + key.toUpperCase(), bounds.getDouble("max" + key.toUpperCase()) + d);
        }
        SubLevelContainer.getContainer(level).removeSubLevel(sub, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        ServerSubLevel moved = dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer.fullyLoad(level,
            dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer.fromData(tag));
        if (moved == null) return "Rechargement impossible (plot pris) : Mini perdue, refais setup";
        SubLevelContainer.getContainer(level).addForceLoadTicket(moved, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
        sub = moved;
        // la position du joueur est en coordonnées du monde (le journal A7 le montre) : il suit la pose, donc on le décale du même vecteur
        player.teleportTo(level, rawBefore.x + dx, rawBefore.y + dy, rawBefore.z + dz, player.getYRot(), player.getXRot());
        double ms = (System.nanoTime() - t0) / 1e6;
        log(String.format("TELEPORT PAR SÉRIALISATION tick=%d d=(%.1f %.1f %.1f) %.2f ms pose avant=%s demandée=%s position brute joueur=%s", now, dx, dy, dz, ms,
            A1Runner.fmt(poseBefore), A1Runner.fmt(new Vector3d(poseBefore).add(dx, dy, dz)), A1Runner.fmt(rawBefore)));
        QUEUE.add(new Scheduled(now + 2, () -> {
            log(String.format("  après (+2 ticks) : pose=%s joueur brut=(%.2f %.2f %.2f) suivi=%b", A1Runner.fmt(new Vector3d(sub.logicalPose().position())),
                player.getX(), player.getY(), player.getZ(), Sable.HELPER.getTrackingSubLevel(player) != null));
            RigidBodyHandle.of(sub).applyLinearImpulse(new Vector3d(vel).mul(sub.getMassTracker().getMass()));
        }));
        return String.format("Mini rechargée avec un décalage de %.0f %.0f %.0f (%.1f ms)", dx, dy, dz, ms);
    }

    static String move(double vx) {
        if (sub == null) return "Fais d'abord /spacetest a3 setup";
        RigidBodyHandle.of(sub).applyLinearImpulse(new Vector3d(sub.getMassTracker().getMass() * vx, 0, 0));
        log("MOUVEMENT vitesse demandée " + vx + " blocs/s");
        return "Impulsion appliquée";
    }

    /** Répète une téléportation count fois, toutes les intervalTicks ticks. */
    static String batch(int count, int intervalTicks, double dx, ServerPlayer observer) {
        for (int i = 0; i < count; i++) {
            final int k = i;
            QUEUE.add(new Scheduled(now + 1 + (long) i * intervalTicks, () -> teleport(k % 2 == 0 ? dx : -dx, 0, 0, false, observer)));
        }
        log("BATCH " + count + " téléportations de ±" + dx + " tous les " + intervalTicks + " tick(s)");
        return "Série lancée";
    }

    static String stop() {
        if (sub != null) SubLevelPhysicsSystem.get(sub.getLevel()).getPipeline().resetVelocity(sub);
        return "Vitesse remise à zéro";
    }

    static void tick() {
        now++;
        // copie : une action planifiée peut elle-même planifier (ConcurrentModificationException sinon)
        for (Scheduled s : new ArrayList<>(QUEUE)) {
            if (s.tick() > now) continue;
            QUEUE.remove(s);
            s.run().run();
        }
    }

    private static Vector3d relative(ServerPlayer p) {
        if (sub == null || p == null) return new Vector3d();
        boolean tracked = Sable.HELPER.getTrackingSubLevel(p) != null;
        // position brute : en repère du plot si le joueur est suivi par le sub-level
        return tracked ? new Vector3d(p.getX(), p.getY(), p.getZ()) : sub.logicalPose().transformPositionInverse(new Vector3d(p.getX(), p.getY(), p.getZ()));
    }

    private static void log(String line) {
        SpaceTestMod.LOGGER.info("[A3] {}", line);
        try {
            Files.createDirectories(LOG.getParent());
            Files.writeString(LOG, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            SpaceTestMod.LOGGER.error("[A3] log", e);
        }
    }
}
