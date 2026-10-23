package fr.systamynna.spacemod.network;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.client.ClientKnowledgeState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Serveur vers client : l'état « connaissance de l'espace » du joueur.
 *
 * @param on {@code true} si le client doit dessiner les repères
 */
public record KnowledgeStatePacket(boolean on) implements CustomPacketPayload {
    /** Type du paquet. */
    public static final Type<KnowledgeStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, "knowledge_state"));
    /** Codec réseau. */
    public static final StreamCodec<FriendlyByteBuf, KnowledgeStatePacket> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeBoolean(p.on), buf -> new KnowledgeStatePacket(buf.readBoolean()));

    @Override
    public Type<KnowledgeStatePacket> type() {
        return TYPE;
    }

    /** À brancher sur le bus du mod. */
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (p, c) -> ClientKnowledgeState.set(p.on()));
    }
}
