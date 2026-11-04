package fr.systamynna.spacemod.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Abonnements des joueurs (côté serveur) : un objet abonné est loadé sur le serveur et poussé au client à chaque changement.
 * Deux sources : les abonnements manuels (commandes), persistés dans le store, et les abonnements automatiques calculés par
 * {@link fr.systamynna.spacemod.core.relevance.Relevance}, jamais persistés (recalculés à la connexion). Un joueur est répliqué sur l'union.
 * Un unload garde l'abonnement (le client reçoit un {@code Drop}, l'objet est renvoyé au reload).
 */
public final class Subscriptions implements SpaceListener {
    private final StrategicLayer layer;
    private MinecraftServer server;
    /** Joueurs en ligne → leurs abonnements répliqués (manuels + automatiques). */
    private final Map<UUID, Set<UUID>> byPlayer = new HashMap<>();
    /** Joueurs en ligne → abonnements manuels (ceux du store). */
    private final Map<UUID, Set<UUID>> manual = new HashMap<>();
    /** Joueurs en ligne → abonnements automatiques. */
    private final Map<UUID, Set<UUID>> auto = new HashMap<>();
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
        manual.clear();
        auto.clear();
        server = null;
    }

    private Set<UUID> manual(UUID player) {
        return manual.computeIfAbsent(player, id -> new HashSet<>(layer.store().loadSubs(id)));
    }

    private Set<UUID> subs(UUID player) {
        return byPlayer.computeIfAbsent(player, id -> {
            Set<UUID> s = new HashSet<>(manual(id));
            s.forEach(o -> byObject.computeIfAbsent(o, k -> new HashSet<>()).add(id));
            return s;
        });
    }

    /** Abonnements automatiques actuels du joueur (vide s'il n'en a pas). */
    public Set<UUID> auto(ServerPlayer p) { return auto.getOrDefault(p.getUUID(), Set.of()); }

    /** Objet désigné comme parent du joueur (source de sa position dans l'arbre). */
    public Optional<UUID> parentOf(UUID player) { return layer.store().loadPlayerParent(player); }

    /** Remplace les abonnements automatiques du joueur par {@code next} (non persisté) : Drop des retirés, Upsert des nouveaux. */
    public void setAuto(ServerPlayer p, Set<UUID> next) {
        auto.put(p.getUUID(), new HashSet<>(next));
        refresh(p.getUUID(), packet -> PacketDistributor.sendToPlayer(p, packet));
    }

    /** Vrai si un joueur en ligne est abonné à cet objet. */
    public boolean hasSubscribers(UUID object) { return byObject.containsKey(object); }

    public List<UUID> subscriptions(ServerPlayer p) { return List.copyOf(subs(p.getUUID())); }

    /** Connexion : repart d'une couche cliente vide, puis charge et envoie les abonnements manuels persistés (les objets disparus sont purgés). */
    public void login(ServerPlayer p) { login(p.getUUID(), packet -> PacketDistributor.sendToPlayer(p, packet)); }

    void login(UUID player, Consumer<CustomPacketPayload> out) {
        out.accept(new SpaceNet.Reset());
        for (UUID o : List.copyOf(subs(player))) push(player, o, out);
    }

    /** Libère l'état mémoire du joueur (les objets qu'il retenait peuvent être évincés) ; ses abonnements manuels restent dans le store. */
    public void logout(ServerPlayer p) {
        Set<UUID> s = byPlayer.remove(p.getUUID());
        if (s != null) s.forEach(o -> {
            unindex(o, p.getUUID());
            layer.touch(o);
        });
        manual.remove(p.getUUID());
        auto.remove(p.getUUID());
    }

    /** {@code o} doit être loadé. */
    public void subscribe(ServerPlayer p, SpaceObject o) {
        Set<UUID> next = new HashSet<>(manual(p.getUUID()));
        next.add(o.getUuid());
        setManual(p, next);
    }

    /** Retire l'abonnement manuel ; si le calcul automatique en a besoin, l'objet reste répliqué. */
    public void unsubscribe(ServerPlayer p, UUID object) {
        Set<UUID> next = new HashSet<>(manual(p.getUUID()));
        next.remove(object);
        setManual(p, next);
    }

    /**
     * Le parent du joueur est la source de sa position ; {@code null} l'efface. Ne persiste aucun abonnement : l'appelant relance le calcul
     * automatique ({@link fr.systamynna.spacemod.core.render.RenderConfigs#send}).
     */
    public void setParent(UUID player, @javax.annotation.Nullable UUID x) { layer.store().savePlayerParent(player, x); }

    private void setManual(ServerPlayer p, Set<UUID> next) {
        subs(p.getUUID()); // état répliqué initialisé avant que manual change, pour que refresh envoie les ajouts
        manual.put(p.getUUID(), next);
        refresh(p.getUUID(), packet -> PacketDistributor.sendToPlayer(p, packet));
        layer.store().saveSubs(p.getUUID(), next);
    }

    /**
     * Aligne les abonnements répliqués sur manuels + automatiques : Drop des retirés, Upsert des nouveaux et de ceux qui ne sont plus loadés
     * (invariant : abonné => loadé et présent chez le client).
     */
    void refresh(UUID player, Consumer<CustomPacketPayload> out) {
        Set<UUID> cur = subs(player), next = new HashSet<>(manual(player));
        next.addAll(auto.getOrDefault(player, Set.of()));
        for (UUID o : cur) if (!next.contains(o)) {
            unindex(o, player);
            layer.touch(o);
            out.accept(new SpaceNet.Drop(o));
        }
        List<UUID> sent = new ArrayList<>();
        for (UUID o : List.copyOf(next)) if (!cur.contains(o) || !layer.isLoaded(o)) {
            if (push(player, o, out)) sent.add(o); else next.remove(o);
        }
        // diagnostic NONE, retirable
        if (!sent.isEmpty()) AmynnaSpaceMod.LOGGER.debug("Subscriptions.refresh {} : {} Upsert {}", player, sent.size(), sent);
        cur.clear();
        cur.addAll(next);
    }

    /** Indexe l'abonnement, charge l'objet et l'envoie ; objet disparu : l'abonnement est purgé (manuel, auto, store) et la méthode renvoie faux. */
    private boolean push(UUID player, UUID o, Consumer<CustomPacketPayload> out) {
        byObject.computeIfAbsent(o, k -> new HashSet<>()).add(player);
        try {
            out.accept(SpaceNet.upsert(layer.getOrThrow(o)));
            return true;
        } catch (SpaceObjectException e) {
            AmynnaSpaceMod.LOGGER.warn("Abonnement à {} purgé pour {} : objet introuvable", o, player);
            unindex(o, player);
            subs(player).remove(o);
            Set<UUID> m = manual(player);
            if (m.remove(o)) layer.store().saveSubs(player, m);
            Set<UUID> a = auto.get(player);
            if (a != null) a.remove(o);
            return false;
        }
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
        if (ps != null) ps.forEach(id -> {
            for (var m : List.of(byPlayer, manual, auto)) {
                Set<UUID> s = m.get(id);
                if (s != null) s.remove(uuid);
            }
        });
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
