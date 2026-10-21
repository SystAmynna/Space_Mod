package fr.systamynna.spacemod.spacetest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
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
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.Blocks;

/**
 * Test A5 du plan (sommeil, réveil, gel après rechargement). Le gel par contrainte fixe a été mesuré en A4.
 * Sable n'expose pas l'état de sommeil : on compare le coût d'un tick serveur avec 0, 1 puis 200 Minis posées
 * sous gravité (le temps de tick serveur remplace Spark, non installé), et on vérifie qu'elles restent immobiles.
 * Lancé par -Pa5=1 ; écrit test-results/A5/a5-run.log.
 */
final class A5Runner {
    private static final ResourceLocation GRAV = ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, "a5g");
    private static final ResourceLocation VOID = ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, "a5v");
    private static final Path DIR = Path.of(System.getProperty("spacetest.results", "../test-results"), "A5");
    private static final int WINDOW = 400;
    private static final int FLOOR_Y = 60;
    private static final int GRID = 15;
    private static final int SPACING = 15;

    private record Step(int delayAfter, Runnable run) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static MinecraftServer server;
    private static ServerLevel grav;
    private static int next = -1;
    private static int wait;
    private static int fails;

    private static long nanos;
    private static long samples;
    private static long t0;
    private static final List<Double> avg = new ArrayList<>();
    private static final Map<UUID, Vector3d> restStart = new HashMap<>();
    private static double peak;
    private static ServerSubLevel frozen;
    private static Vector3d frozenStart;

    private A5Runner() {}

    static void registerPhysics() {
        DimensionPhysicsData.putPhysics(A1Runner.levelKey(VOID), new DimensionPhysics(VOID, 0,
            Optional.of(0f), Optional.of(new org.joml.Vector3f(0, 0, 0)), Optional.of(0.0), Optional.empty(), Optional.empty(), false));
    }

    static void start(MinecraftServer s) {
        server = s;
        try {
            Files.createDirectories(DIR);
            Files.writeString(DIR.resolve("a5-run.log"), "");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        grav = DimDispatch.create(s, GRAV); // pas de DimensionPhysics : gravité par défaut de Sable
        buildFloor();
        log("DÉBUT A5, gravité Sable de a5g : " + DimensionPhysicsData.getGravity(grav));

        STEPS.add(new Step(100, () -> {}));
        STEPS.add(new Step(WINDOW, () -> beginWindow()));
        STEPS.add(new Step(5, () -> endWindow("0 sub-level")));
        STEPS.add(new Step(5, () -> addMinis(0, 1)));
        STEPS.add(new Step(200, () -> {}));
        STEPS.add(new Step(WINDOW, () -> { beginWindow(); snapshot(); }));
        STEPS.add(new Step(5, () -> { endWindow("1 sub-level"); checkRest("1 sub-level"); }));
        // réveil
        STEPS.add(new Step(5, A5Runner::wakeByImpulse));
        STEPS.add(new Step(100, () -> { }));
        STEPS.add(new Step(5, A5Runner::checkBackToRest));
        STEPS.add(new Step(5, A5Runner::wakeByWakeUp));
        STEPS.add(new Step(100, () -> { }));
        STEPS.add(new Step(5, A5Runner::checkBackToRest));
        STEPS.add(new Step(5, A5Runner::wakeByBlock));
        STEPS.add(new Step(100, () -> { }));
        STEPS.add(new Step(5, A5Runner::checkBackToRest));
        // 200 Minis
        STEPS.add(new Step(5, () -> addMinis(1, 200)));
        STEPS.add(new Step(300, () -> {}));
        STEPS.add(new Step(WINDOW, () -> { beginWindow(); snapshot(); }));
        STEPS.add(new Step(5, () -> { endWindow("200 sub-levels"); checkRest("200 sub-levels"); }));
        STEPS.add(new Step(5, A5Runner::costSummary));
        // gel + rechargement
        STEPS.add(new Step(5, A5Runner::freeze));
        STEPS.add(new Step(100, () -> {}));
        STEPS.add(new Step(5, A5Runner::unloadFrozen));
        STEPS.add(new Step(2, A5Runner::recreateFrozen));
        STEPS.add(new Step(40, () -> {}));
        STEPS.add(new Step(5, A5Runner::kickAfterReload));
        STEPS.add(new Step(100, () -> {}));
        STEPS.add(new Step(5, A5Runner::finish));
        next = 0;
        wait = 5;
    }

    static void tickStart() {
        t0 = System.nanoTime();
    }

    static void tickEnd() {
        if (next < 0) return;
        if (grav != null && next >= 0 && !SubLevelContainer.getContainer(grav).getAllSubLevels().isEmpty() && restStart.size() == 1) {
            ServerSubLevel f = first();
            Vector3d a = restStart.get(f.getUniqueId());
            if (a != null) peak = Math.max(peak, a.distance(new Vector3d(f.logicalPose().position())));
        }
        nanos += System.nanoTime() - t0;
        samples++;
        if (wait-- > 0 || next >= STEPS.size()) return;
        Step st = STEPS.get(next++);
        try {
            st.run().run();
        } catch (Throwable t) {
            fail("exception à l'étape " + next + " : " + t);
            SpaceTestMod.LOGGER.error("A5", t);
            server.halt(false);
        }
        wait = st.delayAfter();
    }

    // --- étapes ---------------------------------------------------------------------------------

    private static void buildFloor() {
        int size = GRID * SPACING + 20;
        for (int x = -10; x < size; x++) {
            for (int z = -10; z < size; z++) grav.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE.defaultBlockState(), 2);
        }
    }

    private static void addMinis(int from, int to) {
        for (int i = from; i < to; i++) {
            A1Runner.assembleMini(grav, new BlockPos((i % GRID) * SPACING, FLOOR_Y + 1, (i / GRID) * SPACING));
        }
        log("sub-levels dans a5g : " + SubLevelContainer.getContainer(grav).getAllSubLevels().size());
    }

    private static void beginWindow() {
        nanos = 0;
        samples = 0;
    }

    private static void endWindow(String label) {
        double ms = nanos / 1e6 / Math.max(1, samples);
        avg.add(ms);
        log(String.format("temps de tick serveur moyen avec %s : %.4f ms (%d ticks)", label, ms, samples));
    }

    private static void snapshot() {
        restStart.clear();
        for (ServerSubLevel s : SubLevelContainer.getContainer(grav).getAllSubLevels()) restStart.put(s.getUniqueId(), new Vector3d(s.logicalPose().position()));
    }

    private static void checkRest(String label) {
        double max = 0;
        for (ServerSubLevel s : SubLevelContainer.getContainer(grav).getAllSubLevels()) {
            Vector3d a = restStart.get(s.getUniqueId());
            if (a != null) max = Math.max(max, a.distance(new Vector3d(s.logicalPose().position())));
        }
        log(String.format("%s : déplacement max sur la fenêtre de %d ticks = %.3e (seuil 0,01)", label, WINDOW, max));
        if (max >= 0.01) fail(label + " : sub-levels non immobiles au repos");
    }

    private static ServerSubLevel first() {
        return SubLevelContainer.getContainer(grav).getAllSubLevels().get(0);
    }

    private static void wakeByImpulse() {
        ServerSubLevel s = first();
        snapshot();
        peak = 0;
        RigidBodyHandle.of(s).applyLinearImpulse(new Vector3d(0, s.getMassTracker().getMass() * 3, 0));
        log("impulsion verticale appliquée au sub-level au repos");
    }

    private static void wakeByWakeUp() {
        ServerSubLevel s = first();
        snapshot();
        peak = 0;
        SubLevelPhysicsSystem.get(grav).getPipeline().wakeUp(s);
        RigidBodyHandle.of(s).applyLinearImpulse(new Vector3d(0, s.getMassTracker().getMass() * 3, 0));
        log("wakeUp puis impulsion verticale appliqués");
    }

    private static void wakeByBlock() {
        ServerSubLevel s = first();
        snapshot();
        peak = 0;
        var b = s.getPlot().getBoundingBox();
        BlockPos p = new BlockPos(b.minX() + 2, b.maxY() + 1, b.minZ() + 2);
        grav.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
        log("bloc posé sur le sub-level au repos : " + p);
    }

    private static void checkBackToRest() {
        ServerSubLevel s = first();
        Vector3d now = new Vector3d(s.logicalPose().position());
        double moved = restStart.get(s.getUniqueId()).distance(now);
        double v = RigidBodyHandle.of(s).getLinearVelocity().length();
        log(String.format("après la sollicitation : écart maximal pendant la fenêtre=%.3f, déplacement final=%.3f, vitesse=%.4f -> %s", peak, moved, v, v < 0.01 ? "rendormi/au repos" : "encore en mouvement"));
        if (v >= 0.01) fail("sub-level pas revenu au repos 5 s après sollicitation");
        if (peak < 1e-3) log("note : aucun mouvement pendant la fenêtre, le réveil n'est pas démontré");
    }

    private static void costSummary() {
        double base = avg.get(0), one = avg.get(1), many = avg.get(2);
        log(String.format("coût supplémentaire par rapport à 0 sub-level : 1 -> %.4f ms ; 200 -> %.4f ms (%.4f ms par sub-level au repos)",
            one - base, many - base, (many - base) / 200));
        log(String.format("variation 200 contre 1 (tick total) : %.1f %%", 100 * (many - one) / one));
    }

    private static void freeze() {
        ServerLevel v = DimDispatch.create(server, VOID);
        frozen = A4Runner.assembleSmall(v, new BlockPos(0, 64, 0));
        Vector3d p = new Vector3d(frozen.logicalPose().position());
        SubLevelPhysicsSystem.get(v).getPipeline().addConstraint(null, frozen,
            new FixedConstraintConfiguration(p, new Vector3d(frozen.logicalPose().rotationPoint()), new Quaterniond()));
        frozenStart = p;
        log("sub-level gelé dans a5v : " + frozen.getUniqueId() + " pose=" + A1Runner.fmt(p));
    }

    private static void unloadFrozen() {
        DimBackend.select().unload(server, VOID);
        log("dimension a5v désenregistrée");
    }

    /** Recrée la dimension une fois le retrait différé terminé (rejoue l'étape tant que le niveau existe). */
    private static void recreateFrozen() {
        if (server.getLevel(A1Runner.levelKey(VOID)) != null) {
            next--;
            return;
        }
        UUID id = frozen.getUniqueId();
        ServerLevel v = DimBackend.select().create(server, VOID, A1Runner.voidGenerator(server), A1Runner.dimensionType(0f));
        frozen = (ServerSubLevel) SubLevelContainer.getContainer(v).getSubLevel(id);
        log("après rechargement, sub-level retrouvé : " + (frozen != null));
        if (frozen == null) {
            fail("sub-level gelé introuvable après rechargement");
            return;
        }
        SubLevelContainer.getContainer(v).addForceLoadTicket(frozen, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    }

    private static void kickAfterReload() {
        if (frozen == null) return;
        frozenStart = new Vector3d(frozen.logicalPose().position());
        RigidBodyHandle.of(frozen).applyLinearImpulse(new Vector3d(frozen.getMassTracker().getMass() * 10, 0, 0));
        log("impulsion linéaire appliquée au sub-level gelé rechargé");
    }

    private static void finish() {
        if (frozen != null) {
            double d = frozenStart.distance(new Vector3d(frozen.logicalPose().position()));
            log(String.format("déplacement du sub-level gelé après rechargement et impulsion : %.3e -> gel %s", d, d < 0.01 ? "conservé" : "PERDU (contrainte non persistée)"));
            if (d >= 0.01) fail("la contrainte de gel ne survit pas au rechargement de la dimension");
        }
        log("FIN A5 : " + (fails == 0 ? "RÉUSSI" : "ÉCHEC/PARTIEL (" + fails + ")"));
        server.halt(false);
    }

    private static void fail(String m) {
        fails++;
        log("ÉCHEC : " + m);
    }

    private static void log(String line) {
        SpaceTestMod.LOGGER.info("[A5] {}", line);
        try {
            Files.writeString(DIR.resolve("a5-run.log"), line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            SpaceTestMod.LOGGER.error("[A5] log", e);
        }
    }
}
