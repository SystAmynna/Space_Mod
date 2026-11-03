package fr.systamynna.spacemod.commands;

import static fr.systamynna.spacemod.commands.SpaceObjectArg.*;

import java.util.function.Function;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.StrategicLayer;
import fr.systamynna.spacemod.core.controller.SpaceController;
import fr.systamynna.spacemod.core.controller.SpaceControllers;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/** {@code /spacecontroller <objet> get [full] | set <type|none> | edit <args…>} (OP). */
public final class SpaceControllerCommand {
    private SpaceControllerCommand() {}

    private static final StrategicLayer S = AmynnaSpaceMod.STRATEGIC;
    private static final String NONE = "none";

    /** Complétions fournies par le contrôleur édité, appliquées au dernier mot. */
    private static final SuggestionProvider<CommandSourceStack> EDIT_ARGS = (c, b) -> {
        try {
            SpaceController ctl = target(c, 0).getController();
            if (ctl == null) return b.buildFuture();
            String rem = b.getRemaining();
            return SharedSuggestionProvider.suggest(ctl.suggest(rem), b.createOffset(b.getStart() + rem.lastIndexOf(' ') + 1));
        } catch (RuntimeException e) { // objet introuvable : pas de suggestion
            return b.buildFuture();
        }
    };

    /** Contrôleurs applicables : tous sauf celui équipé, plus {@code none}. */
    private static final SuggestionProvider<CommandSourceStack> SETTABLE = (c, b) -> {
        try {
            SpaceController cur = target(c, 0).getController();
            return SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(
                    SpaceControllers.ids().stream().filter(i -> cur == null || !i.equals(cur.id())), java.util.stream.Stream.of(NONE)), b);
        } catch (RuntimeException e) { // objet introuvable : pas de suggestion
            return b.buildFuture();
        }
    };

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var root = Commands.literal("spacecontroller").requires(s -> s.hasPermission(2));
        selector(root, 0, n -> {
            Function<Boolean, com.mojang.brigadier.Command<CommandSourceStack>> get = full -> c -> run(c, x -> {
                SpaceObject o = target(x, 0);
                SpaceController ctl = o.getController();
                String what = ctl == null ? NONE : full ? ctl.id() + " {" + ctl.describe() + "}" : ctl.id();
                x.getSource().sendSuccess(() -> Component.literal(S.tree().addressOf(o) + " controller: " + what), false);
            });
            n.then(Commands.literal("get").executes(get.apply(false)).then(Commands.literal("full").executes(get.apply(true))));
            n.then(Commands.literal("set").then(Commands.argument("type", StringArgumentType.word()).suggests(SETTABLE)
                    .executes(c -> run(c, x -> {
                        SpaceObject o = target(x, 0);
                        String type = StringArgumentType.getString(x, "type");
                        if (type.equals(NONE)) {
                            S.setController(o.getUuid(), null);
                            done(x, "cleared", o);
                            return;
                        }
                        SpaceController cur = o.getController();
                        if (cur != null && cur.id().equals(type)) throw new SpaceObjectException("amynnaspacemod.controller.already", type);
                        S.setController(o.getUuid(), SpaceControllers.create(type));
                        done(x, "set", o, type);
                    }))));
            n.then(Commands.literal("edit").then(Commands.argument("args", StringArgumentType.greedyString()).suggests(EDIT_ARGS)
                    .executes(c -> run(c, x -> {
                        SpaceObject o = target(x, 0);
                        String args = StringArgumentType.getString(x, "args");
                        S.editController(o.getUuid(), ctl -> ctl.edit(args));
                        done(x, "edited", o);
                    }))));
        });
        d.register(root);
    }

    private static void done(CommandContext<CommandSourceStack> c, String key, SpaceObject o, Object... pre) {
        Object[] args = new Object[pre.length + 1];
        System.arraycopy(pre, 0, args, 0, pre.length);
        args[pre.length] = S.tree().addressOf(o);
        c.getSource().sendSuccess(() -> Component.translatable("amynnaspacemod.controller." + key, args), true);
    }
}
