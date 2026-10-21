package fr.systamynna.spacemod.spacetest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

import org.joml.Vector3d;

import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Test A8 (manuel) : changer un joueur de dimension sans écran de chargement et sans Immersive Portals.
 * Deux dimensions identiques (a8a, a8b), chacune avec la même Mini à la même pose : le joueur reste sur « sa » Mini.
 * Modes : vanilla (référence), noscreen (changement vanilla, écran de chargement supprimé côté client),
 * nopacket (piste 1 : le joueur est retiré d'un niveau et ajouté à l'autre sans paquet de réapparition).
 */
final class A8Server {
    static final ResourceLocation A = ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, "a8a");
    static final ResourceLocation B = ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, "a8b");
    private static final Path LOG = Path.of(System.getProperty("spacetest.results", "../test-results"), "A8", "server-a8.log");

    private A8Server() {}

    static void registerPhysics() {
        for (ResourceLocation id : new ResourceLocation[] { A, B }) {
            DimensionPhysicsData.putPhysics(A1Runner.levelKey(id), new DimensionPhysics(id, 0,
                Optional.of(0f), Optional.of(new org.joml.Vector3f(0, 0, 0)), Optional.of(0.0), Optional.empty(), Optional.empty(), false));
        }
    }

    private record Later(long tick, Runnable run) {}
    private static final java.util.List<Later> LATER = new java.util.ArrayList<>();
    private static long now;

    static void tick() {
        now++;
        for (Later l : new java.util.ArrayList<>(LATER)) {
            if (l.tick() <= now) {
                LATER.remove(l);
                l.run().run();
            }
        }
    }

    /** Contenu de la Mini d'un niveau : nombre de sub-levels, blocs, coffres, pose. */
    static String state(ServerLevel l) {
        var c = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(l);
        if (c == null) return "pas de conteneur Sable";
        var all = c.getAllSubLevels();
        if (all.isEmpty()) return "aucun sub-level";
        StringBuilder sb = new StringBuilder(all.size() + " sub-level(s) :");
        for (ServerSubLevel s : all) {
            sb.append(' ').append(s.getUniqueId().toString(), 0, 8).append(' ').append(A2Runner.fingerprint(l, s)).append(" pose=")
                .append(A1Runner.fmt(new Vector3d(s.logicalPose().position()))).append(" retiré=").append(s.isRemoved());
        }
        return sb.toString();
    }

    /** Garantit une Mini intacte dans le niveau (la reconstruit si le sub-level manque ou n'a plus de blocs). */
    static ServerSubLevel ensureMini(ServerLevel l) {
        var c = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(l);
        for (ServerSubLevel s : new java.util.ArrayList<>(c.getAllSubLevels())) {
            if (A2Runner.fingerprint(l, s).startsWith("blocs=0")) {
                log("Mini sans blocs retirée dans " + l.dimension().location() + " : " + state(l));
                s.deleteAllEntities();
                c.removeSubLevel(s, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
            }
        }
        if (c.getAllSubLevels().isEmpty()) {
            log("Mini reconstruite dans " + l.dimension().location());
            return A4Runner.assembleSmall(l, new BlockPos(0, 64, 0));
        }
        return c.getAllSubLevels().get(0);
    }

    static String status(MinecraftServer server) {
        StringBuilder sb = new StringBuilder();
        for (ResourceLocation id : new ResourceLocation[] { A, B }) {
            ServerLevel l = server.getLevel(A1Runner.levelKey(id));
            sb.append(id.getPath()).append(" : ").append(l == null ? "niveau absent" : state(l)).append(" | joueurs=").append(l == null ? 0 : l.players().size()).append('\n');
        }
        log("status : " + sb.toString().replace('\n', ';'));
        return sb.toString();
    }

    static String setup(ServerPlayer player) {
        MinecraftServer server = player.server;
        ServerLevel a = DimDispatch.create(server, A);
        ServerLevel b = DimDispatch.create(server, B);
        // une seule Mini, dans a8a : elle voyage avec le joueur (A9, mécanisme 3) ; b est vidée des restes d'essais précédents
        for (ServerSubLevel old : new java.util.ArrayList<>(dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(b).getAllSubLevels())) {
            old.deleteAllEntities();
            dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(b).removeSubLevel(old, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        }
        ensureMini(a);
        ServerSubLevel s = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(a).getAllSubLevels().get(0);
        Vector3d p = new Vector3d(s.logicalPose().position());
        player.teleportTo(a, p.x, p.y + 2, p.z, 0f, 0f);
        log("setup : une Mini dans a8a (elle suivra le joueur), pose " + A1Runner.fmt(p));
        return "Prêt : tu es sur la Mini de a8a ; elle suit le joueur à chaque changement de dimension. Utilise /spacetest a8 go vanilla|noscreen|nopacket (et /a8client noscreen on|off côté client).";
    }

    static String go(String mode, ServerPlayer player) {
        ServerLevel from = player.serverLevel();
        ServerLevel to = player.server.getLevel(A1Runner.levelKey(from.dimension().location().equals(A) ? B : A));
        if (to == null) return "Fais d'abord /spacetest a8 setup";
        log("avant go : " + from.dimension().location() + " [" + state(from) + "] -> " + to.dimension().location() + " [" + state(to) + "]");
        // la Mini suit le joueur : on la transfère d'abord (sérialisation, ordre « charger puis retirer »), puis le joueur change de dimension
        ServerSubLevel mini = ensureMini(from);
        // nopacket2 : la copie est chargée dans la destination, le joueur y passe, et SEULEMENT ENSUITE la source est retirée,
        // pour que le client ne reçoive pas l'arrêt de suivi de la Mini avant l'arrivée de la nouvelle.
        boolean sourceAfter = mode.equals("nopacket2");
        A9Runner.keepSource = sourceAfter;
        ServerSubLevel moved;
        try {
            moved = A9Runner.serializeReload(mini, from, to);
        } finally {
            A9Runner.keepSource = false;
        }
        if (moved == null) return "Transfert de la Mini refusé (voir le journal)";
        Vec3 pos = player.position();
        long t0 = System.nanoTime();
        try {
            switch (mode) {
                case "vanilla", "noscreen" -> player.changeDimension(new DimensionTransition(to, pos, Vec3.ZERO, player.getYRot(), player.getXRot(), DimensionTransition.DO_NOTHING));
                case "nopacket", "nopacket2" -> {
                    from.removePlayerImmediately(player, Entity.RemovalReason.CHANGED_DIMENSION);
                    player.setServerLevel(to);
                    player.setPos(pos);
                    to.addDuringTeleport(player);
                    player.connection.resetPosition();
                }
                default -> { return "Mode inconnu : " + mode; }
            }
        } catch (Throwable t) {
            log("ÉCHEC mode " + mode + " : " + t);
            SpaceTestMod.LOGGER.error("A8", t);
            return "Échec : " + t;
        }
        if (sourceAfter) {
            mini.deleteAllEntities();
            dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(from).removeSubLevel(mini, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        for (long k : new long[] { 2, 20, 100 }) {
            LATER.add(new Later(now + k, () -> log("+" + k + " ticks dans " + to.dimension().location() + " : " + state(to) + " | joueur=(" + String.format("%.1f %.1f %.1f", player.getX(), player.getY(), player.getZ()) + ") niveau joueur=" + player.level().dimension().location()
                + " | dimension d'origine : " + state(from))));
        }
        log(String.format("go %s (Mini transférée avec le joueur) : %s -> %s en %.2f ms, position %s", mode, from.dimension().location(), to.dimension().location(), ms, A1Runner.fmt(new Vector3d(pos.x, pos.y, pos.z))));
        return String.format("Changement (%s) en %.1f ms", mode, ms);
    }

    private static void log(String line) {
        SpaceTestMod.LOGGER.info("[A8] {}", line);
        try {
            Files.createDirectories(LOG.getParent());
            Files.writeString(LOG, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            SpaceTestMod.LOGGER.error("[A8] log", e);
        }
    }
}
