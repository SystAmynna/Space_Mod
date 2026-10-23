package fr.systamynna.spacemod.strategic.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import fr.systamynna.spacemod.ship.PilotManager;
import fr.systamynna.spacemod.ship.ShipManager;
import fr.systamynna.spacemod.ship.ShipRegistry;
import fr.systamynna.spacemod.strategic.SpaceObject;
import fr.systamynna.spacemod.strategic.StrategicLayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /spaceship create <nom> <objet> [shuttle]}, {@code /spaceship remove <nom>} et {@code /spacepilot <joueur> <nom>}, réservés aux opérateurs. Le nom est
 * simple et unique parmi les vaisseaux ; l'objet est un identifiant texte (voir {@code /spaceobject list}), parent du
 * vaisseau et lieu de son apparition.
 */
public final class ShipCommands {
    /** Classe utilitaire. */
    private ShipCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spaceship").requires(s -> s.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("other", StringArgumentType.string())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(SpaceObjectCommands.ids(), b))
                                        .executes(c -> create(c.getSource(), StringArgumentType.getString(c, "name"),
                                                StringArgumentType.getString(c, "other"), true))
                                        .then(Commands.literal("shuttle")
                                                .executes(c -> create(c.getSource(), StringArgumentType.getString(c, "name"),
                                                        StringArgumentType.getString(c, "other"), false))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(ShipRegistry.names(StrategicLayer.get()), b))
                                .executes(c -> remove(c.getSource(), StringArgumentType.getString(c, "name"))))));
        dispatcher.register(Commands.literal("spacepilot").requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("ship", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(ShipRegistry.names(StrategicLayer.get()), b))
                                .executes(c -> pilot(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                        StringArgumentType.getString(c, "ship"))))));
    }

    /** Le joueur, dans le vaisseau, en devient le pilote (ECHAP pour quitter). */
    private static int pilot(CommandSourceStack source, ServerPlayer player, String shipName) {
        SpaceObject ship = ShipRegistry.find(StrategicLayer.get(), shipName);
        if (ship == null) return fail(source, "Vaisseau inconnu : " + shipName);
        try {
            PilotManager.start(player, ship);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return fail(source, e.getMessage());
        }
        source.sendSuccess(() -> Component.literal(player.getGameProfile().getName() + " pilote " + shipName), true);
        return 1;
    }

    private static int create(CommandSourceStack source, String name, String otherId, boolean large) {
        SpaceObject other = SpaceObjectCommands.byId(otherId);
        if (other == null) return fail(source, "Objet inconnu : " + otherId);
        try {
            ShipManager.create(source.getServer(), name, other, large);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return fail(source, e.getMessage());
        }
        source.sendSuccess(() -> Component.literal((large ? "Vaisseau " : "Navette ") + name + " créé à côté de " + otherId), true);
        return 1;
    }

    private static int remove(CommandSourceStack source, String name) {
        try {
            ShipManager.remove(source.getServer(), name);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return fail(source, e.getMessage());
        }
        source.sendSuccess(() -> Component.literal("Vaisseau " + name + " supprimé"), true);
        return 1;
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }
}
