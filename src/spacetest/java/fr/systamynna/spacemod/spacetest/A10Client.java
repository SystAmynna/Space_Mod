package fr.systamynna.spacemod.spacetest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Test A10 (manuel) : mesure du nombre d'images par seconde sur une durée donnée.
 * /a10client fps <secondes> <étiquette> écrit moyenne, 1 % bas, image la plus longue dans test-results/A10/client-a10.log.
 */
final class A10Client {
    private static final Path FILE = Path.of(System.getProperty("spacetest.results", "../test-results"), "A10", "client-a10.log");
    private static final List<Double> FRAMES = new ArrayList<>();
    private static long endAt;
    private static long last;
    private static String label = "";

    private A10Client() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post e) -> {
            long now = System.nanoTime();
            if (endAt > 0) {
                if (last != 0) FRAMES.add((now - last) / 1e6);
                if (now >= endAt) finish();
            }
            last = now;
        });
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> e.getDispatcher().register(Commands.literal("a10client")
            .then(Commands.literal("fps").then(Commands.argument("seconds", IntegerArgumentType.integer(5, 600))
                .then(Commands.argument("label", StringArgumentType.word()).executes(c -> {
                    FRAMES.clear();
                    label = StringArgumentType.getString(c, "label");
                    endAt = System.nanoTime() + IntegerArgumentType.getInteger(c, "seconds") * 1_000_000_000L;
                    c.getSource().sendSuccess(() -> Component.literal("Mesure FPS lancée (" + label + ")"), false);
                    return 1;
                }))))
            .then(Commands.literal("note").then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> {
                write("NOTE : " + StringArgumentType.getString(c, "text"));
                return 1;
            })))));
    }

    private static void finish() {
        endAt = 0;
        if (FRAMES.isEmpty()) return;
        List<Double> sorted = new ArrayList<>(FRAMES);
        Collections.sort(sorted);
        double sum = 0;
        for (double d : sorted) sum += d;
        double avg = sum / sorted.size();
        double p99 = sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(0.99 * sorted.size()) - 1));
        write(String.format("MESURE %s : %d images, moyenne %.1f FPS (%.2f ms), 1 %% bas %.1f FPS, image la plus longue %.1f ms",
            label, sorted.size(), 1000 / avg, avg, 1000 / p99, sorted.get(sorted.size() - 1)));
        net.minecraft.client.Minecraft.getInstance().gui.getChat().addMessage(Component.literal("Mesure " + label + " terminée : voir client-a10.log"));
    }

    private static void write(String line) {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, System.currentTimeMillis() + " " + line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            SpaceTestMod.LOGGER.error("[A10] journal", e);
        }
    }
}
