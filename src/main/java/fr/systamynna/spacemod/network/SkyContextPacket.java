package fr.systamynna.spacemod.network;

import java.util.UUID;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.client.SkyContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Serveur vers client, à l'entrée dans la dimension d'un objet : de quoi calibrer le ciel.
 *
 * @param objectId UUID de l'objet hôte
 * @param name nom affiché de l'objet
 * @param sky rendu de ciel ({@code SpaceRender})
 * @param radius rayon apparent de l'objet, 0 sans {@code Render}
 * @param port nom du port d'arrivée
 * @param x position du port dans le repère local de l'objet
 * @param y idem
 * @param z idem
 * @param qx rotation du ciel (placement de la dimension composé avec celle du port), quaternion x
 * @param qy idem
 * @param qz idem
 * @param qw idem
 */
public record SkyContextPacket(UUID objectId, String name, ResourceLocation sky, double radius,
        String port, double x, double y, double z,
        double qx, double qy, double qz, double qw) implements CustomPacketPayload {
    /** Type du paquet. */
    public static final Type<SkyContextPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, "sky_context"));
    /** Codec réseau. */
    public static final StreamCodec<FriendlyByteBuf, SkyContextPacket> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUUID(p.objectId);
                buf.writeUtf(p.name);
                buf.writeResourceLocation(p.sky);
                buf.writeDouble(p.radius);
                buf.writeUtf(p.port);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeDouble(p.qx);
                buf.writeDouble(p.qy);
                buf.writeDouble(p.qz);
                buf.writeDouble(p.qw);
            },
            buf -> new SkyContextPacket(buf.readUUID(), buf.readUtf(), buf.readResourceLocation(),
                    buf.readDouble(), buf.readUtf(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

    @Override
    public Type<SkyContextPacket> type() {
        return TYPE;
    }

    /** À brancher sur le bus du mod. */
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (p, c) -> SkyContext.set(p));
    }
}
