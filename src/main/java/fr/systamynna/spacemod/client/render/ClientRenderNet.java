package fr.systamynna.spacemod.client.render;

import fr.systamynna.spacemod.core.render.RenderConfig;
import fr.systamynna.spacemod.core.render.RenderNet;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Handlers côté client de {@link RenderNet}. */
public final class ClientRenderNet {
    private ClientRenderNet() {}

    public static void onConfig(RenderNet.Config p, IPayloadContext ctx) {
        RenderConfig c = p.config() == null ? null : RenderConfig.load(p.config());
        if (c != null) {
            ClientRenderState.set(c);
            AssetCache.refresh();
        } else if (ClientRenderState.forDimension(p.dimension()) != null || p.config() == null) {
            ClientRenderState.set(null);
        }
    }

    /** Rotation pure de la caméra : ni position ni vitesse ne changent ; le client renvoie sa nouvelle orientation au serveur avec ses paquets de mouvement. */
    public static void onLook(RenderNet.Look p, IPayloadContext ctx) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null || !Float.isFinite(p.yaw() + p.pitch())) return;
        float pitch = net.minecraft.util.Mth.clamp(p.pitch(), -90, 90);
        player.setYRot(p.yaw());
        player.setXRot(pitch);
        player.yRotO = p.yaw(); // pas d'interpolation depuis l'ancienne orientation
        player.xRotO = pitch;
        player.setYHeadRot(p.yaw());
    }

    public static void onAsset(RenderNet.AssetData p, IPayloadContext ctx) { AssetCache.onData(p); }
}
