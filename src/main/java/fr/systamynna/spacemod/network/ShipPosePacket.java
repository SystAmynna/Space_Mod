package fr.systamynna.spacemod.network;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.client.ClientSpace;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Serveur vers client : position, orientation et vitesse d'un vaisseau, dans le référentiel de son parent. Le client
 * extrapole la position avec la vitesse jusqu'au paquet suivant.
 *
 * @param id vaisseau
 * @param parent parent actuel (change quand le vaisseau adopte un autre astre)
 * @param tx translation
 * @param ty idem
 * @param tz idem
 * @param qx quaternion d'orientation
 * @param qy idem
 * @param qz idem
 * @param qw idem
 * @param vx vitesse, en unités du parent par tick
 * @param vy idem
 * @param vz idem
 * @param gameTick temps de jeu auquel la pose a été prise
 */
public record ShipPosePacket(UUID id, @Nullable UUID parent, double tx, double ty, double tz,
        double qx, double qy, double qz, double qw, double vx, double vy, double vz, long gameTick) implements CustomPacketPayload {
    /** Type du paquet. */
    public static final Type<ShipPosePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, "ship_pose"));
    /** Codec réseau. */
    public static final StreamCodec<FriendlyByteBuf, ShipPosePacket> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUUID(p.id);
                buf.writeBoolean(p.parent != null);
                if (p.parent != null) buf.writeUUID(p.parent);
                for (double d : new double[] { p.tx, p.ty, p.tz, p.qx, p.qy, p.qz, p.qw, p.vx, p.vy, p.vz }) buf.writeDouble(d);
                buf.writeLong(p.gameTick);
            },
            buf -> {
                UUID id = buf.readUUID();
                UUID parent = buf.readBoolean() ? buf.readUUID() : null;
                return new ShipPosePacket(id, parent, buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readLong());
            });

    @Override
    public Type<ShipPosePacket> type() {
        return TYPE;
    }

    /** À brancher sur le bus du mod. */
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (p, c) -> ClientSpace.applyPose(p));
    }
}
