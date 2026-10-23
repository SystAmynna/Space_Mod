package fr.systamynna.spacemod.network;

import java.util.UUID;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.client.ClientPilotState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Serveur vers client : le joueur devient (ou cesse d'être) pilote d'un vaisseau. Le client capture alors ses entrées.
 *
 * @param piloting {@code true} au début du pilotage
 * @param ship vaisseau piloté
 */
public record PilotStatePacket(boolean piloting, UUID ship) implements CustomPacketPayload {
    /** Type du paquet. */
    public static final Type<PilotStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, "pilot_state"));
    /** Codec réseau. */
    public static final StreamCodec<FriendlyByteBuf, PilotStatePacket> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.piloting);
                buf.writeUUID(p.ship);
            },
            buf -> new PilotStatePacket(buf.readBoolean(), buf.readUUID()));

    @Override
    public Type<PilotStatePacket> type() {
        return TYPE;
    }

    /** À brancher sur le bus du mod. */
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (p, c) -> ClientPilotState.set(p.piloting(), p.ship()));
    }
}
