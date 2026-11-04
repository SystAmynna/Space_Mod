package fr.systamynna.spacemod.commands;

import com.mojang.brigadier.CommandDispatcher;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.render.RenderConfig;
import fr.systamynna.spacemod.core.render.RenderNet;
import fr.systamynna.spacemod.core.render.RenderMath;
import fr.systamynna.spacemod.core.render.RenderTransform;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** {@code /spacelookat <spaceobject>} : oriente la caméra du joueur (sans le téléporter) vers l'objet, vu du point de vue du ciel spatial de sa dimension (OP). */
public final class SpaceLookAtCommand {
    private SpaceLookAtCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var root = Commands.literal("spacelookat").requires(s -> s.hasPermission(2));
        SpaceObjectArg.selector(root, 0, end -> end.executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            return SpaceObjectArg.run(c, x -> lookAt(x.getSource(), p, SpaceObjectArg.target(x, 0)));
        }));
        d.register(root);
    }

    private static void lookAt(CommandSourceStack src, ServerPlayer p, SpaceObject target) {
        var layer = AmynnaSpaceMod.STRATEGIC;
        RenderConfig cfg = AmynnaSpaceMod.RENDER.get(p.level().dimension().location());
        if (cfg == null) throw new SpaceObjectException("amynnaspacemod.lookat.no_sky", p.level().dimension().location().toString());
        var ob = RenderMath.observer(layer::getOrThrow, layer.getOrThrow(cfg.object), cfg);
        var placed = RenderMath.place(layer::getOrThrow, ob, target, new RenderTransform(), 0);
        float[] a = placed == null ? null : RenderMath.lookAngles(placed.pos());
        if (a == null) throw new SpaceObjectException("amynnaspacemod.lookat.unreachable", target.getName());
        // Paquet dédié : orienter la caméra côté client, sans téléportation (la position et la vitesse du joueur ne sont pas touchées).
        PacketDistributor.sendToPlayer(p, new RenderNet.Look(a[0], a[1]));
        src.sendSuccess(() -> Component.translatable("amynnaspacemod.lookat.done", target.getName()), false);
    }
}
