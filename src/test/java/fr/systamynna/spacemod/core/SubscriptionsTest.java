package fr.systamynna.spacemod.core;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import fr.systamynna.spacemod.core.store.JdbcSpaceStore;

class SubscriptionsTest {
    private static StrategicLayer layer(JdbcSpaceStore store) throws Exception {
        StrategicLayer s = new StrategicLayer();
        s.start(store);
        return s;
    }

    @Test
    void loginLoadsAndSendsPersistedUnloadedSubs() throws Exception {
        JdbcSpaceStore store = new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:"));
        StrategicLayer s = layer(store);
        UUID player = UUID.randomUUID(), gone = UUID.randomUUID();
        SpaceObject a = s.add("a", s.tree().resolve("universe"));
        s.unload(a.getUuid(), false);
        store.saveSubs(player, Set.of(a.getUuid(), gone)); // `gone` : objet supprimé depuis
        Subscriptions subs = new Subscriptions(s);
        List<CustomPacketPayload> out = new ArrayList<>();
        subs.login(player, out::add);
        assertTrue(s.isLoaded(a.getUuid()));
        assertInstanceOf(SpaceNet.Reset.class, out.get(0));
        assertEquals(2, out.size()); // Reset + Upsert de a ; `gone` ignoré
        assertEquals(a.getUuid(), ((SpaceNet.Upsert) out.get(1)).uuid());
        assertEquals(Set.of(a.getUuid()), store.loadSubs(player)); // `gone` purgé

        // refresh : un abonné déjà connu mais déchargé est rechargé et renvoyé
        s.unload(a.getUuid(), false);
        out.clear();
        subs.refresh(player, out::add);
        assertTrue(s.isLoaded(a.getUuid()));
        assertEquals(1, out.size());
    }

    @Test
    void setParentPersistsNoSubscription() throws Exception {
        JdbcSpaceStore store = new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:"));
        StrategicLayer s = layer(store);
        UUID player = UUID.randomUUID();
        SpaceObject earth = s.add("earth", s.add("sol", s.tree().resolve("universe")));
        Subscriptions subs = new Subscriptions(s);
        subs.setParent(player, earth.getUuid());
        assertEquals(earth.getUuid(), subs.parentOf(player).orElseThrow());
        assertTrue(store.loadSubs(player).isEmpty());
        subs.setParent(player, null);
        assertTrue(subs.parentOf(player).isEmpty());
    }

    @Test
    void listenerSeesUpdateDropRemove() throws Exception {
        StrategicLayer s = new StrategicLayer();
        s.start(new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:")));
        AtomicInteger up = new AtomicInteger(), drop = new AtomicInteger(), rem = new AtomicInteger();
        s.setListener(new SpaceListener() {
            @Override public void updated(SpaceObject o) { up.incrementAndGet(); }
            @Override public void dropped(UUID u) { drop.incrementAndGet(); }
            @Override public void removed(UUID u) { rem.incrementAndGet(); }
        });
        SpaceObject o = s.add("a", s.tree().resolve("universe"));
        assertEquals(1, up.get());
        s.unload(o.getUuid(), false);
        assertEquals(1, drop.get());
        s.load(o.getUuid()); // reload : renvoi
        assertEquals(2, up.get());
        s.remove(o, false);
        assertEquals(1, rem.get());
    }
}
