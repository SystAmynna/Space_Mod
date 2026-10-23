package fr.systamynna.spacemod.strategic.command;

import com.mojang.brigadier.CommandDispatcher;

import fr.systamynna.spacemod.knowledge.SpaceKnowledge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /spaceknowledge <on|off> <joueur>}, réservé aux opérateurs : active l'état de connaissance de l'espace. */
public final class KnowledgeCommand {
    /** Classe utilitaire. */
    private KnowledgeCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spaceknowledge").requires(s -> s.hasPermission(2))
                .then(Commands.literal("on").then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> set(c.getSource(), EntityArgument.getPlayer(c, "player"), true))))
                .then(Commands.literal("off").then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> set(c.getSource(), EntityArgument.getPlayer(c, "player"), false)))));
    }

    private static int set(CommandSourceStack source, ServerPlayer player, boolean on) {
        SpaceKnowledge.set(player, on);
        source.sendSuccess(() -> Component.literal("Connaissance de l'espace " + (on ? "activée" : "désactivée") + " pour "
                + player.getGameProfile().getName()), true);
        return 1;
    }
}
