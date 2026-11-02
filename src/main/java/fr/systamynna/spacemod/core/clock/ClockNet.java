package fr.systamynna.spacemod.core.clock;

import java.util.UUID;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;

/** Réseau de l'horloge : synchro d'état, ping/pong de mesure de latence, et protocole de vérification (get/getall). */
public final class ClockNet {
    /** Requester des pings de synchro (pas de rapport). */
    public static final UUID NONE = new UUID(0, 0);
    /** Requester = console du serveur. */
    public static final UUID CONSOLE = new UUID(0, 1);
    private static final int SYNC_PINGS = 4;

    private ClockNet() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, path));
    }

    /** S2C : segment courant de l'horloge serveur. */
    public record Sync(double baseTime, long baseNanos, double multiplier) implements CustomPacketPayload {
        static final Type<Sync> TYPE = typeOf("clock_sync");
        static final StreamCodec<FriendlyByteBuf, Sync> CODEC = StreamCodec.of(
                (b, p) -> { b.writeDouble(p.baseTime); b.writeLong(p.baseNanos); b.writeDouble(p.multiplier); },
                b -> new Sync(b.readDouble(), b.readLong(), b.readDouble()));
        @Override public Type<Sync> type() { return TYPE; }
    }

    /** C2S : {@code local} = temps client au moment de la demande (inutilisé si requester = NONE). */
    public record Ping(long clientNanos, UUID requester, double local) implements CustomPacketPayload {
        static final Type<Ping> TYPE = typeOf("clock_ping");
        static final StreamCodec<FriendlyByteBuf, Ping> CODEC = StreamCodec.of(
                (b, p) -> { b.writeLong(p.clientNanos); b.writeUUID(p.requester); b.writeDouble(p.local); },
                b -> new Ping(b.readLong(), b.readUUID(), b.readDouble()));
        @Override public Type<Ping> type() { return TYPE; }
    }

    /** S2C : écho du ping + nanoTime et temps d'horloge du serveur au moment de la réponse. */
    public record Pong(Ping ping, long serverNanos, double serverTime) implements CustomPacketPayload {
        static final Type<Pong> TYPE = typeOf("clock_pong");
        static final StreamCodec<FriendlyByteBuf, Pong> CODEC = StreamCodec.of(
                (b, p) -> { Ping.CODEC.encode(b, p.ping); b.writeLong(p.serverNanos); b.writeDouble(p.serverTime); },
                b -> new Pong(Ping.CODEC.decode(b), b.readLong(), b.readDouble()));
        @Override public Type<Pong> type() { return TYPE; }
    }

    /** S2C : le client doit mesurer sa synchro et la rapporter à {@code requester}. */
    public record Probe(UUID requester) implements CustomPacketPayload {
        static final Type<Probe> TYPE = typeOf("clock_probe");
        static final StreamCodec<FriendlyByteBuf, Probe> CODEC = StreamCodec.of(
                (b, p) -> b.writeUUID(p.requester), b -> new Probe(b.readUUID()));
        @Override public Type<Probe> type() { return TYPE; }
    }

    /** C2S : résultat d'une vérification ({@code delta} = corrigé - temps client à la réception). */
    public record Report(UUID requester, double local, double serverRaw, double rttMs, double corrected, double delta)
            implements CustomPacketPayload {
        static final Type<Report> TYPE = typeOf("clock_report");
        static final StreamCodec<FriendlyByteBuf, Report> CODEC = StreamCodec.of(
                (b, p) -> { b.writeUUID(p.requester); b.writeDouble(p.local); b.writeDouble(p.serverRaw);
                    b.writeDouble(p.rttMs); b.writeDouble(p.corrected); b.writeDouble(p.delta); },
                b -> new Report(b.readUUID(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble()));
        @Override public Type<Report> type() { return TYPE; }
    }

    /** À brancher sur le bus du mod. */
    public static void register(RegisterPayloadHandlersEvent event) {
        // Thread réseau : pas d'attente du tick, sinon la latence mesurée inclurait le délai du thread principal.
        var r = event.registrar("1").optional().executesOn(HandlerThread.NETWORK);
        r.playToClient(Sync.TYPE, Sync.CODEC, ClockNet::onSync);
        r.playToServer(Ping.TYPE, Ping.CODEC, ClockNet::onPing);
        r.playToClient(Pong.TYPE, Pong.CODEC, ClockNet::onPong);
        r.playToClient(Probe.TYPE, Probe.CODEC, ClockNet::onProbe);
        r.playToServer(Report.TYPE, Report.CODEC, ClockNet::onReport);
    }

    public static Sync currentSync() {
        var s = SpaceClock.SERVER.state();
        return new Sync(s.baseTime(), s.baseNanos(), s.multiplier());
    }

    public static void sendProbe(ServerPlayer target, UUID requester) {
        PacketDistributor.sendToPlayer(target, new Probe(requester));
    }

    // --- client ---

    private static void onSync(Sync p, IPayloadContext ctx) {
        SpaceClock.CLIENT.applySync(new SpaceClock.State(p.baseTime, p.baseNanos, p.multiplier));
        for (int i = 0; i < SYNC_PINGS; i++) ctx.reply(new Ping(System.nanoTime(), NONE, 0));
    }

    private static void onProbe(Probe p, IPayloadContext ctx) {
        ctx.reply(new Ping(System.nanoTime(), p.requester, SpaceClock.CLIENT.now()));
    }

    private static void onPong(Pong p, IPayloadContext ctx) {
        long t2 = System.nanoTime();
        SpaceClock.CLIENT.applyPong(p.ping.clientNanos, p.serverNanos, t2);
        if (p.ping.requester.equals(NONE)) return;
        double rttMs = (t2 - p.ping.clientNanos) / 1e6;
        double corrected = p.serverTime + rttMs / 2 * SpaceClock.CLIENT.multiplier();
        ctx.reply(new Report(p.ping.requester, p.ping.local, p.serverTime, rttMs, corrected, corrected - SpaceClock.CLIENT.now()));
    }

    // --- serveur ---

    private static void onPing(Ping p, IPayloadContext ctx) {
        ctx.reply(new Pong(p, System.nanoTime(), SpaceClock.SERVER.now()));
    }

    // shortcut: le requester du rapport n'est pas authentifié (un client modifié peut envoyer des rapports à un OP), à revoir si gênant.
    private static void onReport(Report r, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var server = ((ServerPlayer) ctx.player()).server;
            Component msg = Component.translatable("amynnaspacemod.clock.report", ctx.player().getName(),
                    f(r.local), f(r.serverRaw), f(r.rttMs), f(r.corrected), f(r.delta));
            if (r.requester.equals(CONSOLE)) server.sendSystemMessage(msg);
            else {
                ServerPlayer to = server.getPlayerList().getPlayer(r.requester);
                if (to != null) to.sendSystemMessage(msg);
            }
        });
    }

    private static String f(double v) {
        return String.format("%.2f", v);
    }
}
