package fr.systamynna.spacemod.commands;

import java.util.function.Consumer;
import java.util.function.Predicate;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.StrategicLayer;
import fr.systamynna.spacemod.core.Transform;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;

/**
 * Argument {@code <spaceobject>} partagé : {@code uuid <uuid>} ou {@code addr <adresse>}
 * (adresse entre guillemets : elle contient des '/').
 */
public final class SpaceObjectArg {
    private SpaceObjectArg() {}

    public interface Action {
        void run(CommandContext<CommandSourceStack> c);
    }

    private static final StrategicLayer S = AmynnaSpaceMod.STRATEGIC;

    // shortcut: les suggestions parcourent tout l'arbre à chaque frappe ; ajouter un cache si l'arbre devient grand.
    private static SuggestionProvider<CommandSourceStack> uuids(Predicate<SpaceObject> f) {
        return (c, b) -> SharedSuggestionProvider.suggest(S.tree().all().stream().filter(f).map(o -> o.getUuid().toString()), b);
    }

    private static SuggestionProvider<CommandSourceStack> addrs(Predicate<SpaceObject> f) {
        return (c, b) -> SharedSuggestionProvider.suggest(S.tree().all().stream().filter(f).map(o -> '"' + S.tree().addressOf(o) + '"'), b);
    }

    /** Complétions de {@link Transform#editAll}, appliquées au dernier mot. */
    public static final SuggestionProvider<CommandSourceStack> TRANSFORM_ARGS = (c, b) -> {
        String rem = b.getRemaining();
        return SharedSuggestionProvider.suggest(new Transform().suggest(rem), b.createOffset(b.getStart() + rem.lastIndexOf(' ') + 1));
    };

    public static final SuggestionProvider<CommandSourceStack> TOP_UUIDS = (c, b) ->
            SharedSuggestionProvider.suggest(S.tree().topLevel().stream().map(o -> o.getUuid().toString()), b);
    public static final SuggestionProvider<CommandSourceStack> TOP_NAMES = (c, b) ->
            SharedSuggestionProvider.suggest(S.tree().topLevel().stream().map(SpaceObject::getName), b);

    public static void selector(ArgumentBuilder<CommandSourceStack, ?> parent, int i, Consumer<ArgumentBuilder<CommandSourceStack, ?>> end) {
        selector(parent, i, o -> true, end);
    }

    /** Ajoute à {@code parent} les branches {@code uuid}/{@code addr} (suggestions limitées à {@code filter}), puis applique {@code end} à chaque argument. */
    public static void selector(ArgumentBuilder<CommandSourceStack, ?> parent, int i, Predicate<SpaceObject> filter,
            Consumer<ArgumentBuilder<CommandSourceStack, ?>> end) {
        var u = Commands.argument("uuid" + i, UuidArgument.uuid()).suggests(uuids(filter));
        var a = Commands.argument("addr" + i, StringArgumentType.string()).suggests(addrs(filter));
        end.accept(u);
        end.accept(a);
        parent.then(Commands.literal("uuid").then(u));
        parent.then(Commands.literal("addr").then(a));
    }

    public static SpaceObject target(CommandContext<CommandSourceStack> c, int i) {
        try {
            return S.getOrThrow(UuidArgument.getUuid(c, "uuid" + i));
        } catch (IllegalArgumentException notUuid) { // l'argument uuid{i} n'existe pas : branche addr (ou name pour `root`)
            try {
                return S.tree().resolve(StringArgumentType.getString(c, "addr" + i));
            } catch (IllegalArgumentException notAddr) {
                return S.tree().resolve(StringArgumentType.getString(c, "name" + i));
            }
        }
    }

    public static int run(CommandContext<CommandSourceStack> c, Action a) {
        try {
            a.run(c);
            return 1;
        } catch (SpaceObjectException e) {
            c.getSource().sendFailure(Component.translatable(e.getMessage(), e.args()));
            return 0;
        }
    }
}
