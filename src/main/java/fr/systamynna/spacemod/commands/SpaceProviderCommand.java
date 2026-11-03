package fr.systamynna.spacemod.commands;

import static fr.systamynna.spacemod.commands.SpaceObjectArg.run;

import java.util.Arrays;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.StrategicLayer;
import fr.systamynna.spacemod.core.provider.ProviderType;
import fr.systamynna.spacemod.core.provider.Providers;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/** {@code /spaceprovider | set <provider> | refresh} (OP). */
public final class SpaceProviderCommand {
    private SpaceProviderCommand() {}

    private static final StrategicLayer S = AmynnaSpaceMod.STRATEGIC;

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("spaceprovider").requires(s -> s.hasPermission(2))
                .executes(c -> run(c, x -> x.getSource().sendSuccess(
                        () -> Component.translatable("amynnaspacemod.provider.current", S.getProvider().name()), false)))
                .then(Commands.literal("set").then(Commands.argument("provider", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(ProviderType.values()).map(ProviderType::name), b))
                        .executes(c -> run(c, x -> {
                            String name = StringArgumentType.getString(x, "provider");
                            ProviderType p;
                            try {
                                p = ProviderType.valueOf(name.toUpperCase(Locale.ROOT));
                            } catch (IllegalArgumentException e) {
                                throw new SpaceObjectException("amynnaspacemod.provider.unknown", name);
                            }
                            S.setProvider(p);
                            x.getSource().sendSuccess(() -> Component.translatable("amynnaspacemod.provider.set", p.name()), true);
                        }))))
                .then(Commands.literal("refresh").executes(c -> run(c, x -> {
                    ProviderType p = S.getProvider();
                    var r = Providers.refresh(S, p);
                    var src = x.getSource();
                    src.sendSuccess(() -> Component.translatable("amynnaspacemod.provider.refresh.summary", p.name(),
                            r.created().size(), r.updated().size(), r.unchanged(), r.errors().size()), true);
                    r.created().forEach(a -> src.sendSuccess(() -> Component.translatable("amynnaspacemod.provider.refresh.created", a), false));
                    r.updated().forEach(a -> src.sendSuccess(() -> Component.translatable("amynnaspacemod.provider.refresh.updated", a), false));
                    r.errors().forEach(e -> src.sendSuccess(() -> Component.translatable(e.getMessage(), e.args()).withStyle(ChatFormatting.RED), false));
                }))));
    }
}
