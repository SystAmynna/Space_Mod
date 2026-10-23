package fr.systamynna.spacemod.network;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.ship.PilotManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Client vers serveur : commandes du pilote, quantifiées sur un octet signé ({@code valeur × 127}). Envoyé quand elles
 * changent (au plus tous les 2 ticks) et en battement de cœur ; le serveur borne tout et ignore qui n'est pas pilote.
 *
 * @param throttle poussée, −127 (S) à 127 (Z)
 * @param roll roulis, −127 (gauche) à 127 (droite)
 * @param yaw lacet du joystick
 * @param pitch tangage du joystick
 * @param flags {@link #RELEASE}, {@link #BRAKE}
 */
public record PilotInputPacket(byte throttle, byte roll, byte yaw, byte pitch, byte flags) implements CustomPacketPayload {
    /** Drapeau : le pilote quitte le pilotage (ECHAP). */
    public static final byte RELEASE = 1;
    /** Drapeau : frein. */
    public static final byte BRAKE = 2;

    /** Type du paquet. */
    public static final Type<PilotInputPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, "pilot_input"));
    /** Codec réseau. */
    public static final StreamCodec<FriendlyByteBuf, PilotInputPacket> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.throttle);
                buf.writeByte(p.roll);
                buf.writeByte(p.yaw);
                buf.writeByte(p.pitch);
                buf.writeByte(p.flags);
            },
            buf -> new PilotInputPacket(buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte()));

    /** Quantifie une valeur de [−1, 1] sur un octet. */
    public static byte quantize(double v) {
        return (byte) Math.round(Math.max(-1, Math.min(1, v)) * 127);
    }

    @Override
    public Type<PilotInputPacket> type() {
        return TYPE;
    }

    /** À brancher sur le bus du mod. */
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer player) PilotManager.onInput(player, p);
        });
    }
}
