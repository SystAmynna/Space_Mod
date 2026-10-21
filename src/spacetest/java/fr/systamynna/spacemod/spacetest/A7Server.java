package fr.systamynna.spacemod.spacetest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.joml.Vector3d;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Test A7 (manuel) : déconnexion d'un joueur seul dans une Mini en vol libre.
 * Au départ du joueur, un minuteur (délai de test : 30 s) puis sérialisation de la Mini dans
 * test-results/A7/mini.nbt (écriture atomique) ; à la reconnexion, la Mini est restaurée si besoin et le joueur
 * replacé à sa position relative. Arrêt du serveur pendant le délai : sérialisation immédiate.
 */
final class A7Server {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, "a7");
    private static final Path DIR = Path.of(System.getProperty("spacetest.results", "../test-results"), "A7");
    private static final Path MINI = DIR.resolve("mini.nbt");
    private static final Path STATE = DIR.resolve("joueur.txt");
    private static final int DELAY_TICKS = Integer.getInteger("spacetest.a7.delay", 30) * 20;

    private static UUID miniId;
    private static long deadline = -1;
    private static long now;
    private static boolean active = Files.exists(STATE); // un redémarrage reprend le suivi si un joueur s'est déconnecté avant

    private A7Server() {}

    static void registerPhysics() {
        DimensionPhysicsData.putPhysics(A1Runner.levelKey(ID), new DimensionPhysics(ID, 0,
            Optional.of(0f), Optional.of(new org.joml.Vector3f(0, 0, 0)), Optional.of(0.0), Optional.empty(), Optional.empty(), false));
    }

    static void register(net.neoforged.bus.api.IEventBus bus) {
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> { if (active && e.getEntity() instanceof ServerPlayer p) onLogout(p); });
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> { if (active && e.getEntity() instanceof ServerPlayer p) onLogin(p); });
        bus.addListener((ServerStoppingEvent e) -> { if (active && deadline >= 0) { log("arrêt du serveur pendant le délai : sérialisation immédiate"); serializeNow(e.getServer()); } });
    }

    /** /spacetest a7 setup : crée la Mini avec un repère, y place le joueur, active le suivi. */
    static String setup(ServerPlayer player) {
        ServerLevel level = DimDispatch.create(player.server, ID);
        List<BlockPos> blocks = new ArrayList<>();
        BlockPos o = new BlockPos(0, 64, 0);
        for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) {
            level.setBlock(o.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
            blocks.add(o.offset(x, 0, z));
        }
        level.setBlock(o.offset(7, 1, 7), Blocks.GOLD_BLOCK.defaultBlockState(), 3); // repère visuel
        blocks.add(o.offset(7, 1, 7));
        ServerSubLevel sub = SubLevelAssemblyHelper.assembleBlocks(level, o, blocks, BoundingBox3i.from(blocks));
        SubLevelContainer.getContainer(level).addForceLoadTicket(sub, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
        miniId = sub.getUniqueId();
        active = true;
        Vector3d p = new Vector3d(sub.logicalPose().position());
        player.teleportTo(level, p.x, p.y + 2, p.z, 0f, 0f);
        log("setup : Mini " + miniId + " pose=" + A1Runner.fmt(p) + " ; délai=" + DELAY_TICKS / 20 + " s");
        return "Mini prête (repère : bloc d'or). Marche vers le bloc d'or, puis déconnecte-toi quand on te le demande.";
    }

    static void tick(MinecraftServer server) {
        now++;
        if (deadline >= 0 && now >= deadline) {
            log("délai écoulé : sérialisation");
            serializeNow(server);
        }
    }

    private static void onLogout(ServerPlayer p) {
        if (!p.level().dimension().equals(A1Runner.levelKey(ID))) return;
        ServerLevel level = (ServerLevel) p.level();
        String rel = String.format(java.util.Locale.ROOT, "%.4f|%.4f|%.4f", p.getX(), p.getY(), p.getZ());
        boolean tracked = Sable.HELPER.getTrackingSubLevel(p) != null;
        try {
            Files.createDirectories(DIR);
            Files.writeString(STATE, rel + "|" + tracked + "|" + miniId + "\n");
        } catch (IOException e) {
            log("écriture de l'état du joueur impossible : " + e);
        }
        log("DÉCONNEXION de " + p.getGameProfile().getName() + " position brute=" + rel + " suivi par un sub-level=" + tracked
            + " ; Mini présente=" + (SubLevelContainer.getContainer(level).getSubLevel(miniId) != null));
        deadline = now + DELAY_TICKS;
    }

    private static void onLogin(ServerPlayer p) {
        // un état A7 d'une session précédente ne doit pas gêner une autre connexion (autre dimension, monde neuf)
        if (!p.level().dimension().equals(A1Runner.levelKey(ID))) return;
        ServerLevel level = p.server.getLevel(A1Runner.levelKey(ID));
        if (level == null || SubLevelContainer.getContainer(level) == null) {
            log("connexion ignorée : dimension a7 ou conteneur Sable indisponible");
            return;
        }
        if (miniId == null) {
            try {
                miniId = UUID.fromString(Files.readString(STATE).trim().split("\\|")[4]);
            } catch (IOException | RuntimeException e) {
                log("identifiant de la Mini illisible : " + e);
                return;
            }
        }
        boolean cancelled = deadline >= 0;
        deadline = -1;
        boolean present = SubLevelContainer.getContainer(level).getSubLevel(miniId) != null;
        log("CONNEXION de " + p.getGameProfile().getName() + " : minuteur en cours annulé=" + cancelled + ", Mini en sub-level=" + present
            + ", position à la connexion=" + A1Runner.fmt(new Vector3d(p.getX(), p.getY(), p.getZ())));
        if (!present) {
            if (!Files.exists(MINI)) {
                log("ÉCHEC : ni sub-level ni données sur disque : Mini perdue");
                return;
            }
            restore(level);
        }
        try {
            String[] s = Files.readString(STATE).trim().split("\\|");
            double x = Double.parseDouble(s[0]), y = Double.parseDouble(s[1]), z = Double.parseDouble(s[2]);
            p.teleportTo(level, x, y, z, p.getYRot(), p.getXRot());
            log("joueur replacé à la position enregistrée " + A1Runner.fmt(new Vector3d(x, y, z)));
        } catch (IOException | RuntimeException e) {
            log("replacement du joueur impossible : " + e);
        }
    }

    private static void serializeNow(MinecraftServer server) {
        deadline = -1;
        ServerLevel level = server.getLevel(A1Runner.levelKey(ID));
        ServerSubLevel sub = level == null ? null : (ServerSubLevel) SubLevelContainer.getContainer(level).getSubLevel(miniId);
        if (sub == null) {
            log("Mini introuvable pour la sérialisation");
            return;
        }
        try {
            Files.createDirectories(DIR);
            Path tmp = DIR.resolve("mini.nbt.tmp");
            try (var out = Files.newOutputStream(tmp)) {
                NbtIo.writeCompressed(SubLevelSerializer.toData(sub, List.of()).fullTag(), out);
            }
            Files.move(tmp, MINI, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            SubLevelContainer.getContainer(level).removeSubLevel(sub, SubLevelRemovalReason.REMOVED);
            log("Mini sérialisée dans " + MINI + " puis retirée du conteneur");
        } catch (IOException e) {
            log("ÉCHEC de sérialisation : " + e);
        }
    }

    private static void restore(ServerLevel level) {
        try (var in = Files.newInputStream(MINI)) {
            CompoundTag tag = NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            SubLevelData data = SubLevelSerializer.fromData(tag);
            ServerSubLevel s = SubLevelSerializer.fullyLoad(level, data);
            if (s == null) {
                log("ÉCHEC : fullyLoad a renvoyé null (plot pris)");
                return;
            }
            SubLevelContainer.getContainer(level).addForceLoadTicket(s, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
            log("Mini restaurée depuis les données : " + s.getUniqueId() + " pose=" + A1Runner.fmt(new Vector3d(s.logicalPose().position())));
        } catch (IOException e) {
            log("ÉCHEC de restauration : " + e);
        }
    }

    private static void log(String line) {
        SpaceTestMod.LOGGER.info("[A7] {}", line);
        try {
            Files.createDirectories(DIR);
            Files.writeString(DIR.resolve("a7.log"), java.time.LocalTime.now() + " " + line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            SpaceTestMod.LOGGER.error("[A7] log", e);
        }
    }
}
