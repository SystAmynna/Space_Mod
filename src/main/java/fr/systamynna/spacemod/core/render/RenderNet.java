package fr.systamynna.spacemod.core.render;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.client.render.ClientRenderNet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Réseau du rendu : config de ciel par dimension et assets (textures) répliqués une fois, en reliable. */
public final class RenderNet {
    private RenderNet() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, path));
    }

    /** S2C : config du ciel de la dimension du joueur ({@code config} absent = ciel vanilla). */
    public record Config(ResourceLocation dimension, @Nullable CompoundTag config) implements CustomPacketPayload {
        static final Type<Config> TYPE = typeOf("render_config");
        static final StreamCodec<FriendlyByteBuf, Config> CODEC = StreamCodec.of(
                (b, p) -> { b.writeResourceLocation(p.dimension); b.writeNullable(p.config, (buf, t) -> buf.writeNbt(t)); },
                b -> new Config(b.readResourceLocation(), b.readNullable(buf -> (CompoundTag) buf.readNbt())));
        @Override public Type<Config> type() { return TYPE; }
    }

    /** S2C : le client oriente sa caméra (yaw/pitch Minecraft, en degrés) sans que le joueur bouge ni soit téléporté. */
    public record Look(float yaw, float pitch) implements CustomPacketPayload {
        static final Type<Look> TYPE = typeOf("render_look");
        static final StreamCodec<FriendlyByteBuf, Look> CODEC = StreamCodec.of(
                (b, p) -> { b.writeFloat(p.yaw); b.writeFloat(p.pitch); }, b -> new Look(b.readFloat(), b.readFloat()));
        @Override public Type<Look> type() { return TYPE; }
    }

    /** C2S : le client veut {@code name} ; {@code knownHash} = celui qu'il possède déjà ("" sinon). */
    public record AssetRequest(String name, String knownHash) implements CustomPacketPayload {
        static final Type<AssetRequest> TYPE = typeOf("render_asset_request");
        static final StreamCodec<FriendlyByteBuf, AssetRequest> CODEC = StreamCodec.of(
                (b, p) -> { b.writeUtf(p.name, 256); b.writeUtf(p.knownHash, 64); },
                b -> new AssetRequest(b.readUtf(256), b.readUtf(64)));
        @Override public Type<AssetRequest> type() { return TYPE; }
    }

    /** S2C : contenu d'un asset (envoyé seulement si son hash diffère de celui du client). */
    public record AssetData(String name, String hash, byte[] bytes) implements CustomPacketPayload {
        static final Type<AssetData> TYPE = typeOf("render_asset_data");
        static final StreamCodec<FriendlyByteBuf, AssetData> CODEC = StreamCodec.of(
                (b, p) -> { b.writeUtf(p.name, 256); b.writeUtf(p.hash, 64); b.writeByteArray(p.bytes); },
                b -> new AssetData(b.readUtf(256), b.readUtf(64), b.readByteArray(RenderAssets.MAX_BYTES)));
        @Override public Type<AssetData> type() { return TYPE; }
    }

    /** À brancher sur le bus du mod. */
    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1").optional();
        r.playToClient(Config.TYPE, Config.CODEC, ClientRenderNet::onConfig);
        r.playToClient(Look.TYPE, Look.CODEC, ClientRenderNet::onLook);
        r.playToClient(AssetData.TYPE, AssetData.CODEC, ClientRenderNet::onAsset);
        r.playToServer(AssetRequest.TYPE, AssetRequest.CODEC, RenderNet::onRequest);
    }

    private static void onRequest(AssetRequest r, IPayloadContext ctx) {
        RenderAssets.Asset a = AmynnaSpaceMod.RENDER.assets().read(r.name());
        if (a != null && !a.hash().equals(r.knownHash())) PacketDistributor.sendToPlayer((ServerPlayer) ctx.player(), new AssetData(r.name(), a.hash(), a.bytes()));
    }
}
