package fr.systamynna.spacemod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.render.RenderConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** {@code /spacerender <dimension> object|transform|cancel} : ciel spatial d'une dimension (OP). */
public final class SpaceRenderCommand {
    private SpaceRenderCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var object = Commands.literal("object");
        SpaceObjectArg.selector(object, 0, end -> end.executes(c -> {
            ServerLevel l = DimensionArgument.getDimension(c, "dimension");
            return SpaceObjectArg.run(c, x -> {
            SpaceObject o = SpaceObjectArg.target(x, 0);
            AmynnaSpaceMod.RENDER.setObject(l.dimension().location(), o.getUuid());
            AmynnaSpaceMod.RENDER.broadcast(l);
            x.getSource().sendSuccess(() -> Component.translatable("amynnaspacemod.render.object", l.dimension().location().toString(), o.getName()), true);
        });
        }));
        var transform = Commands.literal("transform").then(Commands.argument("args", StringArgumentType.greedyString())
                .suggests((c, b) -> {
                    String rem = b.getRemaining();
                    return SharedSuggestionProvider.suggest(RenderConfig.suggest(rem), b.createOffset(b.getStart() + rem.lastIndexOf(' ') + 1));
                })
                .executes(c -> {
                    ServerLevel l = DimensionArgument.getDimension(c, "dimension");
                    return SpaceObjectArg.run(c, x -> {
                    RenderConfig cfg = AmynnaSpaceMod.RENDER.get(l.dimension().location());
                    if (cfg == null) throw new SpaceObjectException("amynnaspacemod.render.no_object", l.dimension().location().toString());
                    cfg.editAll(StringArgumentType.getString(x, "args"));
                    AmynnaSpaceMod.RENDER.markDirty();
                    AmynnaSpaceMod.RENDER.broadcast(l);
                    x.getSource().sendSuccess(() -> Component.translatable("amynnaspacemod.render.transform", l.dimension().location().toString(), cfg.describe()), true);
                    });
                }));
        var cancel = Commands.literal("cancel").executes(c -> {
            ServerLevel l = DimensionArgument.getDimension(c, "dimension");
            return SpaceObjectArg.run(c, x -> {
            AmynnaSpaceMod.RENDER.remove(l.dimension().location());
            AmynnaSpaceMod.RENDER.broadcast(l);
            x.getSource().sendSuccess(() -> Component.translatable("amynnaspacemod.render.cancel", l.dimension().location().toString()), true);
            });
        });
        d.register(Commands.literal("spacerender").requires(s -> s.hasPermission(2))
                .then(Commands.argument("dimension", DimensionArgument.dimension()).then(object).then(transform).then(cancel)));
    }
}
