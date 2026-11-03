package fr.systamynna.spacemod.commands;

import static fr.systamynna.spacemod.commands.SpaceObjectArg.*;

import java.util.function.Function;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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
 * {@code /spaceobject list [full] | root | add | remove keep|recursive <objet> | get <objet> | parent <objet> (none|<objet>) [transpose] | transform <objet> [(pos <x> <y> <z>|rot <yaw> <pitch> <roll>)…] | scale <objet> [<valeur>]} (OP).
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

        var parent = Commands.literal("parent");
        selector(parent, 0, n -> {
            var none = Commands.literal("none");
            for (boolean tr : new boolean[] {false, true}) {
                com.mojang.brigadier.Command<CommandSourceStack> detach = c -> run(c, x -> {
                    SpaceObject o = target(x, 0);
                    S.setParent(o, null, tr);
                    ok(x, "detached", S.tree().addressOf(o));
                });
                if (tr) none.then(Commands.literal("transpose").executes(detach)); else none.executes(detach);
            }
            n.then(none);
            selector(n, 1, m -> {
                for (boolean tr : new boolean[] {false, true}) {
                    com.mojang.brigadier.Command<CommandSourceStack> move = c -> run(c, x -> {
                        SpaceObject o = target(x, 0);
                        S.setParent(o, target(x, 1), tr);
                        ok(x, "modified", S.tree().addressOf(o));
                    });
                    if (tr) m.then(Commands.literal("transpose").executes(move)); else m.executes(move);
                }
            });
        });

        var get = Commands.literal("get");
        selector(get, 0, n -> n.executes(c -> run(c, x -> { // uuid → adresse, adresse → uuid
            SpaceObject o = target(x, 0);
            String out = isUuid(x, 0) ? S.tree().addressOf(o) : o.getUuid().toString();
            x.getSource().sendSuccess(() -> Component.literal(out), false);
        })));

        var edit = Commands.literal("spaceobject").requires(s -> s.hasPermission(2))
                .then(list).then(get).then(root).then(add).then(remove).then(parent);
        var b = Commands.literal("transform");
        selector(b, 0, n -> {
            n.executes(c -> run(c, x -> { // sans argument : affiche la donnée
                SpaceObject o = target(x, 0);
                show(x, "transform", o.transform.describe(), o);
            }));
            n.then(Commands.argument("args", StringArgumentType.greedyString()).suggests(TRANSFORM_ARGS).executes(c -> run(c, x -> {
                SpaceObject o = target(x, 0);
                S.editTransform(o.getUuid(), t -> t.editAll(StringArgumentType.getString(x, "args")));
                ok(x, "edited", "transform", S.tree().addressOf(o));
            })));
        });
        edit.then(b);
        var scale = Commands.literal("scale");
        selector(scale, 0, n -> {
            n.executes(c -> run(c, x -> {
                SpaceObject o = target(x, 0);
                show(x, "scale", String.valueOf(o.getScale()), o);
            }));
            n.then(Commands.argument("value", DoubleArgumentType.doubleArg()).executes(c -> run(c, x -> {
                SpaceObject o = target(x, 0);
                S.setScale(o.getUuid(), DoubleArgumentType.getDouble(x, "value"));
                ok(x, "edited", "scale", S.tree().addressOf(o));
            })));
        });
        d.register(edit.then(scale));
    }

    private static void show(CommandContext<CommandSourceStack> c, String what, String value, SpaceObject o) {
        c.getSource().sendSuccess(() -> Component.literal(S.tree().addressOf(o) + " " + what + ": " + value), false);
    }

    private static void ok(CommandContext<CommandSourceStack> c, String key, Object... args) {
        c.getSource().sendSuccess(() -> Component.translatable("amynnaspacemod.spaceobject." + key, args), true);
    }
}
