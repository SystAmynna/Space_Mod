package fr.systamynna.spacemod.core;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.core.store.JdbcSpaceStore;

class SubscriptionsTest {
    @Test
    void parentSetIsObjectAncestorsAndChildren() throws Exception {
        StrategicLayer s = new StrategicLayer();
        s.start(new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:")));
        SpaceObject root = s.tree().resolve("universe");
        SpaceObject sol = s.add("sol", root);
        SpaceObject earth = s.add("earth", sol);
        SpaceObject moon = s.add("moon", earth);
        SpaceObject mars = s.add("mars", sol); // frère de X : exclu
        s.unload(moon.getUuid(), false);
        Set<UUID> set = Subscriptions.parentSet(s, earth);
        assertEquals(Set.of(earth.getUuid(), sol.getUuid(), root.getUuid(), moon.getUuid()), set);
        assertFalse(set.contains(mars.getUuid()));
        assertTrue(s.isLoaded(moon.getUuid())); // loadé au passage
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
