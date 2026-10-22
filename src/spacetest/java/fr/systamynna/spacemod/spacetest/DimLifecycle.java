package fr.systamynna.spacemod.spacetest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import fr.systamynna.dynamicdimensions.api.event.PlayerLoginDimensionEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Tests D : cycle de vie des dimensions DynamicDimensions (chargement à la demande, déchargement automatique, dimensions
 * temporaires). Règles vérifiées : une dimension contenant un joueur n'est jamais déchargée ; un joueur qui se connecte dans une
 * dimension déchargée la fait charger. Commandes /spacetest dd ..., journal test-results/D1/dd-run.log, état run-dev/ddtest.
 */
final class DimLifecycle {
    static final ResourceLocation A = ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, "dd_a");
    static final ResourceLocation B = ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, "dd_b");
    private static final Path LOG = Path.of(System.getProperty("spacetest.results", "../test-results"), "D1", "dd-run.log");
    private static final Path STATE = Path.of("ddtest", "managed.txt");
    private static final int RESERVE_TICKS = 600;

    /** Dimensions gérées par les tests : id -> persist | temp. */
    private static final Map<ResourceLocation, String> MANAGED = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Long> RESERVED = new HashMap<>();
    private static final Map<ResourceLocation, Integer> IDLE = new HashMap<>();
    private static final DdBackend BACKEND = new DdBackend();
    private static long now;
    private static int autoSeconds = 30;

    private DimLifecycle() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(DimLifecycle::onLoginDimension);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer p)) return;
            ResourceLocation d = p.serverLevel().dimension().location();
            RESERVED.remove(d);
            log("LOGIN-OK " + p.getGameProfile().getName() + " dans " + d + " pos=" + p.blockPosition() + " niveaux=" + loadedList(p.server));
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) ->
            log("CHANGE-DIM " + e.getEntity().getGameProfile().getName() + " " + e.getFrom().location() + " -> " + e.getTo().location()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) ->
            log("LOGOUT " + e.getEntity().getGameProfile().getName() + " dans " + e.getEntity().level().dimension().location()));
    }

    static void onServerStarted(MinecraftServer server) {
        MANAGED.clear();
        try {
            if (Files.exists(STATE)) {
                for (String line : Files.readAllLines(STATE)) {
                    String[] f = line.split("\\|");
                    if (f.length == 2) MANAGED.put(ResourceLocation.parse(f[0]), f[1]);
                }
            }
        } catch (IOException e) {
            log("ÉTAT illisible : " + e);
        }
        log("DÉMARRAGE gérées=" + MANAGED + " niveaux chargés=" + loadedList(server));
        if ("autotest".equals(System.getProperty("spacetest.dd"))) autotest(server);
    }

    // --- règle 2 : un joueur qui se connecte dans une dimension déchargée la fait charger ---------------------------------

    private static void onLoginDimension(PlayerLoginDimensionEvent e) {
        e.getSavedDimension().ifPresent(key -> {
            ResourceLocation id = key.location();
            if (!MANAGED.containsKey(id)) return;
            RESERVED.put(id, now + RESERVE_TICKS);
            if (e.getServer().getLevel(key) != null) return;
            if (MANAGED.get(id).equals("temp")) {
                log("LOGIN-PRELOAD " + e.getProfile().getName() + " : " + id + " est temporaire et a été supprimée -> repli Overworld attendu");
                MANAGED.remove(id);
                save();
                return;
            }
            ServerLevel l = loadManaged(e.getServer(), id);
            log("LOGIN-PRELOAD " + e.getProfile().getName() + " : " + id + " chargée=" + (l != null));
        });
    }

    // --- règle 1 : jamais de déchargement avec un joueur dedans ----------------------------------------------------------

    static boolean occupiedOrReserved(MinecraftServer server, ResourceLocation id) {
        ServerLevel l = server.getLevel(Registries.key(id));
        return (l != null && !l.players().isEmpty()) || RESERVED.getOrDefault(id, 0L) > now;
    }

    static String safeUnload(MinecraftServer server, ResourceLocation id) {
        if (server.getLevel(Registries.key(id)) == null) return "déjà déchargée";
        if (occupiedOrReserved(server, id)) {
            log("UNLOAD REFUSÉ (joueur présent ou attendu) " + id);
            return "REFUSÉ : joueur présent ou en cours de connexion";
        }
        boolean ok = BACKEND.unload(server, id);
        log("UNLOAD " + (ok ? "OK" : "REFUSÉ par la bibliothèque") + " " + id);
        return ok ? "OK (déchargement au prochain tick)" : "REFUSÉ par la bibliothèque";
    }

    static String safeDelete(MinecraftServer server, ResourceLocation id) {
        if (occupiedOrReserved(server, id)) {
            log("DELETE REFUSÉ (joueur présent ou attendu) " + id);
            return "REFUSÉ : joueur présent ou en cours de connexion";
        }
        boolean ok = BACKEND.delete(server, id);
        log("DELETE " + (ok ? "OK" : "REFUSÉ par la bibliothèque") + " " + id);
        if (ok) {
            MANAGED.remove(id);
            save();
        }
        return ok ? "OK" : "REFUSÉ par la bibliothèque (inexistante ou occupée)";
    }

    // --- création, chargement ------------------------------------------------------------------------------------------

    private static ServerLevel loadManaged(MinecraftServer server, ResourceLocation id) {
        ServerLevel l = BACKEND.load(server, id, A1Runner.voidGenerator(server), A1Runner.dimensionType(0f));
        log("LOAD " + id + " -> " + (l != null));
        return l;
    }

    static ServerLevel create(MinecraftServer server, ResourceLocation id, String kind) {
        ServerLevel l = BACKEND.create(server, id, A1Runner.voidGenerator(server), A1Runner.dimensionType(0f));
        if (l == null) {
            log("CREATE " + id + " impossible");
            return null;
        }
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) l.setBlock(new BlockPos(x, 63, z), Blocks.STONE.defaultBlockState(), 3);
        if (kind.equals("persist")) A1Runner.assembleMini(l, new BlockPos(20, 64, 20));
        MANAGED.put(id, kind);
        save();
        log("CREATE " + id + " (" + kind + ")");
        return l;
    }

    private static ServerLevel ensureLoaded(MinecraftServer server, ResourceLocation id) {
        ServerLevel l = server.getLevel(Registries.key(id));
        if (l != null) return l;
        if (!MANAGED.containsKey(id)) return null;
        return loadManaged(server, id);
    }

    // --- commandes -----------------------------------------------------------------------------------------------------

    static String setup(ServerPlayer player) {
        MinecraftServer server = player.server;
        for (ResourceLocation id : List.of(A, B)) {
            if (server.getLevel(Registries.key(id)) == null) {
                if (MANAGED.containsKey(id)) ensureLoaded(server, id);
                else create(server, id, "persist");
            }
        }
        return go(player, "a");
    }

    static String go(ServerPlayer player, String target) {
        MinecraftServer server = player.server;
        ServerLevel dest;
        BlockPos pos;
        switch (target) {
            case "overworld" -> { dest = server.overworld(); pos = dest.getSharedSpawnPos(); }
            case "a" -> { dest = ensureLoaded(server, A); pos = new BlockPos(0, 64, 0); }
            case "b" -> { dest = ensureLoaded(server, B); pos = new BlockPos(0, 64, 0); }
            default -> { dest = ensureLoaded(server, idOf(target)); pos = new BlockPos(0, 64, 0); }
        }
        if (dest == null) return "dimension inconnue ou non gérée : " + target + " (voir /spacetest dd status)";
        player.teleportTo(dest, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        log("GO " + player.getGameProfile().getName() + " -> " + dest.dimension().location());
        return "téléporté dans " + dest.dimension().location();
    }

    static String unload(MinecraftServer server, String target) {
        return safeUnload(server, idOf(target));
    }

    static String delete(MinecraftServer server, String target) {
        return safeDelete(server, idOf(target));
    }

    static String load(MinecraftServer server, String target) {
        ResourceLocation id = idOf(target);
        return ensureLoaded(server, id) != null ? "chargée : " + id : "non gérée : " + id;
    }

    static String temp(ServerPlayer player) {
        MinecraftServer server = player.server;
        int n = 1;
        while (MANAGED.containsKey(idOf("tmp_" + n))) n++;
        ResourceLocation id = idOf("tmp_" + n);
        if (create(server, id, "temp") == null) return "création impossible";
        go(player, id.getPath());
        return "dimension temporaire " + id + " créée, tu y es ; supprimée " + autoSeconds + " s après être restée vide (/spacetest dd auto)";
    }

    static String auto(int seconds) {
        autoSeconds = seconds;
        log("AUTO " + (seconds <= 0 ? "désactivé" : seconds + " s"));
        return seconds <= 0 ? "déchargement automatique désactivé" : "déchargement automatique après " + seconds + " s de vide";
    }

    static String reset(MinecraftServer server) {
        StringBuilder sb = new StringBuilder();
        for (ResourceLocation id : new ArrayList<>(MANAGED.keySet())) {
            if (server.getLevel(Registries.key(id)) == null) {
                MANAGED.remove(id);
                continue;
            }
            sb.append(id.getPath()).append(" : ").append(safeDelete(server, id)).append("; ");
        }
        save();
        return sb.length() == 0 ? "rien à supprimer (les dimensions déchargées restent sur le disque : supprime run-dev-server/world pour tout effacer)" : sb.toString();
    }

    static String status(MinecraftServer server) {
        StringBuilder sb = new StringBuilder("niveaux chargés : " + loadedList(server) + " | auto=" + (autoSeconds <= 0 ? "off" : autoSeconds + " s") + '\n');
        for (Map.Entry<ResourceLocation, String> e : MANAGED.entrySet()) {
            ServerLevel l = server.getLevel(Registries.key(e.getKey()));
            sb.append(e.getKey().getPath()).append(" [").append(e.getValue()).append("] ")
                .append(l == null ? "DÉCHARGÉE" : "chargée, joueurs=" + l.players().size() + ", vide depuis " + IDLE.getOrDefault(e.getKey(), 0) / 20 + " s")
                .append(RESERVED.getOrDefault(e.getKey(), 0L) > now ? ", réservée" : "").append('\n');
        }
        log("STATUS " + sb.toString().replace('\n', ';'));
        return sb.toString();
    }

    private static ResourceLocation idOf(String target) {
        return switch (target) {
            case "a" -> A;
            case "b" -> B;
            default -> ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, target);
        };
    }

    // --- déchargeur automatique ----------------------------------------------------------------------------------------

    private static final List<Later> LATER = new ArrayList<>();

    private record Later(long tick, Runnable run) {}

    static void tick(MinecraftServer server) {
        now++;
        for (Later l : new ArrayList<>(LATER)) {
            if (l.tick() <= now) {
                LATER.remove(l);
                l.run().run();
            }
        }
        RESERVED.values().removeIf(t -> t <= now);
        if (now % 20 != 0) return;
        for (Map.Entry<ResourceLocation, String> e : new ArrayList<>(MANAGED.entrySet())) {
            ResourceLocation id = e.getKey();
            ServerLevel l = server.getLevel(Registries.key(id));
            if (l == null) {
                IDLE.remove(id);
                continue;
            }
            if (!l.players().isEmpty()) {
                IDLE.put(id, 0);
                continue;
            }
            int idle = IDLE.merge(id, 20, Integer::sum);
            if (autoSeconds > 0 && idle >= autoSeconds * 20 && !occupiedOrReserved(server, id)) {
                log("AUTO " + id + " vide depuis " + idle / 20 + " s");
                if (e.getValue().equals("temp")) safeDelete(server, id);
                else safeUnload(server, id);
                IDLE.remove(id);
            }
        }
    }

    // --- état et journal -----------------------------------------------------------------------------------------------

    private static void save() {
        try {
            Files.createDirectories(STATE.getParent());
            StringBuilder sb = new StringBuilder();
            MANAGED.forEach((k, v) -> sb.append(k).append('|').append(v).append('\n'));
            Files.writeString(STATE, sb.toString());
        } catch (IOException e) {
            SpaceTestMod.LOGGER.error("[D] état", e);
        }
    }

    private static String loadedList(MinecraftServer server) {
        List<String> l = new ArrayList<>();
        server.levelKeys().forEach(k -> l.add(k.location().toString()));
        return l.toString();
    }

    static void log(String line) {
        String text = "t=" + now + " " + line;
        SpaceTestMod.LOGGER.info("[D] {}", text);
        try {
            Files.createDirectories(LOG.getParent());
            Files.writeString(LOG, text + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            SpaceTestMod.LOGGER.error("[D] log", e);
        }
    }

    /** Clés de dimension à partir d'un identifiant. */
    private static final class Registries {
        static ResourceKey<Level> key(ResourceLocation id) {
            return A1Runner.levelKey(id);
        }
    }

    // --- auto-test sans client (-Pdd=autotest) -------------------------------------------------------------------------

    private static int failures;

    private static void check(boolean ok, String what) {
        if (!ok) failures++;
        log("AUTOTEST " + (ok ? "OK " : "ÉCHEC ") + what);
    }

    private static void autotest(MinecraftServer server) {
        ResourceLocation persist = idOf("t_persist");
        ResourceLocation temp = idOf("tmp_t");
        UUID fake = UUID.nameUUIDFromBytes("autotest".getBytes());
        GameProfile profile = new GameProfile(fake, "autotest");
        long t = 5;
        LATER.add(new Later(now + t, () -> {
            check(create(server, persist, "persist") != null, "création persistante");
            check(create(server, temp, "temp") != null, "création temporaire");
            try { // données de joueur factices dont la dimension est la persistante
                CompoundTag tag = new CompoundTag();
                tag.put("Dimension", StringTag.valueOf(persist.toString()));
                Path dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
                Files.createDirectories(dir);
                NbtIo.writeCompressed(tag, dir.resolve(fake + ".dat"));
            } catch (IOException e) {
                check(false, "écriture playerdata : " + e);
            }
            // règle 1 : un joueur dans la dimension -> refus, y compris côté bibliothèque
            ServerLevel l = server.getLevel(Registries.key(persist));
            ServerPlayer p = net.neoforged.neoforge.common.util.FakePlayerFactory.get(l, new GameProfile(UUID.randomUUID(), "autotest_in"));
            l.addNewPlayer(p);
            check(l.players().size() == 1, "joueur factice présent dans la dimension");
            check(BACKEND.unload(server, persist) == false, "bibliothèque : unload refusé avec joueur");
            check(BACKEND.delete(server, persist) == false, "bibliothèque : delete refusé avec joueur");
            check(safeUnload(server, persist).startsWith("REFUSÉ"), "safeUnload refusé avec joueur");
            l.removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            check(l.players().isEmpty(), "joueur factice retiré");
            check(safeUnload(server, persist).startsWith("OK"), "safeUnload accepté à vide");
        }));
        LATER.add(new Later(now + t + 10, () -> {
            check(server.getLevel(Registries.key(persist)) == null, "dimension déchargée");
            // règle 2 : le vrai chemin de login (mixin sur canPlayerLogin) recharge la dimension sauvegardée
            server.getPlayerList().canPlayerLogin(new InetSocketAddress("127.0.0.1", 1), profile);
            check(server.getLevel(Registries.key(persist)) != null, "login simulé : dimension rechargée par le mixin");
            check(RESERVED.containsKey(persist), "login simulé : dimension réservée");
            check(safeUnload(server, persist).startsWith("REFUSÉ"), "unload refusé tant que la connexion est attendue");
            RESERVED.clear();
            autoSeconds = 1;
        }));
        LATER.add(new Later(now + t + 100, () -> {
            check(server.getLevel(Registries.key(persist)) == null, "déchargement automatique de la persistante vide");
            check(server.getLevel(Registries.key(temp)) == null, "suppression automatique de la temporaire vide");
            Path dir = server.getWorldPath(LevelResource.ROOT).resolve("dimensions").resolve(SpaceTestMod.MODID).resolve("tmp_t");
            check(!Files.exists(dir), "fichiers de la temporaire supprimés");
            check(MANAGED.containsKey(persist) && !MANAGED.containsKey(temp), "registre : persistante gardée, temporaire retirée");
            log("AUTOTEST FIN : " + (failures == 0 ? "RÉUSSI" : "ÉCHEC (" + failures + ")"));
            server.halt(false);
        }));
    }
}
