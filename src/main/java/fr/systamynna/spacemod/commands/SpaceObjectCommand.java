package fr.systamynna.spacemod.commands;

import static fr.systamynna.spacemod.commands.SpaceObjectArg.*;

import java.util.function.Function;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.StrategicLayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;

/**
 * {@code /spaceobject list [full] | root | add | remove keep|recursive <objet> | modify} (OP).
 * {@code <objet>} = {@code uuid <uuid>} ou {@code addr <adresse>} (adresse entre guillemets : elle contient des '/').
 */
public final class SpaceObjectCommand {
    private SpaceObjectCommand() {}

    private static final StrategicLayer S = AmynnaSpaceMod.STRATEGIC;

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        Function<Boolean, com.mojang.brigadier.Command<CommandSourceStack>> listCmd = full -> c -> run(c, x -> S.tree().render(full).forEach(l ->
                x.getSource().sendSuccess(() -> l, false)));
        var list = Commands.literal("list").executes(listCmd.apply(false))
                .then(Commands.literal("full").executes(listCmd.apply(true)));

        var root = Commands.literal("root");
        var rootUuid = Commands.argument("uuid0", UuidArgument.uuid()).suggests(SpaceObjectArg.TOP_UUIDS);
        var rootName = Commands.argument("name0", StringArgumentType.word()).suggests(SpaceObjectArg.TOP_NAMES);
        Action setRoot = x -> {
            SpaceObject o = target(x, 0);
            S.setRoot(o);
            ok(x, "root_set", o.getName());
        };
        root.then(Commands.literal("uuid").then(rootUuid.executes(c -> run(c, setRoot))));
        root.then(Commands.literal("name").then(rootName.executes(c -> run(c, setRoot))));

        var add = Commands.literal("add");
        var name = Commands.argument("name", StringArgumentType.word());
        Action doAdd = x -> {
            boolean hasParent = x.getNodes().stream().anyMatch(n -> n.getNode().getName().matches("uuid0|addr0"));
            SpaceObject o = S.add(StringArgumentType.getString(x, "name"), hasParent ? target(x, 0) : null);
            ok(x, "added", S.tree().addressOf(o), o.getUuid().toString());
        };
        name.executes(c -> run(c, doAdd));
        selector(name, 0, n -> n.executes(c -> run(c, doAdd)));
        add.then(name);

        var remove = Commands.literal("remove");
        for (String mode : new String[] {"keep", "recursive"}) {
            var m = Commands.literal(mode);
            selector(m, 0, n -> n.executes(c -> run(c, x -> {
                SpaceObject o = target(x, 0);
                String addr = S.tree().addressOf(o);
                S.remove(o, mode.equals("recursive"));
                ok(x, "removed", addr);
            })));
            remove.then(m);
        }

        var modify = Commands.literal("modify");
        selector(modify, 0, n -> {
            n.then(Commands.literal("none").executes(c -> run(c, x -> {
                SpaceObject o = target(x, 0);
                S.setParent(o, null);
                ok(x, "detached", S.tree().addressOf(o));
            })));
            selector(n, 1, m -> m.executes(c -> run(c, x -> {
                SpaceObject o = target(x, 0);
                S.setParent(o, target(x, 1));
                ok(x, "modified", S.tree().addressOf(o));
            })));
        });

        d.register(Commands.literal("spaceobject").requires(s -> s.hasPermission(2))
                .then(list).then(root).then(add).then(remove).then(modify));
    }

    private static void ok(CommandContext<CommandSourceStack> c, String key, Object... args) {
        c.getSource().sendSuccess(() -> Component.translatable("amynnaspacemod.spaceobject." + key, args), true);
    }
}
