package fr.systamynna.spacemod.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Abonnements des joueurs (côté serveur) : un objet abonné est loadé sur le serveur et poussé au client à chaque changement.
 * Persistés dans le store ; un unload garde l'abonnement (le client reçoit un {@code Drop}, l'objet est renvoyé au reload).
 */
public final class Subscriptions implements SpaceListener {
    private final StrategicLayer layer;
    private MinecraftServer server;
    /** Joueurs en ligne → leurs abonnements. */
    private final Map<UUID, Set<UUID>> byPlayer = new HashMap<>();
    /** Objet → joueurs en ligne abonnés. */
    private final Map<UUID, Set<UUID>> byObject = new HashMap<>();

    public Subscriptions(StrategicLayer layer) { this.layer = layer; }

    public void start(MinecraftServer server) {
        this.server = server;
        layer.setListener(this);
    }

    public void stop() {
        layer.setListener(SpaceListener.NONE);
        byPlayer.clear();
        byObject.clear();
        server = null;
    }

    private Set<UUID> subs(ServerPlayer p) {
        return byPlayer.computeIfAbsent(p.getUUID(), id -> {
            Set<UUID> s = new HashSet<>(layer.store().loadSubs(id));
            s.forEach(o -> byObject.computeIfAbsent(o, k -> new HashSet<>()).add(id));
            return s;
        });
    }

    public List<UUID> subscriptions(ServerPlayer p) { return List.copyOf(subs(p)); }

    /** Connexion : repart d'une couche cliente vide, puis envoie les abonnements actuellement loadés. */
    public void login(ServerPlayer p) {
        PacketDistributor.sendToPlayer(p, new SpaceNet.Reset());
        for (UUID o : subs(p)) {
            SpaceObject so = layer.loadedOrNull(o);
            if (so != null) PacketDistributor.sendToPlayer(p, SpaceNet.upsert(so));
        }
    }

    public void logout(ServerPlayer p) {
        Set<UUID> s = byPlayer.remove(p.getUUID());
        if (s != null) s.forEach(o -> unindex(o, p.getUUID()));
    }

    /** {@code o} doit être loadé. */
    public void subscribe(ServerPlayer p, SpaceObject o) {
        Set<UUID> next = new HashSet<>(subs(p));
        next.add(o.getUuid());
        apply(p, next);
    }

    public void unsubscribe(ServerPlayer p, UUID object) {
        Set<UUID> next = new HashSet<>(subs(p));
        next.remove(object);
        apply(p, next);
    }

    /** X, ses ancêtres et ses enfants s'ajoutent aux abonnements ({@code replace} : ils les remplacent). Tout est loadé. */
    public void setParent(ServerPlayer p, SpaceObject x, boolean replace) {
        Set<UUID> next = replace ? new HashSet<>() : new HashSet<>(subs(p));
        next.addAll(parentSet(layer, x));
        apply(p, next);
        layer.store().savePlayerParent(p.getUUID(), x.getUuid());
    }

    /** X + tous ses ancêtres + les autres enfants de X (frères du joueur), loadés au passage. */
    static Set<UUID> parentSet(StrategicLayer layer, SpaceObject x) {
        Set<UUID> out = new LinkedHashSet<>();
        for (SpaceObject c = x; c != null; c = c.getParent() == null ? null : layer.getOrThrow(c.getParent())) out.add(c.getUuid());
        for (SpaceObject k : layer.tree().children(x)) out.add(layer.getOrThrow(k.getUuid()).getUuid());
        return out;
    }

    /** Passe les abonnements du joueur à {@code next} : Drop des retirés, Upsert des nouveaux, persistance. */
    private void apply(ServerPlayer p, Set<UUID> next) {
        Set<UUID> cur = subs(p);
        for (UUID o : cur) if (!next.contains(o)) {
            unindex(o, p.getUUID());
            PacketDistributor.sendToPlayer(p, new SpaceNet.Drop(o));
        }
        for (UUID o : next) if (!cur.contains(o)) {
            byObject.computeIfAbsent(o, k -> new HashSet<>()).add(p.getUUID());
            PacketDistributor.sendToPlayer(p, SpaceNet.upsert(layer.getOrThrow(o)));
        }
        cur.clear();
        cur.addAll(next);
        layer.store().saveSubs(p.getUUID(), cur);
    }

    private void unindex(UUID object, UUID player) {
        Set<UUID> ps = byObject.get(object);
        if (ps != null && ps.remove(player) && ps.isEmpty()) byObject.remove(object);
    }

    private void sendToSubscribers(UUID object, net.minecraft.network.protocol.common.custom.CustomPacketPayload packet) {
        Set<UUID> ps = byObject.get(object);
        if (ps == null || server == null) return;
        for (UUID id : ps) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) PacketDistributor.sendToPlayer(p, packet);
        }
    }

    @Override
    public void updated(SpaceObject o) { if (byObject.containsKey(o.getUuid())) sendToSubscribers(o.getUuid(), SpaceNet.upsert(o)); }

    @Override
    public void dropped(UUID uuid) { sendToSubscribers(uuid, new SpaceNet.Drop(uuid)); }

    @Override
    public void removed(UUID uuid) {
        sendToSubscribers(uuid, new SpaceNet.Drop(uuid)); // le store a déjà purgé les lignes d'abonnement
        Set<UUID> ps = byObject.remove(uuid);
        if (ps != null) ps.forEach(id -> { Set<UUID> s = byPlayer.get(id); if (s != null) s.remove(uuid); });
    }

    /** Lignes du rapport {@code /spaceplayerstate}, statuts calculés par le client {@code from}. */
    public List<Component> stateLines(ServerPlayer from, List<UUID> ok, List<UUID> none) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("amynnaspacemod.player.state", from.getName(), ok.size() + none.size()));
        layer.store().loadPlayerParent(from.getUUID()).ifPresent(u -> out.add(Component.translatable("amynnaspacemod.player.state_parent", name(u))));
        for (UUID u : ok) out.add(Component.literal(" OK   " + name(u)).withStyle(ChatFormatting.GREEN));
        for (UUID u : none) out.add(Component.literal(" NONE " + name(u)).withStyle(ChatFormatting.RED));
        return out;
    }

    private String name(UUID u) {
        try {
            return layer.tree().addressOf(layer.peek(u));
        } catch (SpaceObjectException e) {
            return u.toString();
        }
    }
}
