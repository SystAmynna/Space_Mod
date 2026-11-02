package fr.systamynna.spacemod.core;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.client.ClientNet;
import fr.systamynna.spacemod.core.clock.ClockNet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Réseau des abonnements : le serveur pousse les objets auxquels le joueur est abonné vers sa {@link fr.systamynna.spacemod.client.ClientLayer}. */
public final class SpaceNet {
    private SpaceNet() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, path));
    }

    private static void writeUuids(FriendlyByteBuf b, List<UUID> l) { b.writeCollection(l, (buf, u) -> buf.writeUUID(u)); }

    private static List<UUID> readUuids(FriendlyByteBuf b) { return b.readList(buf -> buf.readUUID()); }

    /** S2C : le client vide sa couche (connexion). */
    public record Reset() implements CustomPacketPayload {
        static final Type<Reset> TYPE = typeOf("space_reset");
        static final StreamCodec<FriendlyByteBuf, Reset> CODEC = StreamCodec.unit(new Reset());
        @Override public Type<Reset> type() { return TYPE; }
    }

    /** S2C : état complet d'un objet ({@code data} = {@link SpaceObject#save()}). */
    public record Upsert(UUID uuid, @Nullable UUID parent, CompoundTag data) implements CustomPacketPayload {
        static final Type<Upsert> TYPE = typeOf("space_upsert");
        static final StreamCodec<FriendlyByteBuf, Upsert> CODEC = StreamCodec.of(
                (b, p) -> { b.writeUUID(p.uuid); b.writeNullable(p.parent, (buf, u) -> buf.writeUUID(u)); b.writeNbt(p.data); },
                b -> new Upsert(b.readUUID(), b.readNullable(buf -> buf.readUUID()), b.readNbt()));
        @Override public Type<Upsert> type() { return TYPE; }
    }

    /** S2C : le client oublie l'objet. */
    public record Drop(UUID uuid) implements CustomPacketPayload {
        static final Type<Drop> TYPE = typeOf("space_drop");
        static final StreamCodec<FriendlyByteBuf, Drop> CODEC = StreamCodec.of((b, p) -> b.writeUUID(p.uuid), b -> new Drop(b.readUUID()));
        @Override public Type<Drop> type() { return TYPE; }
    }

    /** S2C : le client dit lequel de ces objets il possède, et le rapporte à {@code requester}. */
    public record StateProbe(UUID requester, List<UUID> subs) implements CustomPacketPayload {
        static final Type<StateProbe> TYPE = typeOf("space_state_probe");
        static final StreamCodec<FriendlyByteBuf, StateProbe> CODEC = StreamCodec.of(
                (b, p) -> { b.writeUUID(p.requester); writeUuids(b, p.subs); },
                b -> new StateProbe(b.readUUID(), readUuids(b)));
        @Override public Type<StateProbe> type() { return TYPE; }
    }

    /** C2S : statut calculé par le client pour chaque objet sondé. */
    public record StateReport(UUID requester, List<UUID> ok, List<UUID> none) implements CustomPacketPayload {
        static final Type<StateReport> TYPE = typeOf("space_state_report");
        static final StreamCodec<FriendlyByteBuf, StateReport> CODEC = StreamCodec.of(
                (b, p) -> { b.writeUUID(p.requester); writeUuids(b, p.ok); writeUuids(b, p.none); },
                b -> new StateReport(b.readUUID(), readUuids(b), readUuids(b)));
        @Override public Type<StateReport> type() { return TYPE; }
    }

    public static Upsert upsert(SpaceObject o) { return new Upsert(o.getUuid(), o.getParent(), o.save()); }

    /** À brancher sur le bus du mod. */
    public static void register(RegisterPayloadHandlersEvent event) {
        // Thread principal (défaut) : les couches ne sont pas thread-safe.
        var r = event.registrar("1").optional();
        r.playToClient(Reset.TYPE, Reset.CODEC, ClientNet::reset);
        r.playToClient(Upsert.TYPE, Upsert.CODEC, ClientNet::upsert);
        r.playToClient(Drop.TYPE, Drop.CODEC, ClientNet::drop);
        r.playToClient(StateProbe.TYPE, StateProbe.CODEC, ClientNet::probe);
        r.playToServer(StateReport.TYPE, StateReport.CODEC, SpaceNet::onReport);
    }

    // shortcut: le requester du rapport n'est pas authentifié (comme ClockNet.onReport), à revoir si gênant.
    private static void onReport(StateReport r, IPayloadContext ctx) {
        var from = (ServerPlayer) ctx.player();
        var lines = AmynnaSpaceMod.SUBS.stateLines(from, r.ok, r.none);
        if (r.requester.equals(ClockNet.CONSOLE)) lines.forEach(from.server::sendSystemMessage);
        else {
            ServerPlayer to = from.server.getPlayerList().getPlayer(r.requester);
            if (to != null) lines.forEach(to::sendSystemMessage);
        }
    }
}
