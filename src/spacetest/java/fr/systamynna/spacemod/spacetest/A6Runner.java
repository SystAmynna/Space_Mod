package fr.systamynna.spacemod.spacetest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.joml.Vector3d;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.phys.AABB;

/**
 * Test A6 du plan : sérialisation en données (SubLevelSerializer), libération du plot, réallocation.
 * Étapes : S puis L (contenu, vitesse, user_data, entités), conflit de plot, 1000 cycles pour la fuite.
 * Lancé par -Pa6=1 ; écrit test-results/A6/a6-run.log. Machine à étapes : chaque étape est suivie d'un délai en ticks.
 */
final class A6Runner {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, "a6");
    private static final Path DIR = Path.of(System.getProperty("spacetest.results", "../test-results"), "A6");
    private static final int LEAK_CYCLES = 1000;
    private static final int LEAK_DELAY = Integer.getInteger("spacetest.a6.delay", 5);

    private record Step(int delayAfter, Runnable run) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<Double> SER_MS = new ArrayList<>();
    private static final List<Double> DES_MS = new ArrayList<>();

    private static MinecraftServer server;
    private static ServerLevel level;
    private static int next = -1;
    private static int wait;
    private static int fails;

    // état partagé entre étapes
    private static ServerSubLevel sub;
    private static UUID id;
    private static String before;
    private static Vector3d velBefore;
    private static SubLevelData data;
    private static int occupiedBefore;
    private static int baseline;

    private A6Runner() {}

    static void registerPhysics() {
        DimensionPhysicsData.putPhysics(A1Runner.levelKey(ID), new DimensionPhysics(ID, 0,
            Optional.of(0f), Optional.of(new org.joml.Vector3f(0, 0, 0)), Optional.of(0.0), Optional.empty(), Optional.empty(), false));
    }

    static void start(MinecraftServer s) {
        server = s;
        try {
            Files.createDirectories(DIR);
            Files.writeString(DIR.resolve("a6-run.log"), "");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        level = DimDispatch.create(s, ID);
        baseline = occupied();
        log("DÉBUT A6, plots occupés au départ : " + baseline);

        for (boolean large : new boolean[] { false, true }) {
            String label = large ? "L" : "S";
            STEPS.add(new Step(5, () -> prepare(large, label)));
            STEPS.add(new Step(10, A6Runner::kick));
            STEPS.add(new Step(5, () -> serializeAndRemove(label)));
            STEPS.add(new Step(5, () -> checkFreedAndDeserialize(label)));
            STEPS.add(new Step(5, () -> verifyRestored(label)));
            STEPS.add(new Step(2, A6Runner::removeCurrent));
        }
        // conflit : un autre sub-level prend la place pendant que le premier est en données
        STEPS.add(new Step(5, () -> prepare(false, "conflit")));
        STEPS.add(new Step(10, A6Runner::kick));
        STEPS.add(new Step(5, () -> serializeAndRemove("conflit")));
        STEPS.add(new Step(5, A6Runner::occupyOldPlot));
        STEPS.add(new Step(5, A6Runner::deserializeInConflict));
        STEPS.add(new Step(20, A6Runner::removeAllAndReport));
        // fuite : 1000 cycles
        STEPS.add(new Step(20, () -> { prepare(false, "fuite"); occupiedBefore = occupied(); }));
        for (int i = 0; i < LEAK_CYCLES; i++) {
            STEPS.add(new Step(LEAK_DELAY, () -> { data = serialize(sub); remove(sub); }));
            STEPS.add(new Step(LEAK_DELAY, () -> { sub = deserialize(data); id = sub.getUniqueId(); }));
        }
        STEPS.add(new Step(2, A6Runner::finish));
        next = 0;
        wait = 5;
    }

    static void tick() {
        if (next < 0) return;
        if (wait-- > 0) return;
        if (next >= STEPS.size()) return;
        Step st = STEPS.get(next++);
        try {
            st.run().run();
        } catch (Throwable t) {
            fail("exception à l'étape " + next + " : " + t);
            SpaceTestMod.LOGGER.error("A6", t);
            server.halt(false);
        }
        wait = st.delayAfter();
    }

    // --- étapes ---------------------------------------------------------------------------------

    private static void prepare(boolean large, String label) {
        BlockPos origin = new BlockPos(label.equals("conflit") ? 1000 : 0, 64, label.equals("fuite") ? 2000 : 0);
        sub = large ? A4Runner.assembleLarge(level, origin) : A4Runner.assembleSmall(level, origin);
        A4Runner.spawnStands(level, sub, 1);
        CompoundTag user = new CompoundTag();
        user.putInt("spacemod_test", 42);
        sub.setUserDataTag(user);
        id = sub.getUniqueId();
        occupiedBefore = occupied();
        log("[" + label + "] prêt : " + id + " plots occupés=" + occupiedBefore);
    }

    /** Donne de la vitesse : l'impulsion est ignorée si elle est appliquée au tick même de l'assemblage. */
    private static void kick() {
        RigidBodyHandle.of(sub).applyLinearImpulse(new Vector3d(sub.getMassTracker().getMass() * 2, 0, 0));
    }

    private static String describe(ServerSubLevel s) {
        BlockPos min = new BlockPos(s.getPlot().getBoundingBox().minX(), s.getPlot().getBoundingBox().minY(), s.getPlot().getBoundingBox().minZ());
        BlockPos max = new BlockPos(s.getPlot().getBoundingBox().maxX(), s.getPlot().getBoundingBox().maxY(), s.getPlot().getBoundingBox().maxZ());
        int stands = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, AABB.encapsulatingFullBlocks(min, max).inflate(2)).size();
        return A2Runner.fingerprint(level, s) + ",entités=" + stands + ",user_data=" + s.getUserDataTag();
    }

    private static void serializeAndRemove(String label) {
        before = describe(sub);
        velBefore = new Vector3d(RigidBodyHandle.of(sub).getLinearVelocity());
        log("[" + label + "] avant : " + before + " pose=" + A1Runner.fmt(new Vector3d(sub.logicalPose().position()))
            + " plot=" + sub.getPlot().plotPos + " v=" + A1Runner.fmt(velBefore));
        data = serialize(sub);
        log("[" + label + "] sérialisé : " + sizeOf(data.fullTag()) + " octets compressés (" + data.fullTag().sizeInBytes() + " non compressés), "
            + String.format("%.2f", SER_MS.get(SER_MS.size() - 1)) + " ms");
        remove(sub);
    }

    private static void checkFreedAndDeserialize(String label) {
        ServerSubLevelContainer c = SubLevelContainer.getContainer(level);
        int occ = occupied();
        boolean gone = c.getSubLevel(id) == null;
        log("[" + label + "] après retrait : plots occupés=" + occ + " (avant " + occupiedBefore + "), absent du conteneur=" + gone);
        if (occ != occupiedBefore - 1) fail("[" + label + "] plot non libéré");
        if (!gone) fail("[" + label + "] sub-level toujours dans le conteneur");
        sub = deserialize(data);
        log("[" + label + "] désérialisé en " + String.format("%.2f", DES_MS.get(DES_MS.size() - 1)) + " ms");
    }

    private static void verifyRestored(String label) {
        String after = describe(sub);
        Vector3d v = new Vector3d(RigidBodyHandle.of(sub).getLinearVelocity());
        boolean same = after.equals(before) && sub.getUniqueId().equals(id);
        log("[" + label + "] après : " + after + " plot=" + sub.getPlot().plotPos + " v=" + A1Runner.fmt(v)
            + " facteur de vitesse≈" + (velBefore.x != 0 ? String.format("%.4f", v.x / velBefore.x) : "n/a") + " -> " + (same ? "contenu identique" : "CONTENU DIFFÉRENT"));
        if (!same) fail("[" + label + "] contenu restitué différent");
    }

    private static void removeCurrent() {
        remove(sub);
    }

    private static void occupyOldPlot() {
        UUID old = id;
        ServerSubLevel other = A4Runner.assembleSmall(level, new BlockPos(3000, 64, 0));
        log("[conflit] autre sub-level " + other.getUniqueId() + " plot=" + other.getPlot().plotPos + " (ancien plot de " + old + " : " + data.getOriginLoadedChunk() + ")");
        sub = other;
    }

    private static void deserializeInConflict() {
        try {
            ServerSubLevel restored = deserialize(data);
            if (restored == null) {
                fail("[conflit] fullyLoad renvoie null (plot pris) sans exception : les données seraient perdues si l'appelant ne le vérifie pas");
                return;
            }
            log("[conflit] restauré malgré tout : plot=" + restored.getPlot().plotPos + " uuid=" + restored.getUniqueId().equals(id) + " contenu=" + describe(restored));
            if (!describe(restored).equals(before)) fail("[conflit] contenu altéré");
        } catch (Throwable t) {
            fail("[conflit] désérialisation impossible : " + t);
        }
    }

    private static void removeAllAndReport() {
        for (ServerSubLevel s : new ArrayList<>(SubLevelContainer.getContainer(level).getAllSubLevels())) remove(s);
    }

    private static void finish() {
        int occ = occupied();
        log("[fuite] plots occupés : début=" + occupiedBefore + " fin=" + occ + " après " + LEAK_CYCLES + " cycles");
        if (occ != occupiedBefore) fail("[fuite] occupation modifiée");
        log(String.format("durées (ms) sérialisation p50=%.2f p95=%.2f ; désérialisation p50=%.2f p95=%.2f", pct(SER_MS, 50), pct(SER_MS, 95), pct(DES_MS, 50), pct(DES_MS, 95)));
        log("FIN A6 : " + (fails == 0 ? "RÉUSSI" : "ÉCHEC (" + fails + ")"));
        server.halt(false);
    }

    // --- outils ---------------------------------------------------------------------------------

    private static SubLevelData serialize(ServerSubLevel s) {
        long t0 = System.nanoTime();
        SubLevelData d = SubLevelSerializer.toData(s, List.of());
        SER_MS.add((System.nanoTime() - t0) / 1e6);
        return d;
    }

    private static ServerSubLevel deserialize(SubLevelData d) {
        long t0 = System.nanoTime();
        ServerSubLevel s = SubLevelSerializer.fullyLoad(level, SubLevelSerializer.fromData(d.fullTag()));
        DES_MS.add((System.nanoTime() - t0) / 1e6);
        // le ticket de chargement n'est pas dans les données : sans lui Sable décharge le sub-level (aucun joueur ici)
        if (s != null) SubLevelContainer.getContainer(level).addForceLoadTicket(s, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
        return s;
    }

    private static void remove(ServerSubLevel s) {
        SubLevelContainer.getContainer(level).removeSubLevel(s, SubLevelRemovalReason.REMOVED);
    }

    private static int occupied() {
        return SubLevelContainer.getContainer(level).getOccupancy().cardinality();
    }

    private static int sizeOf(CompoundTag tag) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, out);
            return out.size();
        } catch (IOException e) {
            return -1;
        }
    }

    private static double pct(List<Double> l, int p) {
        double[] a = l.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(a);
        return a.length == 0 ? Double.NaN : a[Math.min(a.length - 1, (int) Math.ceil(p / 100.0 * a.length) - 1)];
    }

    private static void fail(String m) {
        fails++;
        log("ÉCHEC : " + m);
    }

    private static void log(String line) {
        SpaceTestMod.LOGGER.info("[A6] {}", line);
        try {
            Files.writeString(DIR.resolve("a6-run.log"), line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            SpaceTestMod.LOGGER.error("[A6] log", e);
        }
    }
}
