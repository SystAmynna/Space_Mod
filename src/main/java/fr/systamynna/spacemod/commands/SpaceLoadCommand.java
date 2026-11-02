package fr.systamynna.spacemod.commands;

import static fr.systamynna.spacemod.commands.SpaceObjectArg.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.StrategicLayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** {@code /spaceload list | load <objet> | unload <objet>} (OP). */
public final class SpaceLoadCommand {
    private SpaceLoadCommand() {}

    private static final StrategicLayer S = AmynnaSpaceMod.STRATEGIC;

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var list = Commands.literal("list").executes(c -> run(c, x -> S.tree().render(true, o -> S.isLoaded(o.getUuid())).forEach(l ->
                x.getSource().sendSuccess(() -> l, false))));

        var load = Commands.literal("load");
        selector(load, 0, o -> !S.isLoaded(o.getUuid()), n -> n.executes(c -> run(c, x -> {
            SpaceObject o = target(x, 0);
            S.load(o.getUuid());
            x.getSource().sendSuccess(() -> Component.translatable("amynnaspacemod.spaceobject.loaded", S.tree().addressOf(o)), true);
        })));

        var unload = Commands.literal("unload");
        selector(unload, 0, o -> S.isLoaded(o.getUuid()), n -> n
                .executes(c -> run(c, x -> unload(x, false)))
                .then(Commands.literal("recursive").executes(c -> run(c, x -> unload(x, true)))));

        d.register(Commands.literal("spaceload").requires(s -> s.hasPermission(2)).then(list).then(load).then(unload));
    }

    private static void unload(CommandContext<CommandSourceStack> x, boolean recursive) {
        SpaceObject o = target(x, 0);
        String addr = S.tree().addressOf(o);
        String key = S.unload(o.getUuid(), recursive) ? "unloaded" : "has_loaded_children";
        x.getSource().sendSuccess(() -> Component.translatable("amynnaspacemod.spaceobject." + key, addr), true);
    }
}
