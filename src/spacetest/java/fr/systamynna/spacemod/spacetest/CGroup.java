package fr.systamynna.spacemod.spacetest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;

/** Phases C1 (hôte temporaire : deux Minis) et C6 (charge multijoueur simulée) ; voir CRunner pour le lancement (-Pc=1,6). */
final class CGroup {
    static final ResourceLocation C1T = id("c1t"), C1G = id("c1g"), C6T = id("c6t"), C6H1 = id("c6h1"), C6H2 = id("c6h2"), C1P = id("c1p");
    /** Vitesse relative maximale imposée par le mass lock entre deux Minis (valeur de test, bloc/s). */
    static final double VREL_CAP = 50;

    private CGroup() {}

    private static ResourceLocation id(String p) { return ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, p); }

    static void registerPhysics() {
        for (ResourceLocation r : List.of(C1T, C1G, C1P, C6T, C6H1, C6H2)) {
            DimensionPhysicsData.putPhysics(A1Runner.levelKey(r), new DimensionPhysics(r, 0,
                Optional.of(0f), Optional.of(new org.joml.Vector3f(0, 0, 0)), Optional.of(0.0), Optional.empty(), Optional.empty(), false));
        }
    }

    static void place(ServerSubLevel s, Vector3d p, Vector3d v) {
        SubLevelPhysicsSystem.get(s.getLevel()).getPipeline().teleport(s, p, new Quaterniond());
        CRunner.setVel(s, v);
    }

    static byte[] toBytes(ServerSubLevel s) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        NbtIo.writeCompressed(SubLevelSerializer.toData(s, List.of()).fullTag(), bo);
        return bo.toByteArray();
    }

    static ServerSubLevel fromBytes(ServerLevel l, byte[] b) throws IOException {
        CompoundTag tag = NbtIo.readCompressed(new java.io.ByteArrayInputStream(b), NbtAccounter.unlimitedHeap());
        ServerSubLevel s = SubLevelSerializer.fullyLoad(l, SubLevelSerializer.fromData(tag));
        if (s != null) SubLevelContainer.getContainer(l).addForceLoadTicket(s, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
        return s;
    }

    static void removeAll(ServerLevel l) {
        for (var s : new ArrayList<>(SubLevelContainer.getContainer(l).getAllSubLevels()))
            SubLevelContainer.getContainer(l).removeSubLevel(s, SubLevelRemovalReason.REMOVED);
    }

    // =============================================================================================
    // C1
    // =============================================================================================
    static final class C1 implements CRunner.Phase {
        static final double[] SPEEDS = System.getProperty("spacetest.c1.variant", "").isEmpty() ? new double[] { 5, 20, 50, 100, 200, 400 } : new double[] { 5, 50 };
        static final int PAIRS = 10, ROUNDS = 10;
        ServerLevel t, g, p;
        final StringBuilder trace = new StringBuilder();
        int stage, si, round, tick, wait = 10, fails;
        final List<ServerSubLevel> a = new ArrayList<>(), b = new ArrayList<>();
        final double[] minRel = new double[PAIRS];
        final boolean[] crossed = new boolean[PAIRS], deep = new boolean[PAIRS];
        int deepCount;
        int traversals, passes; double minSum;
        final Random rnd = new Random(7);
        final List<Double> tickMs = new ArrayList<>();
        // formation / fusion / scission
        int phase2Step;
        ServerSubLevel ma, mb, mc;
        byte[] cData;
        final List<Double> formMs = new ArrayList<>(), joinMs = new ArrayList<>(), splitMs = new ArrayList<>();
        double worstRelPoseErr, worstCapOver;
        int cycles, cyclesTarget = 20;
        int occBefore;
        long lastPoll;

        public String name() { return "C1"; }

        public void begin() {
            CRunner.resetLog("C1");
            t = DimDispatch.create(CRunner.server, C1T);
            g = DimDispatch.create(CRunner.server, C1G);
            p = DimDispatch.create(CRunner.server, C1P);
            String variant = System.getProperty("spacetest.c1.variant", "");
            int thick = variant.contains("thick") ? 3 : 1;
            for (int i = 0; i < PAIRS; i++) {
                a.add(A4Runner.assemble(p, new BlockPos(i * 40, 64, 0), 10, thick, 10, 0));    // plaque de 1 bloc d'épaisseur (3 pour la variante « thick »)
                b.add(A4Runner.assemble(p, new BlockPos(i * 40, 100, 0), 10, thick, 10, 0));
                if (variant.contains("wall")) {   // A fixée au monde : mur immobile
                    Vector3d pos = new Vector3d(a.get(i).logicalPose().position());
                    SubLevelPhysicsSystem.get(p).getPipeline().addConstraint(null, a.get(i), new dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration(pos, new Vector3d(a.get(i).logicalPose().rotationPoint()), new Quaterniond()));
                }
            }
            CRunner.logTo("C1", "variante : " + (variant.isEmpty() ? "(standard)" : variant));
            CRunner.logTo("C1", "DÉBUT C1 ; coque = plaque 10×1×10 (1 bloc d'épaisseur), Minis libres de masse égale, gravité nulle ; plafond de vitesse relative du mass lock entre Minis (valeur de test) = " + VREL_CAP + " bloc/s ; " + PAIRS * ROUNDS + " passages par vitesse");
        }

        public boolean tick() {
            if (wait-- > 0) return false;
            if (stage == 0) return tunnelling();
            return grouping();
        }

        // --- 100 passages par vitesse ---
        boolean tunnelling() {
            if (si >= SPEEDS.length) {
                if (!System.getProperty("spacetest.c1.variant", "").isEmpty()) return true;
                stage = 1;
                CRunner.logTo("C1", "--- formation de groupe, fusion et scission ---");
                return false;
            }
            double v = SPEEDS[si];
            int length = 12 + (int) Math.ceil(14.0 / v * 20) + 60;   // 3 s de plus pour laisser finir un enfoncement lent
            if (tick == 0) {
                for (int i = 0; i < PAIRS; i++) {
                    place(a.get(i), new Vector3d(i * 40, 64, 0), new Vector3d());
                    place(b.get(i), new Vector3d(i * 40 + (rnd.nextDouble() - 0.5) * 6, 72, (rnd.nextDouble() - 0.5) * 6), new Vector3d(0, -v, 0));
                    minRel[i] = 1e9; crossed[i] = false; deep[i] = false;
                }
            }
            if (tick > 0) {
                for (int i = 0; i < PAIRS; i++) {
                    double rel = b.get(i).logicalPose().position().y() - a.get(i).logicalPose().position().y();
                    minRel[i] = Math.min(minRel[i], rel);
                    if (i == 0 && round == 0 && si <= 1) trace.append(String.format("%.2f ", rel));
                    if (rel < -1) crossed[i] = true;   // le centre de B est passé de l'autre côté de la coque de A (épaisseur 1)
                    if (rel < 0) deep[i] = true;
                }
            }
            tickMs.add(CRunner.server.getAverageTickTimeNanos() / 1e6);
            tick++;
            if (tick > length) {
                if (trace.length() > 0) { CRunner.logTo("C1", "trace de la paire 0, vitesse " + v + " : écart vertical par tick = " + trace); trace.setLength(0); }
                for (int i = 0; i < PAIRS; i++) { passes++; if (crossed[i]) traversals++; if (deep[i]) deepCount++; minSum += minRel[i]; }
                tick = 0;
                if (++round >= ROUNDS) {
                    CRunner.logTo("C1", String.format("vitesse relative %.0f bloc/s (%s du plafond) : %d traversées complètes de coque (centre de B au-delà de la face opposée) sur %d passages ; %d enfoncements au-delà du centre de A ; écart vertical minimal moyen %.2f (contact = 1,0) ; tick moyen %.2f ms", v, v <= VREL_CAP ? "sous ou au niveau" : "AU-DESSUS", traversals, passes, deepCount, minSum / passes, tickMs.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
                    if (v <= VREL_CAP && traversals > 0) fails++;
                    round = 0; traversals = 0; passes = 0; deepCount = 0; minSum = 0; si++;
                }
            }
            return false;
        }

        // --- formation (deux Minis), ajout d'une troisième depuis les données, scission, répétés ---
        boolean grouping() {
            try {
                switch (phase2Step) {
                    case 0 -> {   // deux Minis dans la Tampon, avec position et vitesse relatives stratégiques
                        CRunner.removeAllSafe(g);
                        CRunner.removeAllSafe(t);
                        ma = A4Runner.assembleSmall(t, new BlockPos(0, 64, 0)); A4Runner.spawnStands(t, ma, 1);
                        mb = A4Runner.assembleSmall(t, new BlockPos(60, 64, 0)); A4Runner.spawnStands(t, mb, 1);
                        mc = A4Runner.assembleSmall(t, new BlockPos(0, 64, 200));
                        cData = toBytes(mc);
                        SubLevelContainer.getContainer(t).removeSubLevel(mc, SubLevelRemovalReason.REMOVED);
                        mc = null;
                        occBefore = CRunner.occupied(t) + CRunner.occupied(g);
                        phase2Step = 1; wait = 6;
                    }
                    case 1 -> {   // entrée dans le groupe : transfert des deux Minis puis reproduction de la pose relative et de la vitesse relative
                        Vector3d rel = new Vector3d(60, 0, 0), vRelWanted = new Vector3d(-(cycles % 2 == 0 ? 30 : 80), 0, 0);   // un cycle sur deux dépasse le plafond
                        Vector3d vRel = new Vector3d(vRelWanted);
                        if (vRel.length() > VREL_CAP) vRel.normalize(VREL_CAP);
                        worstCapOver = Math.max(worstCapOver, vRel.length() - VREL_CAP);
                        long t0 = System.nanoTime();
                        ServerSubLevel na = A9Runner.serializeReload(ma, t, g), nb = A9Runner.serializeReload(mb, t, g);
                        SubLevelPhysicsSystem.get(g).getPipeline().teleport(na, new Vector3d(1000, 64, 0), new Quaterniond());
                        SubLevelPhysicsSystem.get(g).getPipeline().teleport(nb, new Vector3d(1000, 64, 0).add(rel), new Quaterniond());
                        formMs.add((System.nanoTime() - t0) / 1e6);
                        ma = na; mb = nb; expectedRel = new Vector3d(rel); expectedV = new Vector3d(vRel);
                        phase2Step = 2; wait = 3;   // l'impulsion de vitesse donnée au tick de création est ignorée : donnée 3 ticks plus tard
                    }
                    case 2 -> {
                        CRunner.setVel(ma, new Vector3d());
                        CRunner.setVel(mb, new Vector3d(expectedV));
                        phase2Step = 3; wait = 5;
                    }
                    case 3 -> {   // contrôle de la pose relative et de la vitesse relative 5 ticks après (t = 5/20 s)
                        Vector3d pa = new Vector3d(ma.logicalPose().position()), pb = new Vector3d(mb.logicalPose().position());
                        Vector3d expected = new Vector3d(expectedRel).fma(5 / 20.0, expectedV);
                        double err = new Vector3d(pb).sub(pa).distance(expected);
                        worstRelPoseErr = Math.max(worstRelPoseErr, err);
                        Vector3d vr = CRunner.vel(mb).sub(CRunner.vel(ma));
                        if (vr.distance(expectedV) > 1e-3 * Math.max(1, expectedV.length())) { fails++; CRunner.logTo("C1", "ÉCHEC : vitesse relative " + A1Runner.fmt(vr) + " contre " + A1Runner.fmt(expectedV)); }
                        // une Mini en données rejoint le groupe : désérialisation
                        long t0 = System.nanoTime();
                        mc = fromBytes(g, cData);
                        joinMs.add((System.nanoTime() - t0) / 1e6);
                        if (mc == null) { fails++; CRunner.logTo("C1", "ÉCHEC : désérialisation de la troisième Mini refusée"); return true; }
                        SubLevelPhysicsSystem.get(g).getPipeline().teleport(mc, new Vector3d(1000, 64, 40), new Quaterniond());
                        phase2Step = 4; wait = 4;
                    }
                    case 4 -> {   // scission : une Mini quitte le groupe (retour dans la Tampon), une autre est sérialisée en données
                        long t0 = System.nanoTime();
                        cData = toBytes(mc);
                        SubLevelContainer.getContainer(g).removeSubLevel(mc, SubLevelRemovalReason.REMOVED);
                        ServerSubLevel back = A9Runner.serializeReload(ma, g, t);
                        splitMs.add((System.nanoTime() - t0) / 1e6);
                        // remettre le groupe à zéro pour le cycle suivant
                        SubLevelContainer.getContainer(g).removeSubLevel(mb, SubLevelRemovalReason.REMOVED);
                        mc = null; mb = null; ma = back;
                        int occ = CRunner.occupied(t) + CRunner.occupied(g);
                        if (occ != 1) { fails++; CRunner.logTo("C1", "ÉCHEC : plots occupés " + occ + " au lieu de 1 après la scission"); }
                        SubLevelContainer.getContainer(t).removeSubLevel(ma, SubLevelRemovalReason.REMOVED);
                        ma = null;
                        cycles++;
                        phase2Step = cycles >= cyclesTarget ? 5 : 0;
                        wait = 4;
                    }
                    default -> {
                        CRunner.logTo("C1", String.format("%d cycles formation → ajout depuis les données → scission : formation (2 transferts + poses) p50=%.2f ms p95=%.2f ms ; désérialisation d'une Mini en données p50=%.2f ms p95=%.2f ms ; scission p50=%.2f ms p95=%.2f ms", cycles,
                            CRunner.pct(formMs, 50), CRunner.pct(formMs, 95), CRunner.pct(joinMs, 50), CRunner.pct(joinMs, 95), CRunner.pct(splitMs, 50), CRunner.pct(splitMs, 95)));
                        CRunner.logTo("C1", String.format("erreur maximale de pose relative 5 ticks après la formation : %.4f bloc ; dépassement maximal du plafond de vitesse relative après écrêtage : %.2e bloc/s", worstRelPoseErr, worstCapOver));
                        CRunner.logTo("C1", "FIN C1 : " + (fails == 0 ? "RÉUSSI (partie automatique ; [MANUEL-CLIENT] et [MANUEL-VISUEL] non exécutés)" : "ÉCHEC/PARTIEL (" + fails + ")"));
                        return true;
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return false;
        }
        Vector3d expectedRel, expectedV;
    }

    // =============================================================================================
    // C6 : charge multijoueur simulée (sans client, sans réseau)
    // =============================================================================================
    static final class C6 implements CRunner.Phase {
        static final int[] COUNTS = { 2, 4, 8, 16 };
        static final int TICKS = 1200;
        ServerLevel t, h1, h2;
        int ci = -1, tick, wait = 10;
        boolean clearing;
        int failLogged;
        final List<ServerSubLevel> minis = new ArrayList<>();
        final List<ServerLevel> where = new ArrayList<>();
        final List<Integer> kind = new ArrayList<>();       // 0 = Tampon (rencontre), 1 = croiseur, 2 = planète
        final List<Boolean> inside = new ArrayList<>();
        final double M = 15, R1 = 300, R2 = 2000;
        final List<Double> tickMs = new ArrayList<>(), period = new ArrayList<>(), transferMs = new ArrayList<>();
        long lastNs;
        int transfers;
        final List<Object[]> pendingVel = new ArrayList<>();
        byte[] offline; int offlineIdx; double serMs, desMs; double maxTick;
        boolean forceDone;
        double memStart;

        public String name() { return "C6"; }

        public void begin() {
            CRunner.resetLog("C6");
            t = DimDispatch.create(CRunner.server, C6T);
            h1 = DimDispatch.create(CRunner.server, C6H1);
            h2 = DimBackend.select().create(CRunner.server, C6H2, CRunner.C2.flatGen(CRunner.server), null);
            for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) h2.setChunkForced(x, z, true);   // la planète garde son terrain chargé (rayon 8)
            CRunner.logTo("C6", "DÉBUT C6 ; SIMULATION CÔTÉ SERVEUR SEULEMENT (aucun client, aucune bande passante mesurée) : chaque « joueur » est une Mini S avec un armor stand ; deux hôtes (croiseur R=" + R1 + ", planète R=" + R2 + " avec terrain plat chargé sur 17×17 chunks) et la Tampon avec des rencontres ; les Minis des hôtes franchissent la frontière (amplitude 4M, période 60 ticks)");
            memStart = CRunner.usedMb();
        }

        void setup(int n) {
            minis.clear(); where.clear(); kind.clear(); inside.clear(); pendingVel.clear();
            transferMs.clear(); tickMs.clear(); period.clear(); transfers = 0; maxTick = 0; tick = 0; offline = null;
            // 1re passe : toutes les Minis dans la Tampon, pour que chacune ait un plot distinct ; 2e passe : déplacement vers les hôtes
            // (le plot est conservé par la sérialisation : un plot déjà pris dans la destination fait échouer le transfert, « Plot already exists »,
            // et un plot libéré est réattribué à la Mini suivante)
            for (int i = 0; i < n; i++) {
                ServerSubLevel s = A4Runner.assembleSmall(t, new BlockPos(i * 40, 64, 0));
                A4Runner.spawnStands(t, s, 1);
                minis.add(s); where.add(t); kind.add(i % 3); inside.add(i % 3 != 0);
            }
            for (int i = 0; i < n; i++) {
                int k = kind.get(i);
                ServerLevel l = k == 0 ? t : k == 1 ? h1 : h2;
                ServerSubLevel s = minis.get(i);
                if (l != t) { s = A9Runner.serializeReload(s, t, l); minis.set(i, s); where.set(i, l); }
                if (k == 0) place(s, new Vector3d((i / 3) * 30, 200, 0), new Vector3d(((i / 3) % 2 == 0 ? 1 : -1) * 15, 0, 0));
                else place(s, new Vector3d(i * 40, 64 + (k == 2 ? 200 : 0), 0), new Vector3d(1, 0, 0));
            }
            lastNs = 0;
        }

        public boolean tick() {
            if (wait-- > 0) return false;
            if (clearing) {
                clearing = false;
                if (ci < COUNTS.length) { setup(COUNTS[ci]); wait = 20; }
                return false;
            }
            if (ci < 0 || tick >= TICKS) {
                if (ci >= 0) report();
                ci++;
                if (ci >= COUNTS.length) {
                    CRunner.logTo("C6", String.format("FIN C6 : mémoire %.0f → %.0f Mo ; partie automatique seulement", memStart, CRunner.usedMb()));
                    return true;
                }
                removeAll(t); removeAll(h1); removeAll(h2);
                tick = 0; clearing = true;
                wait = 60;   // le retrait de Sable est différé : les plots ne sont libres que quelques ticks plus tard
                return false;
            }
            long now = System.nanoTime();
            if (lastNs != 0) period.add((now - lastNs) / 1e6);
            lastNs = now;
            if (tick % 20 == 0) { double ms = CRunner.server.getAverageTickTimeNanos() / 1e6; tickMs.add(ms); maxTick = Math.max(maxTick, ms); }
            for (var it = pendingVel.iterator(); it.hasNext();) {
                Object[] e = it.next();
                if (tick >= (int) e[2]) { CRunner.setVel((ServerSubLevel) e[0], (Vector3d) e[1]); it.remove(); }
            }
            // franchissements des Minis d'hôte
            for (int i = 0; i < minis.size(); i++) {
                int k = kind.get(i);
                if (k == 0) continue;
                double R = k == 1 ? R1 : R2;
                double d = R + 4 * M * Math.sin(2 * Math.PI * (tick + i * 7) / 60.0);
                boolean in = inside.get(i);
                ServerLevel hostL = k == 1 ? h1 : h2;
                if (in && d >= R + M) cross(i, hostL, t, false);
                else if (!in && d <= R - M) cross(i, t, hostL, true);
            }
            // déconnexion d'un joueur à mi-parcours : sérialisation en données (délai de test de 5 s), restauration à 20 s
            if (tick == 300 && minis.size() >= 2) {
                int i = minis.size() - 1;
                long t0 = System.nanoTime();
                try { offline = toBytes(minis.get(i)); } catch (IOException e) { throw new IllegalStateException(e); }
                SubLevelContainer.getContainer(where.get(i)).removeSubLevel(minis.get(i), SubLevelRemovalReason.REMOVED);
                serMs = (System.nanoTime() - t0) / 1e6; offlineIdx = i; minis.set(i, null);
            }
            if (tick == 900 && offline != null) {
                long t0 = System.nanoTime();
                try { minis.set(offlineIdx, fromBytes(where.get(offlineIdx), offline)); } catch (IOException e) { throw new IllegalStateException(e); }
                desMs = (System.nanoTime() - t0) / 1e6;
            }
            tick++;
            return false;
        }

        void cross(int i, ServerLevel from, ServerLevel to, boolean toHost) {
            ServerSubLevel s = minis.get(i);
            if (s == null) return;
            Vector3d v = CRunner.vel(s);
            long t0 = System.nanoTime();
            ServerSubLevel m = A9Runner.serializeReload(s, from, to);
            transferMs.add((System.nanoTime() - t0) / 1e6);
            if (m == null) {
                if (failLogged++ < 3) CRunner.logTo("C6", "diagnostic : transfert refusé, Mini " + i + " plot " + s.getPlot().plotPos + " de " + from.dimension().location().getPath() + " vers " + to.dimension().location().getPath()
                    + " ; plots occupés source=" + SubLevelContainer.getContainer(from).getOccupancy() + " destination=" + SubLevelContainer.getContainer(to).getOccupancy());
                return;
            }
            minis.set(i, m); where.set(i, to); inside.set(i, toHost); transfers++;
            pendingVel.add(new Object[] { m, v, tick + 2 });
        }

        void report() {
            int occ = CRunner.occupied(t) + CRunner.occupied(h1) + CRunner.occupied(h2);
            double avgPeriod = period.stream().mapToDouble(Double::doubleValue).average().orElse(50);
            double tps = Math.min(20, 1000 / avgPeriod);
            CRunner.logTo("C6", String.format("%2d joueurs simulés : TPS mesuré %.2f (période moyenne %.2f ms, max %.1f ms) ; temps de tick moyen du serveur %.2f ms (pic %.2f ms) ; %d transferts de frontière (p50 %.2f ms, p95 %.2f ms) ; plots occupés %d (attendu %d) ; sérialisation d'un joueur déconnecté %.2f ms, restauration %.2f ms",
                COUNTS[ci], tps, avgPeriod, period.stream().mapToDouble(Double::doubleValue).max().orElse(0), tickMs.stream().mapToDouble(Double::doubleValue).average().orElse(0), maxTick, transfers, CRunner.pct(transferMs, 50), CRunner.pct(transferMs, 95), occ, COUNTS[ci], serMs, desMs));
        }
    }
}
