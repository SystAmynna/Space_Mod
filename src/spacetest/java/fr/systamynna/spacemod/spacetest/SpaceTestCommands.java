package fr.systamynna.spacemod.spacetest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class SpaceTestCommands {
    private SpaceTestCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("spacetest").requires(s -> s.hasPermission(2) || Boolean.getBoolean("spacetest.open"))
            .then(Commands.literal("a8")
                .then(Commands.literal("setup").executes(c -> msg(c.getSource(), A8Server.setup(c.getSource().getPlayerOrException()))))
                .then(Commands.literal("status").executes(c -> msg(c.getSource(), A8Server.status(c.getSource().getServer()))))
                .then(Commands.literal("go").then(Commands.argument("mode", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(c -> msg(c.getSource(), A8Server.go(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "mode"), c.getSource().getPlayerOrException()))))))
            .then(Commands.literal("a7").then(Commands.literal("setup").executes(c -> msg(c.getSource(), A7Server.setup(c.getSource().getPlayerOrException())))))
            .then(Commands.literal("a3")
                .then(Commands.literal("setup").executes(c -> msg(c.getSource(), A3Server.setup(c.getSource().getPlayerOrException()))))
                .then(Commands.literal("move").then(Commands.argument("vx", DoubleArgumentType.doubleArg())
                    .executes(c -> msg(c.getSource(), A3Server.move(DoubleArgumentType.getDouble(c, "vx"))))))
                .then(Commands.literal("stop").executes(c -> msg(c.getSource(), A3Server.stop())))
                .then(Commands.literal("tps")
                    .then(Commands.argument("dx", DoubleArgumentType.doubleArg()).then(Commands.argument("dy", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("dz", DoubleArgumentType.doubleArg()).executes(c -> msg(c.getSource(),
                            A3Server.teleportBySerialization(DoubleArgumentType.getDouble(c, "dx"), DoubleArgumentType.getDouble(c, "dy"),
                                DoubleArgumentType.getDouble(c, "dz"), c.getSource().getPlayerOrException())))))))
                .then(Commands.literal("tp")
                    .then(Commands.argument("dx", DoubleArgumentType.doubleArg()).then(Commands.argument("dy", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("dz", DoubleArgumentType.doubleArg())
                            .executes(c -> tp(c, false))
                            .then(Commands.literal("reset").executes(c -> tp(c, true)))))))
                .then(Commands.literal("batch")
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 100)).then(Commands.argument("interval_ticks", IntegerArgumentType.integer(1, 1200))
                        .then(Commands.argument("dx", DoubleArgumentType.doubleArg()).executes(c -> msg(c.getSource(),
                            A3Server.batch(IntegerArgumentType.getInteger(c, "count"), IntegerArgumentType.getInteger(c, "interval_ticks"),
                                DoubleArgumentType.getDouble(c, "dx"), c.getSource().getPlayerOrException()))))))))
            .then(Commands.literal("a1")
                .then(Commands.literal("run")
                    .executes(c -> run(c.getSource(), 10))
                    .then(Commands.argument("cycles", IntegerArgumentType.integer(1, 100))
                        .executes(c -> run(c.getSource(), IntegerArgumentType.getInteger(c, "cycles")))))
                .then(Commands.literal("status").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal(A1Runner.status()), false);
                    return 1;
                }))
                .then(Commands.literal("abort").executes(c -> {
                    A1Runner.abort();
                    c.getSource().sendSuccess(() -> Component.literal("A1 abandonné"), true);
                    return 1;
                }))));
    }

    private static int tp(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, boolean reset) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return msg(c.getSource(), A3Server.teleport(DoubleArgumentType.getDouble(c, "dx"), DoubleArgumentType.getDouble(c, "dy"),
            DoubleArgumentType.getDouble(c, "dz"), reset, c.getSource().getPlayerOrException()));
    }

    private static int msg(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int run(CommandSourceStack src, int cycles) {
        String err = A1Runner.start(src.getServer(), cycles);
        if (err != null) {
            src.sendFailure(Component.literal(err));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("A1 lancé : " + cycles + " cycle(s), voir test-results/A1/a1-run.log"), true);
        return 1;
    }
}
