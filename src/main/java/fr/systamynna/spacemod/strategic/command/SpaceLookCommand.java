package fr.systamynna.spacemod.strategic.command;

import java.util.List;
import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import fr.systamynna.spacemod.strategic.DimensionManager;
import fr.systamynna.spacemod.strategic.RailMath;
import fr.systamynna.spacemod.strategic.SkyFrame;
import fr.systamynna.spacemod.strategic.SpaceClock;
import fr.systamynna.spacemod.strategic.SpaceObject;
import fr.systamynna.spacemod.strategic.StrategicLayer;
import fr.systamynna.spacemod.strategic.component.ComponentTypes;
import fr.systamynna.spacemod.strategic.component.HostedDimension;
import fr.systamynna.spacemod.strategic.component.hostedPropriety.PortPoint;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /spacelook <id>} : tourne la caméra du joueur vers un objet céleste, pour évaluer son rendu. La direction vient de la
 * position de l'objet relative à l'hôte de la dimension ({@link RailMath#relativePosition}), vue du port comme le fait le
 * ciel ({@link SkyFrame}). Réservé aux opérateurs ; la cible n'est pas forcément visible (le rendu filtre ce qui est trop loin).
 */
public final class SpaceLookCommand {
    /** Classe utilitaire. */
    private SpaceLookCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("spacelook").requires(s -> s.hasPermission(2))
                .then(Commands.argument("id", StringArgumentType.greedyString())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(SpaceObjectCommands.ids().stream().map(i -> i.replace("\"", "")).toList(), b))
                        .executes(c -> look(c.getSource(), unquote(StringArgumentType.getString(c, "id"))))));
    }

    private static String unquote(String id) {
        String s = id.trim();
        return s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"") ? s.substring(1, s.length() - 1) : s;
    }

    private static int look(CommandSourceStack source, String id) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            StrategicLayer layer = StrategicLayer.get();
            SpaceObject target = SpaceObjectCommands.byId(id);
            if (target == null) return fail(source, "Objet inconnu : " + id);
            SpaceObject host = layer.hostOf(player.level().dimension());
            if (host == null) return fail(source, "Vous n'êtes pas dans la dimension d'un objet");
            HostedDimension hosted = host.get(ComponentTypes.HOSTED_DIMENSION);
            PortPoint port = hosted.port(DimensionManager.portOf(player.getUUID()));
            if (port == null) port = hosted.ports().get(0);
            double t = layer.clock().spaceTime(SpaceClock.gameTime(source.getServer()));
            SkyFrame frame = SkyFrame.of(RailMath.local(host, t), hosted.skyRotation(port), new double[] { port.x(), port.y(), port.z() });
            float[] angles = SkyFrame.lookAngles(frame.view(RailMath.relativePosition(layer, target, host, t)));
            if (angles == null) return fail(source, id + " : direction indéfinie (même position que l'observateur)");
            // position inchangée (relative), rotation absolue
            player.connection.teleport(0, 0, 0, angles[0], angles[1], Set.of(RelativeMovement.X, RelativeMovement.Y, RelativeMovement.Z));
            source.sendSuccess(() -> Component.literal("Vue vers " + id), false);
            return 1;
        } catch (CommandSyntaxException | IllegalStateException e) {
            return fail(source, e.getMessage());
        }
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }
}
