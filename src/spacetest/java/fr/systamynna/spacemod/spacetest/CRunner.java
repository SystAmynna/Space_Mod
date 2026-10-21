package fr.systamynna.spacemod.spacetest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

/**
 * Tests d'intégration C3 (frontière d'un hôte, marge M), C7 (effet d'inertie) et C2 (préchargement) côté serveur, sans client.
 * Lancé par -Pc=3,7,2 (liste des phases). Valeurs de test choisies par l'agent (voir les constantes), à remplacer en production.
 * Écrit test-results/C3, C7, C2.
 */
final class CRunner {
    // --- valeurs de test (cohérentes entre elles) ---
    static final double G = 9.81;                         // blocs/s² pour 1 g
    static final double RADIUS_CRUISER = 300;             // R d'un croiseur
    static final double RADIUS_PLANET = 2000;             // R d'une planète
    static final double MASSLOCK_VMIN = 10, MASSLOCK_K = 0.1; // plafond = vmin + k·d (d = distance au volume logique, blocs/s ; k en 1/s)
    static final double HOST_VEL_X = 5;                   // vitesse stratégique de l'hôte de test (blocs/s)

    private static final ResourceLocation C3H = id("c3h"), C3T = id("c3t"), C7H = id("c7h"), C7T = id("c7t"), C7E = id("c7e");
    private static final Path ROOT = Path.of(System.getProperty("spacetest.results", "../test-results"));

    private static ResourceLocation id(String p) { return ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, p); }

    interface Phase { String name(); void begin(); boolean tick(); }

    private static final List<Phase> PHASES = new ArrayList<>();
    private static int cur = -1;
    static MinecraftServer server;
    private static int fails;

    private CRunner() {}

    static void registerPhysics() {
        CGroup.registerPhysics();
        for (ResourceLocation r : List.of(C3H, C3T, C7H, C7T, C7E)) {
            DimensionPhysicsData.putPhysics(A1Runner.levelKey(r), new DimensionPhysics(r, 0,
                Optional.of(0f), Optional.of(new org.joml.Vector3f(0, 0, 0)), Optional.of(0.0), Optional.empty(), Optional.empty(), false));
        }
    }

    static void start(MinecraftServer s, String which) {
        server = s;
        for (String w : which.split(",")) {
            switch (w.trim()) {
                case "3" -> PHASES.add(new C3());
                case "7" -> PHASES.add(new C7());
                case "2" -> PHASES.add(new C2());
                case "1" -> PHASES.add(new CGroup.C1());
                case "6" -> PHASES.add(new CGroup.C6());
                default -> SpaceTestMod.LOGGER.error("[C] phase inconnue {}", w);
            }
        }
        cur = 0;
        begin();
    }

    private static void begin() {
        try {
            PHASES.get(cur).begin();
        } catch (Throwable t) {
            crash(t);
        }
    }

    static void tick() {
        if (cur < 0 || cur >= PHASES.size()) return;
        try {
            if (!PHASES.get(cur).tick()) return;
        } catch (Throwable t) {
            crash(t);
            return;
        }
        cur++;
        if (cur >= PHASES.size()) server.halt(false);
        else begin();
    }

    private static void crash(Throwable t) {
        SpaceTestMod.LOGGER.error("[C] exception", t);
        logTo(PHASES.get(cur).name(), "ÉCHEC : exception " + t);
        fails++;
        cur++;
        if (cur >= PHASES.size()) server.halt(false);
        else begin();
    }

    static void logTo(String phase, String line) {
        SpaceTestMod.LOGGER.info("[{}] {}", phase, line);
        try {
            Path dir = ROOT.resolve(phase);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(phase.toLowerCase() + "-run.log"), line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            SpaceTestMod.LOGGER.error("[C] log", e);
        }
    }

    static void resetLog(String phase) {
        try {
            Files.createDirectories(ROOT.resolve(phase));
            Files.writeString(ROOT.resolve(phase).resolve(phase.toLowerCase() + "-run.log"), "");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // --- outils ---

    static double radiusOf(ServerSubLevel s) {
        BoundingBox3ic b = s.getPlot().getBoundingBox();
        double dx = b.maxX() - b.minX() + 1, dy = b.maxY() - b.minY() + 1, dz = b.maxZ() - b.minZ() + 1;
        return 0.5 * Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static int standsOf(ServerSubLevel s) {
        BoundingBox3ic b = s.getPlot().getBoundingBox();
        AABB box = AABB.encapsulatingFullBlocks(new BlockPos(b.minX(), b.minY(), b.minZ()), new BlockPos(b.maxX(), b.maxY(), b.maxZ())).inflate(2);
        return s.getLevel().getEntitiesOfClass(ArmorStand.class, box).size();
    }

    static Vector3d vel(ServerSubLevel s) { return new Vector3d(RigidBodyHandle.of(s).getLinearVelocity()); }

    static void setVel(ServerSubLevel s, Vector3d v) {
        SubLevelPhysicsSystem.get(s.getLevel()).getPipeline().resetVelocity(s);
        RigidBodyHandle.of(s).applyLinearImpulse(new Vector3d(v).mul(s.getMassTracker().getMass()));
    }

    static void teleport(ServerSubLevel s, Vector3d p) {
        SubLevelPhysicsSystem.get(s.getLevel()).getPipeline().teleport(s, p, new Quaterniond(s.logicalPose().orientation()));
    }

    static double pct(List<Double> l, int p) {
        List<Double> s = new ArrayList<>(l);
        s.sort(Double::compare);
        return s.isEmpty() ? Double.NaN : s.get(Math.max(0, (int) Math.ceil(p / 100.0 * s.size()) - 1));
    }

    static double usedMb() {
        System.gc();
        Runtime r = Runtime.getRuntime();
        return (r.totalMemory() - r.freeMemory()) / 1048576.0;
    }

    static void removeAllSafe(ServerLevel l) { CGroup.removeAll(l); }

    static int occupied(ServerLevel l) { return SubLevelContainer.getContainer(l).getOccupancy().cardinality(); }

    // =============================================================================================
    // C3 : frontière avec la marge M
    // =============================================================================================
    static final class C3 implements Phase {
        record Series(String label, boolean torus, double amp, int cycles) {}

        static final int PERIOD = 30;                                  // ticks
        ServerLevel host, tampon;
        ServerSubLevel mini;
        double M;
        double rt = RADIUS_CRUISER;                                    // rayon du volume (sphère) ou du tube (tore)
        final double ringR = 1200;                                     // rayon majeur du tore
        final Vector3d hostVel = new Vector3d(HOST_VEL_X, 0, 0);
        final Vector3d miniVelStrategic = new Vector3d(30, 10, -20);   // vitesse stratégique du passager de test
        List<Series> series = new ArrayList<>();
        int verified, si = -1, tick, transfers, enters, exits, wrongThreshold, passengerLoss, velFails, wait = 10;
        boolean inHost, started;
        double hostX;
        double worstVelRel, worstVelAbs;
        final List<Double> ms = new ArrayList<>();
        ServerSubLevel pendingVel; Vector3d pendingV; int pendingAt = -1; Vector3d expectedStrategic; boolean verifyHost; int verifyAt = -1;
        double mem0;
        long ticksTotal;
        int occ0H, occ0T;
        String phaseNote = "";

        public String name() { return "C3"; }

        public void begin() {
            resetLog("C3");
            host = DimDispatch.create(server, C3H);
            tampon = DimDispatch.create(server, C3T);
            // M = rayon de la plus grande Mini admise (Mini L)
            ServerSubLevel probe = A4Runner.assembleLarge(host, new BlockPos(0, 64, 0));
            M = Math.ceil(radiusOf(probe));
            SubLevelContainer.getContainer(host).removeSubLevel(probe, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
            logTo("C3", "DÉBUT C3 ; R(croiseur)=" + rt + " ; M = rayon de la Mini L arrondi = " + M + " (formule de test : M = rayon de la Mini ; l'agent la juge adaptée pour une sphère, voir rapport) ; période d'oscillation " + PERIOD + " ticks ; vitesse de l'hôte " + hostVel.x + " bloc/s");
            series.add(new Series("sphère, amplitude M/2", false, 0.5 * M, 20));
            series.add(new Series("sphère, amplitude 0,99 M", false, 0.99 * M, 20));
            series.add(new Series("sphère, amplitude 1,1 M", false, 1.1 * M, 10));
            series.add(new Series("sphère, amplitude 2M", false, 2 * M, 50));
            series.add(new Series("sphère, amplitude 4M (1000 franchissements)", false, 4 * M, 500));
            series.add(new Series("tore, amplitude 4M", true, 4 * M, 100));
            mem0 = usedMb();
            logTo("C3", String.format("mémoire utilisée au départ : %.1f Mo", mem0));
        }

        // distance signée au bord de la forme, mesurée comme dans la spécification : d = distance à l'axe/centre
        double dist(Series s, double t) { return rt + s.amp * Math.sin(2 * Math.PI * t / PERIOD); }

        public boolean tick() {
            if (wait-- > 0) return false;
            // vérifications différées
            if (pendingAt >= 0 && tick == pendingAt) {
                setVel(pendingVel, pendingV);
                pendingAt = -1;
            }
            if (verifyAt >= 0 && tick == verifyAt) {
                verifyAt = -1;
                Vector3d v = vel(mini);
                Vector3d strategic = new Vector3d(v).add(verifyHost ? hostVel : new Vector3d());
                verified++;
                double abs = strategic.distance(expectedStrategic), rel = abs / expectedStrategic.length();
                worstVelRel = Math.max(worstVelRel, rel);
                worstVelAbs = Math.max(worstVelAbs, abs);
                if (rel > 1e-6) velFails++;
            }
            if (si >= 0 && si < series.size() && started) {
                Series s = series.get(si);
                if (tick >= s.cycles * PERIOD) { endSeries(s); started = false; return false; }
                step(s);
                tick++;
                ticksTotal++;
                hostX += hostVel.x / 20.0;
                return false;
            }
            // lancer la série suivante
            si++;
            if (si >= series.size()) return finish();
            Series s = series.get(si);
            if (mini == null) {
                mini = A4Runner.assembleSmall(tampon, new BlockPos(0, 64, 0));
                A4Runner.spawnStands(tampon, mini, 1);
                inHost = false;
                occ0H = occupied(host); occ0T = occupied(tampon);
                logTo("C3", "plots occupés au départ : hôte=" + occ0H + " tampon=" + occ0T);
            }
            tick = 0; transfers = 0; enters = 0; exits = 0; wrongThreshold = 0; passengerLoss = 0; velFails = 0; worstVelRel = 0; worstVelAbs = 0; verified = 0; ms.clear();
            setVel(mini, new Vector3d(miniVelStrategic).sub(inHost ? hostVel : new Vector3d()));
            started = true;
            return false;
        }

        void step(Series s) {
            double d = dist(s, tick + 1.0);
            if (!inHost && d <= rt - M) {
                if (d > rt - M + 1e-9) wrongThreshold++;
                transfer(true, d);
                enters++;
            } else if (inHost && d >= rt + M) {
                if (d < rt + M - 1e-9) wrongThreshold++;
                transfer(false, d);
                exits++;
            }
        }

        void transfer(boolean toHost, double d) {
            ServerLevel from = toHost ? tampon : host, to = toHost ? host : tampon;
            Vector3d vBefore = vel(mini);
            Vector3d strategic = new Vector3d(vBefore).add(inHost ? hostVel : new Vector3d());
            long t0 = System.nanoTime();
            ServerSubLevel moved = A9Runner.serializeReload(mini, from, to);
            ms.add((System.nanoTime() - t0) / 1e6);
            transfers++;
            if (moved == null) { fails++; logTo("C3", "ÉCHEC : transfert refusé"); return; }
            mini = moved;
            inHost = toHost;
            if (standsOf(mini) != 1) passengerLoss++;
            // passage : la vitesse locale devient vitesse stratégique moins vitesse de l'hôte (et inversement), la pose reste cohérente avec la simulation
            Vector3d target = new Vector3d(strategic).sub(toHost ? hostVel : new Vector3d());
            pendingVel = mini; pendingV = target; pendingAt = tick + 2;
            expectedStrategic = strategic; verifyHost = toHost; verifyAt = tick + 6;
        }

        void endSeries(Series s) {
            if (inHost) { transfer(false, rt + M); pendingAt = -1; verifyAt = -1; transfers--; }
            double[] stats = { pct(ms, 50), pct(ms, 95), ms.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN) };
            logTo("C3", String.format("[%s] %d ticks, %d entrées + %d sorties = %d transferts ; seuils violés=%d ; passager perdu=%d fois ; continuité de la vitesse stratégique (%d contrôles) : pire écart relatif %.2e (absolu %.2e bloc/s), >1e-6 : %d fois ; durée de transfert p50=%.2f ms p95=%.2f ms max=%.2f ms ; plots occupés hôte=%d tampon=%d",
                s.label, tick, enters, exits, transfers, wrongThreshold, passengerLoss, verified, worstVelRel, worstVelAbs, velFails, stats[0], stats[1], stats[2], occupied(host), occupied(tampon)));
            boolean expectNone = s.amp < M;
            if (expectNone && transfers != 0) { fails++; logTo("C3", "ÉCHEC : " + transfers + " transferts avec une amplitude inférieure à M"); }
            if (!expectNone && transfers == 0) { fails++; logTo("C3", "ÉCHEC : aucun transfert avec une amplitude supérieure à M"); }
            if (wrongThreshold > 0 || passengerLoss > 0 || velFails > 0) { fails++; logTo("C3", "ÉCHEC : voir ligne ci-dessus"); }
            if (occupied(host) + occupied(tampon) != occ0H + occ0T) { fails++; logTo("C3", "ÉCHEC : fuite de plots"); }
            logTo("C3", String.format("  mémoire après la série : %.1f Mo (départ %.1f Mo)", usedMb(), mem0));
        }

        boolean finish() {
            double m1 = usedMb();
            logTo("C3", String.format("FIN C3 : mémoire %.1f Mo -> %.1f Mo, plots occupés hôte=%d tampon=%d (départ %d/%d) -> %s", mem0, m1, occupied(host), occupied(tampon), occ0H, occ0T, fails == 0 ? "RÉUSSI" : "ÉCHEC/PARTIEL (" + fails + ")"));
            return true;
        }
    }

    // =============================================================================================
    // C7 : effet d'inertie sur les Minis d'un hôte mobile
    // =============================================================================================
    static final class C7 implements Phase {
        record Segment(String label, Vector3d accel, int ticks, Quaterniond orient, double radius) {}

        ServerLevel host, tampon;
        final List<ServerSubLevel> uncoupled = new ArrayList<>(), coupled = new ArrayList<>();
        final Set<UUID> coupledIds = new HashSet<>();
        final List<Vector3d> coupledStart = new ArrayList<>();
        final List<Segment> segments = new ArrayList<>();
        int seg = -1, t;
        double M;
        boolean prevValid;
        final List<Vector3d> prevVel = new ArrayList<>();
        Vector3d prevExpected;
        double worstRel, sumRel; int n;
        final Vector3d hostV = new Vector3d(HOST_VEL_X, 0, 0);
        Vector3d hostV0;
        final List<Double> tickMs = new ArrayList<>();
        double maxCoupledDrift;
        boolean ejectPhase;
        final Set<UUID> ejected = new HashSet<>();
        double worstEjectRel;
        int wait = 10;
        ServerSubLevel lateCoupled; Vector3d lateCoupledPos; int lateCoupledAt;
        double lateDrift;
        int ejectedCount;

        public String name() { return "C7"; }

        public void begin() {
            resetLog("C7");
            host = DimDispatch.create(server, C7H);
            tampon = DimDispatch.create(server, C7T);
            ServerSubLevel probe = A4Runner.assembleLarge(host, new BlockPos(0, 64, 0));
            M = Math.ceil(radiusOf(probe));
            SubLevelContainer.getContainer(host).removeSubLevel(probe, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
            logTo("C7", "DÉBUT C7 ; R=" + RADIUS_CRUISER + " (croiseur), M=" + M + ", 1 g = " + G + " bloc/s², vitesse initiale de l'hôte " + HOST_VEL_X + " bloc/s");
            for (int i = 0; i < 3; i++) {
                ServerSubLevel c = A4Runner.assembleSmall(host, new BlockPos(200 + i * 15, 64, 200));
                Vector3d p = new Vector3d(c.logicalPose().position());
                SubLevelPhysicsSystem.get(host).getPipeline().addConstraint(null, c,
                    new FixedConstraintConfiguration(p, new Vector3d(c.logicalPose().rotationPoint()), new Quaterniond()));
                coupled.add(c); coupledIds.add(c.getUniqueId()); coupledStart.add(p);
            }
            double[] xs = { -50, -10, 30, 70 };
            for (int i = 0; i < 4; i++) {
                ServerSubLevel u = A4Runner.assembleSmall(host, new BlockPos(0, 64, 20 * (i + 1)));
                A4Runner.spawnStands(host, u, 1);
                teleport(u, new Vector3d(xs[i], 64, 0));
                setVel(u, new Vector3d());
                uncoupled.add(u);
            }
            double big = 1e9;
            Quaterniond id = new Quaterniond();
            segments.add(new Segment("accélération douce 0,5 g (+x), orientation identité", new Vector3d(0.5 * G, 0, 0), 40, id, big));
            segments.add(new Segment("accélération forte 5 g (+x), hôte tourné de 0,7 rad autour de Y", new Vector3d(5 * G, 0, 0), 40, new Quaterniond().rotateY(0.7), big));
            segments.add(new Segment("freinage 5 g (-x), hôte tourné de 1,1 rad autour de Z", new Vector3d(-5 * G, 0, 0), 40, new Quaterniond().rotateZ(1.1), big));
            segments.add(new Segment("changement de direction (+y puis -z), orientation quelconque", new Vector3d(0, 5 * G, 0), 20, new Quaterniond().rotateXYZ(0.3, -0.9, 1.7), big));
            segments.add(new Segment("changement de direction (-z)", new Vector3d(0, 0, -5 * G), 20, new Quaterniond().rotateXYZ(-1.3, 0.4, 0.8), big));
            for (int k = 0; k < 10; k++) {
                segments.add(new Segment("accélérations répétées " + (k + 1) + "/10 (" + (k % 2 == 0 ? "+" : "-") + "x, 2,5 g)", new Vector3d((k % 2 == 0 ? 1 : -1) * 2.5 * G, 0, 0), 20, id, big));
            }
            segments.add(new Segment("couplage d'une Mini pendant la variation (+x, 2,5 g)", new Vector3d(2.5 * G, 0, 0), 30, id, big));
            segments.add(new Segment("éjection en cascade (+x, 5 g, R=300)", new Vector3d(5 * G, 0, 0), 600, id, RADIUS_CRUISER));
        }

        public boolean tick() {
            if (wait-- > 0) return false;
            if (finishing) return finishStep();
            if (seg < 0 || segDone()) {
                if (seg >= 0) endSegment();
                seg++;
                if (seg >= segments.size()) return finish();
                t = 0;
                startSegment();
            }
            Segment s = segments.get(seg);
            double ms = server.getAverageTickTimeNanos() / 1e6;
            tickMs.add(ms);
            // dv de l'hôte pour ce tick, exprimé dans le référentiel stratégique ; local = q^-1 · dv
            Vector3d dvS = new Vector3d(s.accel()).div(20.0);
            Vector3d dvLocal = new Quaterniond(s.orient()).invert().transform(new Vector3d(dvS));
            // mesure du tick précédent
            List<ServerSubLevel> act = activeUncoupled();
            if (prevValid && prevVel.size() == act.size()) {
                for (int i = 0; i < act.size(); i++) {
                    Vector3d now = vel(act.get(i));
                    Vector3d delta = new Vector3d(now).sub(prevVel.get(i));
                    double rel = delta.distance(prevExpected) / prevExpected.length();
                    worstRel = Math.max(worstRel, rel); sumRel += rel; n++;
                }
            }
            if (ejectPhase) checkEjection(s);
            prevVel.clear();
            act = activeUncoupled();
            for (ServerSubLevel u : act) prevVel.add(vel(u));
            if (!applyInertia(server, C7H, dvLocal, coupledIds)) { fails++; logTo("C7", "ÉCHEC : effet non appliqué"); }
            prevExpected = new Vector3d(dvLocal).negate();
            prevValid = !act.isEmpty();
            hostV.add(dvS);
            t++;
            if (s.label().startsWith("couplage") && t == 10 && lateCoupled == null && !act.isEmpty()) {
                lateCoupled = act.get(0);
                setVel(lateCoupled, new Vector3d());
                Vector3d p = new Vector3d(lateCoupled.logicalPose().position());
                SubLevelPhysicsSystem.get(host).getPipeline().addConstraint(null, lateCoupled,
                    new FixedConstraintConfiguration(p, new Vector3d(lateCoupled.logicalPose().rotationPoint()), new Quaterniond()));
                coupledIds.add(lateCoupled.getUniqueId());
                lateCoupledAt = t + 5;
                logTo("C7", "Mini couplée pendant la variation à pose=" + A1Runner.fmt(p) + " (vitesse remise à zéro avant le couplage)");
            }
            if (lateCoupled != null && t == lateCoupledAt) lateCoupledPos = new Vector3d(lateCoupled.logicalPose().position());
            if (lateCoupled != null && lateCoupledPos != null) lateDrift = Math.max(lateDrift, new Vector3d(lateCoupled.logicalPose().position()).distance(lateCoupledPos));
            for (int i = 0; i < coupled.size(); i++) maxCoupledDrift = Math.max(maxCoupledDrift, new Vector3d(coupled.get(i).logicalPose().position()).distance(coupledStart.get(i)));
            return false;
        }

        boolean segDone() {
            if (ejectPhase) return (ejectedCount >= 3 && pendingEject.isEmpty()) || t >= segments.get(seg).ticks();
            return t >= segments.get(seg).ticks();
        }

        List<ServerSubLevel> activeUncoupled() {
            List<ServerSubLevel> l = new ArrayList<>();
            for (ServerSubLevel u : uncoupled) if (!ejected.contains(u.getUniqueId()) && !coupledIds.contains(u.getUniqueId()) && !u.isRemoved()) l.add(u);
            return l;
        }

        void startSegment() {
            Segment s = segments.get(seg);
            worstRel = 0; sumRel = 0; n = 0; prevValid = false; tickMs.clear();
            ejectPhase = s.label().startsWith("éjection");
            if (ejectPhase) {
                // repartir de Minis non couplées au repos dans l'hôte, vitesse stratégique = vitesse de l'hôte
                hostV0 = new Vector3d(hostV);
                double[] xs = { -50, -10, 70 };
                int i = 0;
                for (ServerSubLevel u : activeUncoupled()) { teleport(u, new Vector3d(xs[i++ % 3], 64, 0)); setVel(u, new Vector3d()); }
                logTo("C7", "début éjection : vitesse stratégique de l'hôte " + A1Runner.fmt(hostV0) + ", " + activeUncoupled().size() + " Minis non couplées en suspension, positions x=-50, -10, 70 ; seuil R+M=" + (RADIUS_CRUISER + M));
            }
        }

        void checkEjection(Segment s) {
            for (ServerSubLevel u : activeUncoupled()) {
                Vector3d p = new Vector3d(u.logicalPose().position());
                if (p.distance(new Vector3d(0, 64, 0)) >= s.radius() + M) {
                    Vector3d lv = vel(u);
                    Vector3d strategic = new Vector3d(lv).add(hostV);   // orientation identité : référentiel local = stratégique
                    double rel = strategic.distance(hostV0) / hostV0.length();
                    long t0 = System.nanoTime();
                    ServerSubLevel moved = A9Runner.serializeReload(u, host, tampon);
                    double dur = (System.nanoTime() - t0) / 1e6;
                    if (moved == null) { fails++; logTo("C7", "ÉCHEC : éjection refusée"); continue; }
                    ejected.add(u.getUniqueId());
                    ejectedCount++;
                    worstEjectRel = Math.max(worstEjectRel, rel);
                    logTo("C7", String.format("Mini éjectée au tick %d à distance %.1f (R+M=%.1f) ; vitesse stratégique avant éjection %s contre vitesse initiale %s : écart relatif %.2e ; transfert %.2f ms", t, p.distance(new Vector3d(0, 64, 0)), s.radius() + M, A1Runner.fmt(strategic), A1Runner.fmt(hostV0), rel, dur));
                    // après le transfert, la vitesse dans le tampon est contrôlée
                    pendingEject.add(new Object[] { moved, strategic, t + 6, false });
                }
            }
            for (var it = pendingEject.iterator(); it.hasNext();) {
                Object[] e = it.next();
                if (t >= (int) e[2] - 4 && !(boolean) e[3]) { setVel((ServerSubLevel) e[0], (Vector3d) e[1]); e[3] = true; }   // impulsion ignorée au tick de création : donnée 2 ticks plus tard
                if (t >= (int) e[2]) {
                    ServerSubLevel m = (ServerSubLevel) e[0]; Vector3d st = (Vector3d) e[1];
                    double rel = vel(m).distance(st) / st.length();
                    logTo("C7", String.format("  vitesse dans la Tampon Spatiale après éjection : %s contre %s, écart relatif %.2e", A1Runner.fmt(vel(m)), A1Runner.fmt(st), rel));
                    worstEjectRel = Math.max(worstEjectRel, rel);
                    it.remove();
                }
            }
        }
        final List<Object[]> pendingEject = new ArrayList<>();

        void endSegment() {
            Segment s = segments.get(seg);
            double avgTick = tickMs.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
            if (!ejectPhase) {
                logTo("C7", String.format("[%s] %d mesures de Δv ; écart relatif à la variation attendue : max %.3e moyen %.3e (critère 1 %%) ; dérive max des Minis couplées %.5f bloc ; tick moyen %.2f ms", s.label(), n, worstRel, n == 0 ? 0 : sumRel / n, maxCoupledDrift, avgTick));
                if (n > 0 && worstRel > 0.01) { fails++; logTo("C7", "ÉCHEC : écart supérieur à 1 %"); }
            } else {
                logTo("C7", String.format("[%s] éjectées : %d ; écart relatif max de continuité : %.3e ; tick moyen %.2f ms", s.label(), ejectedCount, worstEjectRel, avgTick));
                if (ejectedCount < 3 || worstEjectRel > 1e-6) { fails++; logTo("C7", "ÉCHEC : éjection incomplète ou continuité > 1e-6"); }
            }
            if (s.label().startsWith("couplage")) {
                logTo("C7", String.format("  Mini couplée en cours de variation : dérive %.5f bloc depuis 5 ticks après le couplage", lateDrift));
            }
        }

        boolean finishing; int finishWait;

        boolean finish() {
            logTo("C7", String.format("dérive maximale des Minis couplées sur tout le test : %.5f bloc (critère 0,01)", maxCoupledDrift));
            if (maxCoupledDrift > 0.01) { fails++; logTo("C7", "ÉCHEC : dérive des Minis couplées"); }
            // cas limite : dimension déchargée pendant la variation -> l'effet ne s'applique pas
            DimDispatch.create(server, C7E);
            DimBackend.select().unload(server, C7E);
            finishing = true;
            return false;
        }

        boolean finishStep() {
            if (server.getLevel(A1Runner.levelKey(C7E)) != null && finishWait++ < 400) return false;
            boolean applied;
            try {
                applied = applyInertia(server, C7E, new Vector3d(1, 0, 0), Set.of());
            } catch (Throwable t) {
                logTo("C7", "ÉCHEC : exception de l'effet sur une dimension déchargée : " + t);
                fails++;
                return true;
            }
            logTo("C7", "dimension hôte déchargée pendant la variation : effet appliqué=" + applied + " (attendu : non), niveau retiré après " + finishWait + " ticks");
            if (applied) { fails++; logTo("C7", "ÉCHEC : l'effet s'applique à une dimension déchargée"); }
            logTo("C7", "FIN C7 : " + (fails == 0 ? "RÉUSSI (partie automatique)" : "ÉCHEC/PARTIEL (" + fails + ")"));
            return true;
        }
    }

    /** Applique l'effet d'inertie : variation de vitesse locale opposée à celle de l'hôte, aux Minis non couplées. Faux si la dimension n'est pas chargée. */
    static boolean applyInertia(MinecraftServer s, ResourceLocation dim, Vector3d dvLocal, Set<UUID> coupled) {
        ServerLevel l = s.getLevel(A1Runner.levelKey(dim));
        if (l == null) return false;
        for (var sub : SubLevelContainer.getContainer(l).getAllSubLevels()) {
            if (coupled.contains(sub.getUniqueId())) continue;
            if (!(sub instanceof ServerSubLevel ss)) continue;
            RigidBodyHandle.of(ss).applyLinearImpulse(new Vector3d(dvLocal).negate().mul(ss.getMassTracker().getMass()));
        }
        return true;
    }

    // =============================================================================================
    // C2 : préchargement d'une dimension hôte
    // =============================================================================================
    static final class C2 implements Phase {
        record Case(String label, boolean flat, boolean fromDisk) {}
        static final int[] RADII = { 8, 16, 32 };
        final List<Case> cases = List.of(new Case("terrain plat, généré", true, false), new Case("terrain plat, déjà sur disque", true, true),
            new Case("terrain réaliste (générateur de l'Overworld), généré", false, false), new Case("terrain réaliste, déjà sur disque", false, true));
        int ci = -1, state; // 0 = demander, 1 = mesurer, 2 = attendre le retrait
        ServerLevel level; ResourceLocation dim; long t0; int tick;
        final double[] done = new double[3];
        List<int[]> pending = new ArrayList<>();
        final List<String> summary = new ArrayList<>();
        final double[] genTimes = new double[4], gen8 = new double[4], gen16 = new double[4];
        int stage;

        public String name() { return "C2"; }

        public void begin() {
            resetLog("C2");
            logTo("C2", "DÉBUT C2 ; mesures côté serveur uniquement (le client n'est pas lancé) ; valeurs de test : R(croiseur)=300, R(planète)=2000, M=15, mass lock v(d)=" + MASSLOCK_VMIN + " + " + MASSLOCK_K + "·d blocs/s (d = distance au volume logique)");
        }

        static ChunkGenerator flatGen(MinecraftServer s) {
            var ra = s.registryAccess();
            return new FlatLevelSource(FlatLevelGeneratorSettings.getDefault(ra.lookupOrThrow(Registries.BIOME), ra.lookupOrThrow(Registries.STRUCTURE_SET), ra.lookupOrThrow(Registries.PLACED_FEATURE)));
        }

        public boolean tick() {
            if (state == 0) {
                ci++;
                if (ci >= cases.size()) { report(); return true; }
                Case c = cases.get(ci);
                String name = c.flat() ? "c2f" : "c2n";
                dim = id(name);
                if (!c.fromDisk()) {
                    ChunkGenerator g = c.flat() ? flatGen(server) : server.overworld().getChunkSource().getGenerator();
                    level = DimBackend.select().create(server, dim, g, null);
                } else {
                    ChunkGenerator g = c.flat() ? flatGen(server) : server.overworld().getChunkSource().getGenerator();
                    level = DimBackend.select().create(server, dim, g, null);
                }
                t0 = System.nanoTime();
                tick = 0;
                int r = RADII[0];
                stage = 0;
                pending = new ArrayList<>();
                for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) { level.setChunkForced(x, z, true); pending.add(new int[] { x, z }); }
                done[0] = done[1] = done[2] = -1;
                state = 1;
                return false;
            }
            if (state == 1) {
                tick++;
                pending.removeIf(p -> level.getChunkSource().getChunkNow(p[0], p[1]) != null);
                double ms = (System.nanoTime() - t0) / 1e6;
                if (pending.isEmpty() || ms > 900_000) {
                    done[stage] = ms;
                    if (!pending.isEmpty()) logTo("C2", "ÉCHEC : rayon " + RADII[stage] + " incomplet, " + pending.size() + " chunks manquants");
                    if (stage + 1 < RADII.length) {
                        int r0 = RADII[stage], r1 = RADII[++stage];
                        for (int x = -r1; x <= r1; x++) for (int z = -r1; z <= r1; z++) if (Math.abs(x) > r0 || Math.abs(z) > r0) { level.setChunkForced(x, z, true); pending.add(new int[] { x, z }); }
                        return false;
                    }
                    Case c = cases.get(ci);
                    String line = String.format("[%s] chunks disponibles (serveur, cumulé depuis la création/chargement de la dimension, du centre vers l'extérieur) : rayon 8 = %.0f ms, rayon 16 = %.0f ms, rayon 32 = %.0f ms", c.label(), done[0], done[1], done[2]);
                    logTo("C2", line);
                    genTimes[ci] = done[2]; gen8[ci] = done[0]; gen16[ci] = done[1];
                    int r = RADII[2];
                    for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) level.setChunkForced(x, z, false);
                    level.save(null, true, false);   // vide les chunks sur disque avant le déchargement
                    DimBackend.select().unload(server, dim);
                    state = 2;
                    waitUnload = 0;
                }
                return false;
            }
            // état 2 : attendre le retrait différé de la dimension
            if (server.getLevel(A1Runner.levelKey(dim)) != null && waitUnload++ < 600) return false;
            if (server.getLevel(A1Runner.levelKey(dim)) != null) { logTo("C2", "ÉCHEC : la dimension n'a pas été retirée à temps"); fails++; }
            state = 0;
            return false;
        }
        int waitUnload;

        /** Temps passé en vol au plafond du mass lock entre l'entrée du volume d'exclusion (2R) et R-M : intégrale de dd/(vmin + k·d). */
        static double available(double R, double M, double vmin, double k) {
            double D = R + M;
            return Math.log((vmin + k * D) / vmin) / k;
        }

        void report() {
            logTo("C2", "--- temps disponible entre l'entrée du volume d'exclusion (2R) et l'entrée dans l'hôte (R-M), en volant au plafond du mass lock ---");
            double M = 15;
            double worstLoad = 0;
            for (double d : genTimes) worstLoad = Math.max(worstLoad, d);
            double load = genTimes[2];
            logTo("C2", String.format("temps de chargement serveur du pire cas (terrain réaliste généré) : rayon 8 = %.1f s, rayon 16 = %.1f s, rayon 32 = %.1f s", gen8[2] / 1000, gen16[2] / 1000, load / 1000));
            for (double R : new double[] { RADIUS_CRUISER, RADIUS_PLANET, 6000 }) {
                double T = available(R, M, MASSLOCK_VMIN, MASSLOCK_K);
                logTo("C2", String.format("R=%.0f, M=%.0f, mass lock %.0f+%.2f·d : temps disponible %.1f s ; marge rayon 16 = %.1f, rayon 32 = %.1f (critère >= 2)", R, M, MASSLOCK_VMIN, MASSLOCK_K, T, T / (gen16[2] / 1000), T / (load / 1000)));
            }
            logTo("C2", "--- variation des paramètres pour R=2000 : plafond de vitesse maximale pour garder une marge de 2 ---");
            for (double k : new double[] { 0.02, 0.05, 0.1, 0.2, 0.5, 1.0 }) {
                double T = available(RADIUS_PLANET, M, MASSLOCK_VMIN, k);
                logTo("C2", String.format("k=%.2f/s : temps disponible %.1f s, marge %.1f, vitesse au bord du volume d'exclusion %.0f blocs/s", k, T, T / (load / 1000), MASSLOCK_VMIN + k * (RADIUS_PLANET + M)));
            }
            logTo("C2", "FIN C2 : voir rapport (client non mesuré)");
        }
    }
}
