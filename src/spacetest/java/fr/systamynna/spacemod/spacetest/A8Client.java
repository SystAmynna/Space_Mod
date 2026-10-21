package fr.systamynna.spacemod.spacetest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.mojang.brigadier.arguments.BoolArgumentType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Journal client du test A8 : écrans ouverts (dont « Réception du niveau »), changements de niveau client, nombre de
 * chunks chargés autour du changement, images longues. Commande client /a8client noscreen on|off (supprime l'écran de chargement).
 * Écrit test-results/A8/client-a8.log.
 */
final class A8Client {
    private static final Path FILE = Path.of(System.getProperty("spacetest.results", "../test-results"), "A8", "client-a8.log");
    private static BufferedWriter out;
    private static boolean noScreen;
    private static ClientLevel lastLevel;
    private static int watch = -1;
    private static long lastFrame;
    private static long frameCount;
    private static String lastSubLevels = "";

    private A8Client() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Opening e) -> {
            String name = e.getNewScreen() == null ? "null" : e.getNewScreen().getClass().getSimpleName();
            boolean cancel = noScreen && e.getNewScreen() instanceof ReceivingLevelScreen;
            log("écran ouvert : " + name + (cancel ? " (ANNULÉ)" : ""));
            if (cancel) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post e) -> {
            long now = System.nanoTime();
            if (lastFrame != 0 && Minecraft.getInstance().level != null && (now - lastFrame) / 1_000_000 > 40) {
                Minecraft mc = Minecraft.getInstance();
                log(String.format("image longue : %d ms, écran=%s", (now - lastFrame) / 1_000_000, mc.screen == null ? "aucun" : mc.screen.getClass().getSimpleName()));
            }
            lastFrame = now;
            frameCount++;
            logSubLevels();
        });
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> e.getDispatcher().register(Commands.literal("a8client")
            .then(Commands.literal("noscreen").then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                noScreen = BoolArgumentType.getBool(c, "on");
                c.getSource().sendSuccess(() -> Component.literal("suppression de l'écran de chargement : " + noScreen), false);
                log("réglage noscreen = " + noScreen);
                return 1;
            })))));
    }

    /** Journalise chaque changement de l'ensemble des sub-levels côté client (apparition, finalisation, disparition). */
    private static void logSubLevels() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var c = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(mc.level);
        if (c == null) return;
        StringBuilder sb = new StringBuilder();
        for (var sl : c.getAllSubLevels()) {
            sb.append(sl.getUniqueId().toString(), 0, 8).append(" plot=").append(sl.getPlot().plotPos).append(sl.isFinalized() ? " finalisé" : " NON finalisé").append("; ");
        }
        String now = sb.toString();
        if (!now.equals(lastSubLevels)) {
            lastSubLevels = now;
            log("image " + frameCount + " : sub-levels clients = " + (now.isEmpty() ? "AUCUN" : now));
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            if (mc.level != null) {
                log("niveau client changé : " + mc.level.dimension().location() + ", chunks chargés=" + mc.level.getChunkSource().getLoadedChunksCount()
                    + ", écran=" + (mc.screen == null ? "aucun" : mc.screen.getClass().getSimpleName()));
                watch = 0;
            }
        }
        if (watch >= 0 && mc.level != null && mc.player != null) {
            if (watch == 1 || watch == 2 || watch == 5 || watch == 10 || watch == 20 || watch == 40) {
                log(String.format("+%d ticks : chunks chargés=%d, écran=%s, joueur=(%.1f %.1f %.1f)", watch, mc.level.getChunkSource().getLoadedChunksCount(),
                    mc.screen == null ? "aucun" : mc.screen.getClass().getSimpleName(), mc.player.getX(), mc.player.getY(), mc.player.getZ()));
            }
            if (++watch > 40) watch = -1;
        }
    }

    private static void log(String line) {
        try {
            if (out == null) {
                Files.createDirectories(FILE.getParent());
                out = Files.newBufferedWriter(FILE, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            }
            out.write(String.format("%d %s%n", System.currentTimeMillis(), line));
            out.flush();
        } catch (IOException e) {
            SpaceTestMod.LOGGER.error("[A8] journal client", e);
        }
    }
}
