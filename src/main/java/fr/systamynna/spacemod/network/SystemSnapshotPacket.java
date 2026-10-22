package fr.systamynna.spacemod.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.client.ClientSpace;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Serveur vers client, juste après {@link SkyContextPacket} : de quoi dessiner le système de l'hôte. Les objets sont
 * envoyés tels que {@code SpaceObject.save()} les écrit (ancêtres de l'hôte et descendants de son système), avec l'ancre
 * de l'horloge spatiale ; aucun delta, un nouvel envoi remplace le précédent.
 *
 * @param hostId UUID de l'objet hôte
 * @param anchorGameTick temps de jeu de l'ancre de l'horloge spatiale
 * @param anchorSpaceTicks temps spatial à l'ancre
 * @param rate ticks spatiaux par tick de jeu
 * @param objects objets, au format {@code SpaceObject.save()}
 */
public record SystemSnapshotPacket(UUID hostId, long anchorGameTick, double anchorSpaceTicks, double rate,
        List<CompoundTag> objects) implements CustomPacketPayload {
    /** Type du paquet. */
    public static final Type<SystemSnapshotPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, "system_snapshot"));
    /** Codec réseau : les objets voyagent dans un seul tag de liste. */
    public static final StreamCodec<FriendlyByteBuf, SystemSnapshotPacket> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUUID(p.hostId);
                buf.writeLong(p.anchorGameTick);
                buf.writeDouble(p.anchorSpaceTicks);
                buf.writeDouble(p.rate);
                ListTag list = new ListTag();
                list.addAll(p.objects);
                CompoundTag tag = new CompoundTag();
                tag.put("objects", list);
                buf.writeNbt(tag);
            },
            buf -> {
                UUID host = buf.readUUID();
                long anchorGame = buf.readLong();
                double anchorSpace = buf.readDouble();
                double rate = buf.readDouble();
                List<CompoundTag> objects = new ArrayList<>();
                CompoundTag tag = buf.readNbt();
                if (tag != null) for (Tag t : tag.getList("objects", Tag.TAG_COMPOUND)) objects.add((CompoundTag) t);
                return new SystemSnapshotPacket(host, anchorGame, anchorSpace, rate, objects);
            });

    @Override
    public Type<SystemSnapshotPacket> type() {
        return TYPE;
    }

    /** À brancher sur le bus du mod. */
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (p, c) -> ClientSpace.set(p));
    }
}
