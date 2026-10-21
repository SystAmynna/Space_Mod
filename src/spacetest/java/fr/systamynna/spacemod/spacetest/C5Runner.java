package fr.systamynna.spacemod.spacetest;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.joml.Vector3d;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;

/**
 * Test C5 : arrêt propre et plantage. -Pc5=setup crée 4 Minis occupées (sub-levels en mouvement dans la Tampon Spatiale) et
 * 3 Minis en données (fichiers écrits de façon atomique), avec une sauvegarde automatique toutes les 200 ticks ; l'action
 * (-Pc5action) arrête le serveur (stop), attend un plantage externe (kill) ou bloque en pleine écriture (killser).
 * -Pc5=check redémarre et compare à la vérité terrain journalisée. Écrit test-results/C5.
 */
final class C5Runner {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, "c5");
    private static final Path DIR = Path.of(System.getProperty("spacetest.results", "../test-results"), "C5");
    private static final Path DATA = DIR.resolve("minis");
    private static final Path INDEX = DIR.resolve("index.txt");
    private static final Path TRUTH = DIR.resolve("truth.txt");
    private static final int AUTOSAVE = 200;
    private static final int OCCUPIED = 4, DATAONLY = 3;

    private static MinecraftServer server;
    private static ServerLevel level;
    private static String mode, action, label;
    private static final List<UUID> occupied = new ArrayList<>(), dataOnly = new ArrayList<>();
    private static int tick, lastSaveTick = -1, killAt = -1;
    private static boolean done;

    private C5Runner() {}

    static void registerPhysics() {
        DimensionPhysicsData.putPhysics(A1Runner.levelKey(ID), new DimensionPhysics(ID, 0,
            Optional.of(0f), Optional.of(new org.joml.Vector3f(0, 0, 0)), Optional.of(0.0), Optional.empty(), Optional.empty(), false));
    }

    static void start(MinecraftServer s, String m, String a) {
        server = s; mode = m; action = a.isEmpty() ? "stop" : a;
        label = mode.equals("setup") ? "setup-" + action : "check";
        try {
            Files.createDirectories(DATA);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        level = DimDispatch.create(s, ID);
        if (mode.equals("setup")) setup();
    }

    private static void setup() {
        try {
            for (Path p : Files.list(DATA).toList()) Files.delete(p);
            Files.deleteIfExists(INDEX); Files.deleteIfExists(TRUTH);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        for (int i = 0; i < OCCUPIED; i++) {
            ServerSubLevel sub = A4Runner.assembleSmall(level, new BlockPos(i * 40, 64, 0));
            A4Runner.spawnStands(level, sub, 1);
            CRunner.teleport(sub, new Vector3d(i * 40, 64, 0));
            CRunner.setVel(sub, new Vector3d(2, 0, 0)); // appliqué avec retard (impulsion ignorée au tick de création) : refait au tick 5
            occupied.add(sub.getUniqueId());
        }
        for (int i = 0; i < DATAONLY; i++) {
            ServerSubLevel sub = A4Runner.assembleSmall(level, new BlockPos(i * 40, 64, 100));
            UUID id = sub.getUniqueId();
            dataOnly.add(id);
            writeData(id, SubLevelSerializer.toData(sub, List.of()).fullTag(), false);
            SubLevelContainer.getContainer(level).removeSubLevel(sub, SubLevelRemovalReason.REMOVED);
        }
        log("DÉBUT " + label + " : " + OCCUPIED + " Minis occupées " + occupied + ", " + DATAONLY + " Minis en données " + dataOnly + ", intervalle de sauvegarde " + AUTOSAVE + " ticks");
    }

    static void tick() {
        if (server == null || done) return;
        tick++;
        if (!mode.equals("setup")) {
            if (tick == 100) { done = true; check(); }   // laisse 100 ticks aux entités du plot pour se charger avant de les compter
            return;
        }
        if (tick == 5) for (UUID id : occupied) CRunner.setVel((ServerSubLevel) SubLevelContainer.getContainer(level).getSubLevel(id), new Vector3d(2, 0, 0));
        if (tick % 10 == 0) truth();
        if (tick % AUTOSAVE == 0) autosave();
        if (lastSaveTick > 0 && tick == lastSaveTick + 100) {
            switch (action) {
                case "stop" -> { log("arrêt propre demandé au tick " + tick + " (100 ticks après la sauvegarde automatique du tick " + lastSaveTick + ")"); done = true; server.halt(false); }
                case "kill" -> { truth(); mark("READY"); log("prêt pour le plantage au tick " + tick + " ; attente du kill -9 externe"); done = true; }
                case "killser" -> midWrite();
                default -> { }
            }
        }
    }

    private static void autosave() {
        server.saveEverything(true, true, true);
        StringBuilder sb = new StringBuilder("tick=" + tick + "\n");
        for (UUID id : occupied) {
            ServerSubLevel s = (ServerSubLevel) SubLevelContainer.getContainer(level).getSubLevel(id);
            Vector3d p = new Vector3d(s.logicalPose().position());
            sb.append("occupied|").append(id).append('|').append(p.x).append('|').append(p.y).append('|').append(p.z).append('\n');
        }
        for (UUID id : dataOnly) sb.append("data|").append(id).append('\n');
        try {
            Path tmp = DIR.resolve("index.tmp");
            Files.writeString(tmp, sb.toString());
            Files.move(tmp, INDEX, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        lastSaveTick = tick;
        truth();
        log("sauvegarde automatique au tick " + tick);
    }

    /** Vérité terrain : positions réelles, journalisées en continu (le plantage n'efface pas ce qui est déjà écrit). */
    private static void truth() {
        StringBuilder sb = new StringBuilder();
        for (UUID id : occupied) {
            ServerSubLevel s = (ServerSubLevel) SubLevelContainer.getContainer(level).getSubLevel(id);
            if (s == null) continue;
            Vector3d p = new Vector3d(s.logicalPose().position());
            sb.append(tick).append('|').append(id).append('|').append(p.x).append('|').append(p.y).append('|').append(p.z).append('\n');
        }
        try {
            Files.writeString(TRUTH, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.SYNC);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writeData(UUID id, CompoundTag tag, boolean blockHalfway) {
        try {
            Path tmp = DATA.resolve(id + ".nbt.tmp"), fin = DATA.resolve(id + ".nbt");
            if (blockHalfway) {
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                NbtIo.writeCompressed(tag, bo);
                byte[] all = bo.toByteArray();
                try (OutputStream out = Files.newOutputStream(tmp)) {
                    out.write(all, 0, all.length / 2);
                    out.flush();
                }
                log("écriture de " + id + " interrompue à mi-fichier (" + all.length / 2 + "/" + all.length + " octets dans le .tmp)");
                mark("READY");
                try { Thread.sleep(120_000); } catch (InterruptedException ignored) { }
                return;
            }
            try (OutputStream out = Files.newOutputStream(tmp)) {
                NbtIo.writeCompressed(tag, out);
            }
            Files.move(tmp, fin, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Plantage pendant la sérialisation : réécrit une Mini en données et bloque à mi-écriture du fichier temporaire. */
    private static void midWrite() {
        UUID id = occupied.get(0);
        ServerSubLevel s = (ServerSubLevel) SubLevelContainer.getContainer(level).getSubLevel(id);
        done = true;
        truth();
        writeData(id, SubLevelSerializer.toData(s, List.of()).fullTag(), true);
    }

    private static void mark(String name) {
        try {
            Files.writeString(DIR.resolve(name), "x");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // --- vérification après redémarrage ----------------------------------------------------------

    private static void check() {
        int fails = 0;
        try {
            List<String> index = Files.readAllLines(INDEX);
            int idxTick = Integer.parseInt(index.get(0).substring(5));
            Map<UUID, double[]> saved = new HashMap<>();
            List<UUID> dataIds = new ArrayList<>();
            for (String l : index.subList(1, index.size())) {
                String[] f = l.split("\\|");
                if (f[0].equals("occupied")) saved.put(UUID.fromString(f[1]), new double[] { Double.parseDouble(f[2]), Double.parseDouble(f[3]), Double.parseDouble(f[4]) });
                else dataIds.add(UUID.fromString(f[1]));
            }
            Map<UUID, double[]> truth = new HashMap<>(); Map<UUID, Integer> truthTick = new HashMap<>();
            for (String l : Files.readAllLines(TRUTH)) {
                String[] f = l.split("\\|");
                UUID id = UUID.fromString(f[1]);
                truth.put(id, new double[] { Double.parseDouble(f[2]), Double.parseDouble(f[3]), Double.parseDouble(f[4]) });
                truthTick.put(id, Integer.parseInt(f[0]));
            }
            log("DÉBUT check (après " + readLabel() + ") : index du tick " + idxTick + ", dernière vérité terrain au tick " + truthTick.values().stream().mapToInt(i -> i).max().orElse(-1));
            var all = SubLevelContainer.getContainer(level).getAllSubLevels();
            List<UUID> found = new ArrayList<>();
            for (var s : all) found.add(s.getUniqueId());
            int dup = found.size() - new java.util.HashSet<>(found).size();
            log("sub-levels présents dans la Tampon : " + found.size() + " (attendu " + saved.size() + ") ; doublons=" + dup + " ; plots occupés=" + CRunner.occupied(level));
            if (dup > 0) fails++;
            for (var e : saved.entrySet()) {
                ServerSubLevel s = (ServerSubLevel) SubLevelContainer.getContainer(level).getSubLevel(e.getKey());
                if (s == null) { fails++; log("ÉCHEC : Mini occupée " + e.getKey() + " ABSENTE après redémarrage"); continue; }
                Vector3d p = new Vector3d(s.logicalPose().position());
                double[] t = truth.get(e.getKey()), sv = e.getValue();
                double lossVsTruth = p.distance(t[0], t[1], t[2]);
                double vsSave = p.distance(sv[0], sv[1], sv[2]);
                log(String.format("Mini occupée %s : restaurée à %s ; vérité terrain (tick %d) %.2f,%.2f,%.2f -> perte %.3f bloc ; écart à la dernière sauvegarde (tick %d) : %.3f bloc ; vitesse restaurée %s ; passager=%d",
                    e.getKey(), A1Runner.fmt(p), truthTick.get(e.getKey()), t[0], t[1], t[2], lossVsTruth, idxTick, vsSave, A1Runner.fmt(CRunner.vel(s)), CRunner.standsOf(s)));
            }
            for (UUID id : found) if (!saved.containsKey(id)) { fails++; log("ÉCHEC : sub-level inattendu (orphelin ou doublon d'une Mini en données) " + id); }
            int tmp = 0, files = 0;
            for (Path p : Files.list(DATA).toList()) {
                String n = p.getFileName().toString();
                if (n.endsWith(".tmp")) { tmp++; log("fichier temporaire résiduel (ignoré par le chargement) : " + n + " (" + Files.size(p) + " octets)"); }
                else files++;
            }
            log("fichiers de Minis en données : " + files + " (attendu " + dataIds.size() + ") ; temporaires résiduels : " + tmp);
            if (files != dataIds.size()) fails++;
            for (UUID id : dataIds) {
                Path f = DATA.resolve(id + ".nbt");
                try (var in = Files.newInputStream(f)) {
                    CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
                    ServerSubLevel s = SubLevelSerializer.fullyLoad(level, SubLevelSerializer.fromData(tag));
                    if (s == null) { fails++; log("ÉCHEC : désérialisation de " + id + " impossible"); continue; }
                    log("Mini en données " + id + " : désérialisée (lisible, non corrompue) ; identifiant conservé=" + s.getUniqueId().equals(id));
                    SubLevelContainer.getContainer(level).removeSubLevel(s, SubLevelRemovalReason.REMOVED);
                } catch (Throwable t) {
                    fails++;
                    log("ÉCHEC : fichier " + id + " corrompu : " + t);
                }
            }
            log("plots occupés après les désérialisations de contrôle : " + CRunner.occupied(level) + " (attendu " + saved.size() + ")");
            if (CRunner.occupied(level) != saved.size()) { fails++; log("ÉCHEC : plot occupé inutilement"); }
        } catch (Throwable t) {
            fails++;
            log("ÉCHEC : exception " + t);
            SpaceTestMod.LOGGER.error("C5", t);
        }
        log("FIN check : " + (fails == 0 ? "RÉUSSI" : "ÉCHEC (" + fails + ")"));
        server.halt(false);
    }

    private static String readLabel() {
        try {
            return Files.readString(DIR.resolve("last-action.txt")).trim();
        } catch (IOException e) {
            return "?";
        }
    }

    private static void log(String line) {
        SpaceTestMod.LOGGER.info("[C5] {}", line);
        try {
            Files.writeString(DIR.resolve("c5-run.log"), "[" + label + "] " + line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            SpaceTestMod.LOGGER.error("[C5] log", e);
        }
    }
}
