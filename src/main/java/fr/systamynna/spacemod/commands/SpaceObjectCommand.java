package fr.systamynna.spacemod.commands;

import java.util.function.Consumer;
import java.util.function.Function;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.StrategicLayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;

/**
 * {@code /spaceobject list [full] | root | add | remove keep|recursive <objet> | modify} (OP).
 * {@code <objet>} = {@code uuid <uuid>} ou {@code addr <adresse>} (adresse entre guillemets : elle contient des '/').
 */
public final class SpaceObjectCommand {
    private SpaceObjectCommand() {}

    private interface Action {
        void run(CommandContext<CommandSourceStack> c);
    }

    private static final StrategicLayer S = AmynnaSpaceMod.STRATEGIC;

    // shortcut: les suggestions parcourent tout l'arbre à chaque frappe ; ajouter un cache si l'arbre devient grand.
    private static final SuggestionProvider<CommandSourceStack> UUIDS = (c, b) ->
            SharedSuggestionProvider.suggest(S.all().stream().map(o -> o.getUuid().toString()), b);
    private static final SuggestionProvider<CommandSourceStack> ADDRS = (c, b) ->
            SharedSuggestionProvider.suggest(S.all().stream().map(o -> '"' + S.addressOf(o) + '"'), b);
    private static final SuggestionProvider<CommandSourceStack> TOP_UUIDS = (c, b) ->
            SharedSuggestionProvider.suggest(S.topLevel().stream().map(o -> o.getUuid().toString()), b);
    private static final SuggestionProvider<CommandSourceStack> TOP_NAMES = (c, b) ->
            SharedSuggestionProvider.suggest(S.topLevel().stream().map(SpaceObject::getName), b);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        Function<Boolean, com.mojang.brigadier.Command<CommandSourceStack>> listCmd = full -> c -> run(c, x -> S.tree(full).forEach(l ->
                x.getSource().sendSuccess(() -> Component.literal(l), false)));
        var list = Commands.literal("list").executes(listCmd.apply(false))
                .then(Commands.literal("full").executes(listCmd.apply(true)));

        var root = Commands.literal("root");
        var rootUuid = Commands.argument("uuid0", UuidArgument.uuid()).suggests(TOP_UUIDS);
        var rootName = Commands.argument("name0", StringArgumentType.word()).suggests(TOP_NAMES);
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
            ok(x, "added", S.addressOf(o), o.getUuid().toString());
        };
        name.executes(c -> run(c, doAdd));
        selector(name, 0, n -> n.executes(c -> run(c, doAdd)));
        add.then(name);

        var remove = Commands.literal("remove");
        for (String mode : new String[] {"keep", "recursive"}) {
            var m = Commands.literal(mode);
            selector(m, 0, n -> n.executes(c -> run(c, x -> {
                SpaceObject o = target(x, 0);
                String addr = S.addressOf(o);
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
                ok(x, "detached", S.addressOf(o));
            })));
            selector(n, 1, m -> m.executes(c -> run(c, x -> {
                SpaceObject o = target(x, 0);
                S.setParent(o, target(x, 1));
                ok(x, "modified", S.addressOf(o));
            })));
        });

        d.register(Commands.literal("spaceobject").requires(s -> s.hasPermission(2))
                .then(list).then(root).then(add).then(remove).then(modify));
    }

    /** Ajoute à {@code parent} les branches {@code uuid <uuid>} et {@code addr <adresse>}, puis applique {@code end} à chaque argument. */
    private static void selector(ArgumentBuilder<CommandSourceStack, ?> parent, int i, Consumer<ArgumentBuilder<CommandSourceStack, ?>> end) {
        var u = Commands.argument("uuid" + i, UuidArgument.uuid()).suggests(UUIDS);
        var a = Commands.argument("addr" + i, StringArgumentType.string()).suggests(ADDRS);
        end.accept(u);
        end.accept(a);
        parent.then(Commands.literal("uuid").then(u));
        parent.then(Commands.literal("addr").then(a));
    }

    private static SpaceObject target(CommandContext<CommandSourceStack> c, int i) {
        try {
            return S.getOrThrow(UuidArgument.getUuid(c, "uuid" + i));
        } catch (IllegalArgumentException notUuid) { // l'argument uuid{i} n'existe pas : branche addr (ou name pour `root`)
            try {
                return S.resolve(StringArgumentType.getString(c, "addr" + i));
            } catch (IllegalArgumentException notAddr) {
                return S.resolve(StringArgumentType.getString(c, "name" + i));
            }
        }
    }

    private static int run(CommandContext<CommandSourceStack> c, Action a) {
        try {
            a.run(c);
            return 1;
        } catch (SpaceObjectException e) {
            c.getSource().sendFailure(Component.translatable(e.getMessage(), e.args()));
            return 0;
        }
    }

    private static void ok(CommandContext<CommandSourceStack> c, String key, Object... args) {
        c.getSource().sendSuccess(() -> Component.translatable("amynnaspacemod.spaceobject." + key, args), true);
    }
}
