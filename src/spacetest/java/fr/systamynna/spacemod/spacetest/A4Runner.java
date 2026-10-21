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

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Test A4 du plan (et A5, partie « gel par contrainte fixe ») : position verrouillée, rotation libre.
 * Six scénarios tournent en parallèle dans `spacemod_test:a4` pendant 6000 ticks (5 minutes) :
 *   S verrouillée + impulsion linéaire / couple / les deux ; S gelée (contrainte fixe) ; L verrouillée ;
 *   S verrouillée avec 20 armor stands. Pas de joueur (A4 manuel facultatif non fait ici).
 * Lancé par -Pa4=1 ; écrit test-results/A4/a4-run.log.
 */
final class A4Runner {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, "a4");
    private static final Path DIR = Path.of(System.getProperty("spacetest.results", "../test-results"), "A4");
    private static final int SETTLE = 20;
    private static final int TICKS = 6000;
    private static final double MAX_DRIFT = 0.01;
    private static final double MAX_STD = 0.001;

    private enum Kind { LOCKED, FIXED }

    private static final class Scenario {
        final String name;
        final Kind kind;
        final boolean large;
        final boolean linear;
        final boolean torque;
        final int stands;
        ServerSubLevel sub;
        Vector3d start;
        Quaterniond startRot;
        double mass;
        long n;
        final Vector3d sum = new Vector3d();
        final Vector3d sumSq = new Vector3d();
        double maxDrift;
        double maxAngle;
        double peakOmega;
        int calmTick = -1;
        boolean rejected;

        Scenario(String name, Kind kind, boolean large, boolean linear, boolean torque, int stands) {
            this.name = name; this.kind = kind; this.large = large; this.linear = linear; this.torque = torque; this.stands = stands;
        }
    }

    private static final List<Scenario> SCENARIOS = List.of(
        new Scenario("S verrouillée, impulsion linéaire", Kind.LOCKED, false, true, false, 0),
        new Scenario("S verrouillée, couple", Kind.LOCKED, false, false, true, 0),
        new Scenario("S verrouillée, impulsion + couple", Kind.LOCKED, false, true, true, 0),
        new Scenario("S gelée (contrainte fixe), impulsion + couple", Kind.FIXED, false, true, true, 0),
        new Scenario("L verrouillée, impulsion + couple", Kind.LOCKED, true, true, true, 0),
        new Scenario("S verrouillée + 20 entités, impulsion + couple", Kind.LOCKED, false, true, true, 20));

    private static int tick = -1;
    private static long tickNanos;
    private static long tickSamples;
    private static long lastTickStart;

    private A4Runner() {}

    static void registerPhysics() {
        DimensionPhysicsData.putPhysics(A1Runner.levelKey(ID), new DimensionPhysics(ID, 0,
            Optional.of(0f), Optional.of(new org.joml.Vector3f(0, 0, 0)), Optional.of(0.0), Optional.empty(), Optional.empty(), false));
    }

    static void start(MinecraftServer server) {
        try {
            Files.createDirectories(DIR);
            Files.writeString(DIR.resolve("a4-run.log"), "");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        ServerLevel level = DimDispatch.create(server, ID);
        for (int i = 0; i < SCENARIOS.size(); i++) {
            Scenario s = SCENARIOS.get(i);
            BlockPos origin = new BlockPos(i * 100, 64, 0);
            s.sub = s.large ? assembleLarge(level, origin) : assembleSmall(level, origin);
            s.start = new Vector3d(s.sub.logicalPose().position());
            s.startRot = new Quaterniond(s.sub.logicalPose().orientation());
            s.mass = s.sub.getMassTracker().getMass();
            if (s.stands > 0) spawnStands(level, s.sub, s.stands);
            log(s.name + " : sub-level " + s.sub.getUniqueId() + " masse=" + s.mass + " départ=" + A1Runner.fmt(s.start));
        }
        tick = 0;
        log("DÉBUT A4 " + TICKS + " ticks");
    }

    static void tickStart() {
        lastTickStart = System.nanoTime();
    }

    static void tickEnd(MinecraftServer server) {
        if (tick < 0) return;
        tickNanos += System.nanoTime() - lastTickStart;
        tickSamples++;
        ServerLevel level = server.getLevel(A1Runner.levelKey(ID));
        try {
            if (tick == SETTLE) apply(level);
            if (tick > SETTLE) for (Scenario s : SCENARIOS) record(level, s);
            if (++tick > TICKS + SETTLE) finish(server);
        } catch (Throwable t) {
            log("ÉCHEC : exception " + t);
            SpaceTestMod.LOGGER.error("A4", t);
            server.halt(false);
        }
    }

    private static void apply(ServerLevel level) {
        SubLevelPhysicsSystem sys = SubLevelPhysicsSystem.get(level);
        PhysicsPipeline pipe = sys.getPipeline();
        for (Scenario s : SCENARIOS) {
            Pose3d p = s.sub.logicalPose();
            Vector3d world = new Vector3d(p.position());
            Vector3d local = new Vector3d(p.rotationPoint());
            Quaterniond id = new Quaterniond();
            try {
                if (s.kind == Kind.LOCKED) {
                    pipe.addConstraint(null, s.sub, new GenericConstraintConfiguration(world, local, id, id,
                        Set.of(ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.LINEAR_Y, ConstraintJointAxis.LINEAR_Z)));
                } else {
                    pipe.addConstraint(null, s.sub, new FixedConstraintConfiguration(world, local, id));
                }
            } catch (RuntimeException e) {
                s.rejected = true;
                log("ÉCHEC : contrainte rejetée pour « " + s.name + " » : " + e);
                continue;
            }
            RigidBodyHandle h = RigidBodyHandle.of(s.sub);
            if (s.linear) h.applyLinearImpulse(new Vector3d(s.mass * 10, s.mass * 5, s.mass * 3));
            if (s.torque) h.applyTorqueImpulse(new Vector3d(s.mass * 10, s.mass * 10, s.mass * 10));
            log("contrainte posée et impulsions appliquées : " + s.name);
        }
    }

    private static void record(ServerLevel level, Scenario s) {
        Pose3d p = s.sub.logicalPose();
        Vector3d d = new Vector3d(p.position()).sub(s.start);
        s.maxDrift = Math.max(s.maxDrift, d.length());
        if (tick > SETTLE + 20) { // statistiques après stabilisation
            s.n++;
            s.sum.add(d);
            s.sumSq.add(d.x * d.x, d.y * d.y, d.z * d.z);
        }
        Quaterniond rel = new Quaterniond(p.orientation()).mul(new Quaterniond(s.startRot).invert());
        s.maxAngle = Math.max(s.maxAngle, 2 * Math.acos(Math.min(1, Math.abs(rel.w))));
        double w = RigidBodyHandle.of(s.sub).getAngularVelocity().length();
        if (w > s.peakOmega) {
            s.peakOmega = w;
            s.calmTick = -1;
        } else if (s.calmTick < 0 && s.peakOmega > 0 && w < 0.01 * s.peakOmega) {
            s.calmTick = tick - SETTLE;
        }
    }

    private static double std(Scenario s) {
        if (s.n == 0) return Double.NaN;
        double var = 0;
        for (int i = 0; i < 3; i++) {
            double mean = s.sum.get(i) / s.n;
            var += s.sumSq.get(i) / s.n - mean * mean;
        }
        return Math.sqrt(Math.max(0, var));
    }

    private static void finish(MinecraftServer server) {
        int fails = 0;
        for (Scenario s : SCENARIOS) {
            double std = std(s);
            boolean posOk = !s.rejected && s.maxDrift < MAX_DRIFT && std < MAX_STD;
            boolean rotOk = !s.torque || (s.kind == Kind.FIXED ? s.maxAngle < 0.01 : s.maxAngle > 0.1);
            if (!posOk || !rotOk) fails++;
            log(String.format("%s : dérive max=%.3e écart-type=%.3e angle max=%.3f rad pic ω=%.3f amortissement(1%%)=%s -> position %s, rotation %s",
                s.name, s.maxDrift, std, s.maxAngle, s.peakOmega, s.calmTick < 0 ? "non atteint" : s.calmTick + " ticks",
                posOk ? "OK" : "ÉCHEC", rotOk ? "OK" : "ÉCHEC"));
        }
        log(String.format("temps de tick serveur moyen : %.3f ms (%d ticks)", tickNanos / 1e6 / tickSamples, tickSamples));
        log("FIN A4 : " + (fails == 0 ? "RÉUSSI" : "ÉCHEC (" + fails + " scénario(s))"));
        server.halt(false);
    }

    // --- construction ---------------------------------------------------------------------------

    static ServerSubLevel assembleSmall(ServerLevel level, BlockPos origin) {
        return assemble(level, origin, 10, 1, 10, 5);
    }

    /** Mini L : 20x5x20 = 2000 blocs, 50 coffres remplaçant la couche supérieure. */
    static ServerSubLevel assembleLarge(ServerLevel level, BlockPos origin) {
        return assemble(level, origin, 20, 5, 20, 50);
    }

    static ServerSubLevel assemble(ServerLevel level, BlockPos origin, int sx, int sy, int sz, int chests) {
        List<BlockPos> blocks = new ArrayList<>();
        int placedChests = 0;
        for (int y = sy - 1; y >= 0; y--) {
            for (int x = 0; x < sx; x++) {
                for (int z = 0; z < sz; z++) {
                    BlockPos p = origin.offset(x, y, z);
                    boolean chest = placedChests < chests && y == sy - 1 && (x + z) % 2 == 0 && x % 3 == 0;
                    level.setBlock(p, (chest ? Blocks.CHEST : Blocks.STONE).defaultBlockState(), 3);
                    if (chest) {
                        placedChests++;
                        if (level.getBlockEntity(p) instanceof Container c) c.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    }
                    blocks.add(p);
                }
            }
        }
        ServerSubLevel sub = SubLevelAssemblyHelper.assembleBlocks(level, origin, blocks, BoundingBox3i.from(blocks));
        SubLevelContainer.getContainer(level).addForceLoadTicket(sub, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
        return sub;
    }

    static void spawnStands(ServerLevel level, ServerSubLevel sub, int n) {
        BoundingBox3ic b = sub.getPlot().getBoundingBox();
        for (int i = 0; i < n; i++) {
            ArmorStand a = EntityType.ARMOR_STAND.create(level);
            a.setPos(b.minX() + 1 + (i % 5) * 1.5, b.maxY() + 1, b.minZ() + 1 + (i / 5) * 1.5);
            level.addFreshEntity(a);
        }
    }

    private static void log(String line) {
        SpaceTestMod.LOGGER.info("[A4] {}", line);
        try {
            Files.writeString(DIR.resolve("a4-run.log"), line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            SpaceTestMod.LOGGER.error("[A4] log", e);
        }
    }
}
