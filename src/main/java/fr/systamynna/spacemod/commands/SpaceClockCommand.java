package fr.systamynna.spacemod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;

import fr.systamynna.spacemod.core.clock.ClockNet;
import fr.systamynna.spacemod.core.clock.SpaceClock;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** {@code /spaceclock multiplier [valeur] | get | getall} (OP). */
public final class SpaceClockCommand {
    private SpaceClockCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("spaceclock").requires(s -> s.hasPermission(2))
                .then(Commands.literal("multiplier")
                        .executes(c -> {
                            c.getSource().sendSuccess(() -> Component.translatable("amynnaspacemod.clock.multiplier.get",
                                    SpaceClock.SERVER.multiplier()), false);
                            return 1;
                        })
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0)).executes(c -> {
                            double v = DoubleArgumentType.getDouble(c, "value");
                            try {
                                SpaceClock.SERVER.setMultiplier(v);
                            } catch (IllegalArgumentException e) {
                                c.getSource().sendFailure(Component.translatable("amynnaspacemod.clock.multiplier.invalid"));
                                return 0;
                            }
                            PacketDistributor.sendToAllPlayers(ClockNet.currentSync());
                            c.getSource().sendSuccess(() -> Component.translatable("amynnaspacemod.clock.multiplier.set", v), true);
                            return 1;
                        })))
                .then(Commands.literal("get").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    ClockNet.sendProbe(p, p.getUUID());
                    return 1;
                }))
                .then(Commands.literal("getall").executes(c -> {
                    var src = c.getSource();
                    var requester = src.getEntity() instanceof ServerPlayer p ? p.getUUID() : ClockNet.CONSOLE;
                    var players = src.getServer().getPlayerList().getPlayers();
                    players.forEach(t -> ClockNet.sendProbe(t, requester));
                    return players.size();
                })));
    }
}
