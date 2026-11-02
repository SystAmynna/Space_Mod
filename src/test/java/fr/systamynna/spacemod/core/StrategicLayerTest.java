package fr.systamynna.spacemod.core;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.sql.Connection;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.core.store.JdbcSpaceStore;

class StrategicLayerTest {
    @Test
    void treeOperations() throws Exception {
        Connection con = DriverManager.getConnection("jdbc:sqlite::memory:");
        StrategicLayer s = new StrategicLayer();
        s.start(new JdbcSpaceStore(con));
        SpaceObject root = s.tree().resolve("universe");
        SpaceObject sol = s.add("sol", s.add("milky_way", root));
        SpaceObject sun = s.add("sun", sol);
        SpaceObject earth = s.add("earth", sun);
        assertEquals("universe/milky_way/sol/sun/earth", s.tree().addressOf(earth));
        assertSame(earth, s.tree().resolve("universe/milky_way/sol/sun/earth"));

        assertThrows(SpaceObjectException.class, () -> s.add("sun", sol));
        assertThrows(SpaceObjectException.class, () -> s.add("Bad/Name", sol));
        assertThrows(SpaceObjectException.class, () -> s.tree().resolve("universe/nope"));
        assertThrows(SpaceObjectException.class, () -> s.setParent(sol, earth)); // cycle
        assertThrows(SpaceObjectException.class, () -> s.remove(root, true));

        s.setParent(earth, sol);
        assertEquals("universe/milky_way/sol/earth", s.tree().addressOf(earth));

        s.add("moon", earth);
        s.remove(earth, false); // keep : la lune remonte chez sol
        assertEquals("universe/milky_way/sol/moon", s.tree().addressOf(s.tree().resolve("universe/milky_way/sol/moon")));
        assertThrows(SpaceObjectException.class, () -> s.tree().resolve("universe/milky_way/sol/earth"));

        s.remove(sol, true); // recursive
        assertThrows(SpaceObjectException.class, () -> s.tree().resolve("universe/milky_way/sol"));
        assertTrue(s.get(sun.getUuid()).isEmpty());
        s.stop();
    }

    @Test
    void persistsAcrossRestart() throws Exception {
        java.nio.file.Path f = java.nio.file.Files.createTempFile("space", ".db");
        try {
            StrategicLayer s = new StrategicLayer();
            s.start(new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite:" + f)));
            s.add("a", s.tree().resolve("universe"));
            s.stop();
            StrategicLayer s2 = new StrategicLayer();
            s2.start(new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite:" + f)));
            assertEquals("universe/a", s2.tree().addressOf(s2.tree().resolve("universe/a")));
            s2.stop();
        } finally {
            java.nio.file.Files.deleteIfExists(f);
        }
    }

    @Test
    void parentlessRootAndTree() throws Exception {
        StrategicLayer s = new StrategicLayer();
        s.start(new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:")));
        SpaceObject universe = s.tree().resolve("universe");
        assertTrue(s.isRoot(universe));
        SpaceObject galaxy = s.add("galaxy", universe);
        s.add("sol", galaxy);
        s.add("vega", galaxy);
        SpaceObject other = s.add("other", null);
        assertThrows(SpaceObjectException.class, () -> s.add("other", null));
        assertSame(other, s.tree().resolve("other"));
        assertEquals(java.util.List.of("⭐ universe", " └ galaxy", "    ├ sol", "    └ vega"), lines(s, false));
        assertEquals(java.util.List.of("⭐ universe", " └ galaxy", "    ├ sol", "    └ vega", "▶ other"), lines(s, true));

        assertThrows(SpaceObjectException.class, () -> s.setRoot(galaxy)); // a un parent
        s.setRoot(other);
        assertTrue(s.isRoot(other));
        assertEquals("⭐ other", lines(s, false).get(0));
        assertThrows(SpaceObjectException.class, () -> s.remove(other, true));

        s.setParent(galaxy, null); // détacher
        assertEquals("galaxy", s.tree().addressOf(galaxy));
        s.remove(galaxy, false); // keep d'un sans-parent : les enfants deviennent sans-parent
        assertEquals("sol", s.tree().addressOf(s.tree().resolve("sol")));
        s.stop();
    }

    @Test
    void invalidStoredRootIsCleared() throws Exception {
        Connection con = DriverManager.getConnection("jdbc:sqlite::memory:");
        JdbcSpaceStore store = new JdbcSpaceStore(con);
        java.util.UUID a = java.util.UUID.randomUUID(), b = java.util.UUID.randomUUID();
        store.save(java.util.List.of(new SpaceObject(a, null, "a"), new SpaceObject(b, a, "b")));

        store.saveRootUuid(java.util.UUID.randomUUID()); // objet inexistant
        StrategicLayer s = new StrategicLayer();
        s.start(store);
        assertTrue(store.loadRootUuid().isEmpty());
        assertTrue(s.tree().resolve("a") != null && !s.isRoot(s.tree().resolve("a"))); // pas de recréation d'un root

        store.saveRootUuid(b); // objet avec parent
        s.start(store);
        assertTrue(store.loadRootUuid().isEmpty());
        assertEquals("a/b", s.tree().addressOf(s.tree().resolve("a/b"))); // objet conservé
    }

    private static java.util.List<String> lines(StrategicLayer s, boolean full) {
        return s.tree().render(full).stream().map(c -> c.getString()).toList();
    }
}
