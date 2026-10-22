package fr.systamynna.spacemod.network;

import java.util.ArrayList;
import java.util.List;

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
 * Serveur vers client, juste après {@link SystemSnapshotPacket} : le décor lointain du ciel. Les objets (autres galaxies,
 * étoiles voisines et leur système) sont au format {@code SpaceObject.save()} et s'ajoutent à la réplique du client.
 *
 * @param seed graine du motif de fond, commune à tous les joueurs de la dimension
 * @param objects objets, au format {@code SpaceObject.save()}
 */
public record SkyExtrasPacket(int seed, List<CompoundTag> objects) implements CustomPacketPayload {
    /** Type du paquet. */
    public static final Type<SkyExtrasPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, "sky_extras"));
    /** Codec réseau : les objets voyagent dans un seul tag de liste. */
    public static final StreamCodec<FriendlyByteBuf, SkyExtrasPacket> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.seed);
                ListTag list = new ListTag();
                list.addAll(p.objects);
                CompoundTag tag = new CompoundTag();
                tag.put("objects", list);
                buf.writeNbt(tag);
            },
            buf -> {
                int seed = buf.readInt();
                List<CompoundTag> objects = new ArrayList<>();
                CompoundTag tag = buf.readNbt();
                if (tag != null) for (Tag t : tag.getList("objects", Tag.TAG_COMPOUND)) objects.add((CompoundTag) t);
                return new SkyExtrasPacket(seed, objects);
            });

    @Override
    public Type<SkyExtrasPacket> type() {
        return TYPE;
    }

    /** À brancher sur le bus du mod. */
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (p, c) -> ClientSpace.addExtras(p));
    }
}
