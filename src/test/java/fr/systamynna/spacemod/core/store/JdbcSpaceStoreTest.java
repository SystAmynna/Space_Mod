package fr.systamynna.spacemod.core.store;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.core.SpaceObject;

class JdbcSpaceStoreTest {
    @Test
    void saveLoadUpsertChildrenDelete() throws Exception {
        try (SpaceStore s = new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:"))) {
            UUID root = UUID.randomUUID(), child = UUID.randomUUID();
            s.save(List.of(new SpaceObject(root, null), new SpaceObject(child, root)));
            s.save(List.of(new SpaceObject(child, root))); // upsert
            assertNull(s.load(root).orElseThrow().getParent());
            assertEquals(root, s.load(child).orElseThrow().getParent());
            assertEquals(1, s.loadChildren(root).size());
            s.delete(child);
            assertTrue(s.load(child).isEmpty());
        }
    }
}
