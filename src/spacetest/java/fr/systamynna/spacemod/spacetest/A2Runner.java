package fr.systamynna.spacemod.spacetest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.joml.Vector3d;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Test A2 du plan : persistance d'une dimension et de son sub-level à travers un redémarrage.
 * Lancé par -Pa2=setup | setup-crash | verify (voir SpaceTestMod). `setup` écrit test-results/A2/state.txt
 * puis arrête le serveur (propre ou brutal) ; `verify` recrée les dimensions et compare.
 * La remise à zéro de la Tampon Spatiale est volontairement écartée : on observe seulement ce qui persiste.
 */
final class A2Runner {
    private static final ResourceLocation PERSIST = ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, "persist");
    private static final ResourceLocation TAMPON = ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, "tampon");
    private static final BlockPos MARK = new BlockPos(100, 64, 100);
    private static final Path DIR = Path.of(System.getProperty("spacetest.results", "../test-results"), "A2");
    private static final Path STATE = DIR.resolve("state.txt");
    private static final int SETTLE_TICKS = 100;

    private static String mode = "";
    private static int wait;
    private static boolean done = true;
    private static DimBackend backend;

    private A2Runner() {}

    /**
     * La physique de dimension n'est pas persistée, et Madnessverse restaure les dimensions enregistrées
     * avant ServerStartedEvent : à appeler dès ServerAboutToStartEvent, sinon la gravité par défaut s'applique.
     */
    static void registerPhysics() {
        for (ResourceLocation id : List.of(PERSIST, TAMPON)) {
            DimensionPhysicsData.putPhysics(A1Runner.levelKey(id), new DimensionPhysics(id, 0,
                Optional.of(0f), Optional.of(new org.joml.Vector3f(0, 0, 0)), Optional.of(0.0), Optional.empty(), Optional.empty(), false));
        }
    }

    static void start(MinecraftServer server, String m) {
        mode = m;
        backend = DimBackend.select();
        try {
            Files.createDirectories(DIR);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        log("DÉBUT A2 mode=" + m + " bibliothèque=" + backend.name());
        log("niveaux chargés avant (re)création : " + server.levelKeys().size());
        // verify : load (et non create, qui efface les données avec DynamicDimensions) ; identique à create avec Madnessverse
        boolean reload = m.equals("verify");
        ServerLevel persist = reload ? backend.load(server, PERSIST, A1Runner.voidGenerator(server), A1Runner.dimensionType(0f))
            : backend.create(server, PERSIST, A1Runner.voidGenerator(server), A1Runner.dimensionType(0f));
        ServerLevel tampon = reload ? backend.load(server, TAMPON, A1Runner.voidGenerator(server), A1Runner.dimensionType(0.001f))
            : backend.create(server, TAMPON, A1Runner.voidGenerator(server), A1Runner.dimensionType(0.001f));
        if (persist == null || tampon == null) {
            log("ÉCHEC : création impossible");
            server.halt(false);
            return;
        }
        if (m.startsWith("setup")) {
            persist.setBlock(MARK, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            A1Runner.assembleMini(persist);
            A1Runner.assembleMini(tampon);
        }
        log("gravité Sable au démarrage : persist=" + DimensionPhysicsData.getGravity(persist) + " tampon=" + DimensionPhysicsData.getGravity(tampon));
        if (m.equals("verify")) {
            A1Runner.assembleMini(persist, new BlockPos(300, 64, 300));
            log("sub-level témoin ajouté (diagnostic)");
        }
        wait = SETTLE_TICKS;
        done = false;
    }

    static void tick(MinecraftServer server) {
        if (done) return;
        if (!mode.startsWith("setup") && wait % 20 == 0) {
            ServerLevel l = server.getLevel(A1Runner.levelKey(PERSIST));
            List<ServerSubLevel> all = SubLevelContainer.getContainer(l).getAllSubLevels();
            StringBuilder ys = new StringBuilder();
            for (ServerSubLevel a : all) ys.append(String.format(" %.2f", a.logicalPose().position().y()));
            log("tick restant " + wait + " : " + all.size() + " sub-level(s), y =" + ys);
        }
        if (wait-- > 0) return;
        done = true;
        try {
            if (mode.startsWith("setup")) setupFinish(server);
            else verify(server);
        } catch (Throwable t) {
            log("ÉCHEC : exception " + t);
            SpaceTestMod.LOGGER.error("A2", t);
            server.halt(false);
        }
    }

    private static void setupFinish(MinecraftServer server) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (ResourceLocation id : List.of(PERSIST, TAMPON)) {
            ServerLevel l = server.getLevel(A1Runner.levelKey(id));
            ServerSubLevel s = onlySubLevel(l);
            sb.append(id.getPath()).append('|').append(s.getUniqueId()).append('|')
                .append(A1Runner.fmt(new Vector3d(s.logicalPose().position()))).append('|').append(fingerprint(l, s)).append('\n');
            log(id + " : sub-level " + s.getUniqueId() + " pose=" + A1Runner.fmt(new Vector3d(s.logicalPose().position())) + " " + fingerprint(l, s));
        }
        log("bloc repère or présent : " + server.getLevel(A1Runner.levelKey(PERSIST)).getBlockState(MARK).is(Blocks.GOLD_BLOCK));
        Files.writeString(STATE, sb.toString());
        if (mode.equals("setup-crash")) {
            server.saveEverything(true, true, true); // « sauvegarde automatique », puis arrêt brutal
            log("arrêt brutal (halt) après sauvegarde");
            Runtime.getRuntime().halt(137);
        } else {
            log("arrêt propre");
            server.halt(false);
        }
    }

    private static void verify(MinecraftServer server) throws IOException {
        log("gravité Sable à la vérification : persist=" + DimensionPhysicsData.getGravity(server.getLevel(A1Runner.levelKey(PERSIST))));
        List<String> lines = Files.readAllLines(STATE);
        int fails = 0;
        for (String line : lines) {
            String[] f = line.split("\\|");
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, f[0]);
            ServerLevel l = server.getLevel(A1Runner.levelKey(id));
            ServerSubLevelContainer c = SubLevelContainer.getContainer(l);
            ServerSubLevel s = c.getAllSubLevels().stream().filter(x -> x.getUniqueId().toString().equals(f[1])).findFirst().orElse(null);
            String now = s == null ? "aucun sub-level (" + c.getAllSubLevels().size() + ")" :
                s.getUniqueId() + "|" + A1Runner.fmt(new Vector3d(s.logicalPose().position())) + "|" + fingerprint(l, s);
            boolean same = s != null && now.equals(f[1] + "|" + f[2] + "|" + f[3]);
            log(id + " : avant=" + f[1] + "|" + f[2] + "|" + f[3] + "\n        après=" + now + " -> " + (same ? "IDENTIQUE" : "DIFFÉRENT"));
            if (id.equals(PERSIST) && !same) fails++;
            if (id.equals(TAMPON)) log("Tampon : " + (s == null ? "vide (réinitialisée)" : "conservée (acceptable, voir consigne)"));
        }
        boolean mark = server.getLevel(A1Runner.levelKey(PERSIST)).getBlockState(MARK).is(Blocks.GOLD_BLOCK);
        log("bloc repère or présent après redémarrage : " + mark);
        if (!mark) fails++;
        log("FIN A2 verify : " + (fails == 0 ? "RÉUSSI" : "ÉCHEC (" + fails + ")"));
        server.halt(false);
    }

    private static ServerSubLevel onlySubLevel(ServerLevel l) {
        List<ServerSubLevel> all = SubLevelContainer.getContainer(l).getAllSubLevels();
        if (all.size() != 1) throw new IllegalStateException(l.dimension().location() + " : " + all.size() + " sub-levels au lieu de 1");
        return all.get(0);
    }

    /** Blocs non vides, coffres et cobblestones dans le plot : empreinte du contenu. */
    static String fingerprint(ServerLevel l, ServerSubLevel s) {
        BoundingBox3ic b = s.getPlot().getBoundingBox();
        int blocks = 0, chests = 0, items = 0;
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int y = b.minY(); y <= b.maxY(); y++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (l.getBlockState(p).isAir()) continue;
                    blocks++;
                    BlockEntity be = l.getBlockEntity(p);
                    if (be instanceof Container ch) {
                        chests++;
                        for (int i = 0; i < ch.getContainerSize(); i++) {
                            ItemStack st = ch.getItem(i);
                            items += st.getCount();
                        }
                    }
                }
            }
        }
        return "blocs=" + blocks + ",coffres=" + chests + ",items=" + items;
    }

    private static void log(String line) {
        SpaceTestMod.LOGGER.info("[A2] {}", line);
        try {
            Files.writeString(DIR.resolve("a2-run.log"), line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            SpaceTestMod.LOGGER.error("[A2] log", e);
        }
    }
}
