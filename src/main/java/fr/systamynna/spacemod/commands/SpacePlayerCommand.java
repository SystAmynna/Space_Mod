package fr.systamynna.spacemod.commands;

import static fr.systamynna.spacemod.commands.SpaceObjectArg.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.core.SpaceNet;
import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.StrategicLayer;
import fr.systamynna.spacemod.core.Subscriptions;
import fr.systamynna.spacemod.core.clock.ClockNet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * {@code /spaceplayerstate <pseudo>}, {@code /spaceplayersuscribe <pseudo> suscribe|unsuscribe <objet>},
 * {@code /spaceplayerparent <pseudo> <objet>|clear} (OP).
 */
public final class SpacePlayerCommand {
    private SpacePlayerCommand() {}

    private static final StrategicLayer S = AmynnaSpaceMod.STRATEGIC;
    private static final Subscriptions SUBS = AmynnaSpaceMod.SUBS;

    private static ServerPlayer player(CommandContext<CommandSourceStack> c) {
        try {
            return EntityArgument.getPlayer(c, "player");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { // joueur parti entre le parsing et l'exécution
            throw new SpaceObjectException("amynnaspacemod.player.offline");
        }
    }

    private static void ok(CommandContext<CommandSourceStack> c, String key, Object... args) {
        c.getSource().sendSuccess(() -> Component.translatable("amynnaspacemod.player." + key, args), true);
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("spaceplayerstate").requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    var src = c.getSource();
                    ServerPlayer p = player(c);
                    var requester = src.getEntity() instanceof ServerPlayer r ? r.getUUID() : ClockNet.CONSOLE;
                    PacketDistributor.sendToPlayer(p, new SpaceNet.StateProbe(requester, SUBS.subscriptions(p)));
                    return 1;
                })));

        var player = Commands.argument("player", EntityArgument.player());
        for (String mode : new String[] {"suscribe", "unsuscribe"}) {
            var m = Commands.literal(mode);
            selector(m, 0, n -> n.executes(c -> run(c, x -> {
                ServerPlayer p = player(x);
                SpaceObject o = target(x, 0);
                if (mode.equals("suscribe")) SUBS.subscribe(p, o); else SUBS.unsubscribe(p, o.getUuid());
                ok(x, mode, p.getName(), S.tree().addressOf(o));
            })));
            player.then(m);
        }
        d.register(Commands.literal("spaceplayersuscribe").requires(s -> s.hasPermission(2)).then(player));

        var parent = Commands.argument("player", EntityArgument.player());
        selector(parent, 0, n -> n.executes(c -> run(c, x -> parent(x, target(x, 0)))));
        parent.then(Commands.literal("clear").executes(c -> run(c, x -> parent(x, null))));
        d.register(Commands.literal("spaceplayerparent").requires(s -> s.hasPermission(2)).then(parent));
    }

    /** Fixe (ou efface) le parent, puis renvoie la config de ciel : les abonnements automatiques suivent le nouveau parent. */
    private static void parent(CommandContext<CommandSourceStack> x, SpaceObject o) {
        ServerPlayer p = player(x);
        SUBS.setParent(p.getUUID(), o == null ? null : o.getUuid());
        AmynnaSpaceMod.RENDER.send(p);
        if (o == null) ok(x, "parent_clear", p.getName());
        else ok(x, "parent_set", p.getName(), S.tree().addressOf(o));
    }
}
