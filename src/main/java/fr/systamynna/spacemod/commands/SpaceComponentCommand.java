package fr.systamynna.spacemod.commands;

import static fr.systamynna.spacemod.commands.SpaceObjectArg.*;

import java.util.function.Function;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.StrategicLayer;
import fr.systamynna.spacemod.core.component.SpaceComponent;
import fr.systamynna.spacemod.core.component.SpaceComponents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/** {@code /spacecomponent <objet> list [full] | add <comp> | remove <comp> | edit <comp> <args…>} (OP). */
public final class SpaceComponentCommand {
    private SpaceComponentCommand() {}

    private static final StrategicLayer S = AmynnaSpaceMod.STRATEGIC;

    /** Composants que l'objet ciblé a ({@code has}) ou n'a pas. */
    private static SuggestionProvider<CommandSourceStack> ids(boolean has) {
        return (c, b) -> {
            try {
                var owned = target(c, 0).components().stream().map(SpaceComponent::id).toList();
                return SharedSuggestionProvider.suggest(SpaceComponents.ids().stream().filter(i -> owned.contains(i) == has), b);
            } catch (RuntimeException e) { // objet introuvable : pas de suggestion
                return b.buildFuture();
            }
        };
    }

    /** Complétions fournies par le composant édité, appliquées au dernier mot. */
    private static final SuggestionProvider<CommandSourceStack> EDIT_ARGS = (c, b) -> {
        try {
            SpaceComponent comp = target(c, 0).getComponent(SpaceComponents.type(StringArgumentType.getString(c, "comp"))).orElse(null);
            if (comp == null) return b.buildFuture();
            String rem = b.getRemaining();
            return SharedSuggestionProvider.suggest(comp.suggest(rem), b.createOffset(b.getStart() + rem.lastIndexOf(' ') + 1));
        } catch (RuntimeException e) { // objet ou composant introuvable : pas de suggestion
            return b.buildFuture();
        }
    };

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var root = Commands.literal("spacecomponent").requires(s -> s.hasPermission(2));
        // selector() applique le même sous-arbre à chaque branche : on le reconstruit à chaque appel.
        selector(root, 0, n -> {
            Function<Boolean, com.mojang.brigadier.Command<CommandSourceStack>> list = full -> c -> run(c, x -> {
                for (SpaceComponent comp : target(x, 0).components())
                    x.getSource().sendSuccess(() -> Component.literal(full ? comp.id() + ": " + comp.describe() : comp.id()), false);
            });
            n.then(Commands.literal("list").executes(list.apply(false)).then(Commands.literal("full").executes(list.apply(true))));
            n.then(comp("add", false, x -> {
                SpaceObject o = target(x, 0);
                String id = StringArgumentType.getString(x, "comp");
                S.addComponent(o.getUuid(), SpaceComponents.create(id));
                done(x, "added", id, o);
            }));
            n.then(comp("remove", true, x -> {
                SpaceObject o = target(x, 0);
                String id = StringArgumentType.getString(x, "comp");
                S.removeComponent(o.getUuid(), SpaceComponents.type(id));
                done(x, "removed", id, o);
            }));
            n.then(Commands.literal("edit").then(Commands.argument("comp", StringArgumentType.word()).suggests(ids(true))
                    .then(Commands.argument("args", StringArgumentType.greedyString()).suggests(EDIT_ARGS).executes(c -> run(c, x -> {
                        SpaceObject o = target(x, 0);
                        String id = StringArgumentType.getString(x, "comp");
                        String args = StringArgumentType.getString(x, "args");
                        S.editComponent(o.getUuid(), SpaceComponents.type(id), comp -> comp.edit(args));
                        done(x, "edited", id, o);
                    })))));
        });
        d.register(root);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> comp(String literal, boolean has, Action a) {
        return Commands.literal(literal).then(Commands.argument("comp", StringArgumentType.word()).suggests(ids(has)).executes(c -> run(c, a)));
    }

    private static void done(CommandContext<CommandSourceStack> c, String key, String id, SpaceObject o) {
        c.getSource().sendSuccess(() -> Component.translatable("amynnaspacemod.component." + key, id, S.tree().addressOf(o)), true);
    }
}
