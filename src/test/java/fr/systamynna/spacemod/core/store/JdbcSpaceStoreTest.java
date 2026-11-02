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
            s.save(List.of(new SpaceObject(root, null, "universe"), new SpaceObject(child, root, "c")));
            s.save(List.of(new SpaceObject(child, root, "c"))); // upsert
            assertNull(s.load(root).orElseThrow().getParent());
            assertEquals(root, s.load(child).orElseThrow().getParent());
            assertEquals(1, s.loadChildren(root).size());
            s.delete(child);
            assertTrue(s.load(child).isEmpty());
        }
    }

    @Test
    void rootUuidAndParentless() throws Exception {
        try (SpaceStore s = new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:"))) {
            assertTrue(s.loadRootUuid().isEmpty());
            UUID a = UUID.randomUUID(), b = UUID.randomUUID();
            s.save(List.of(new SpaceObject(a, null, "a"), new SpaceObject(b, a, "b")));
            assertEquals(1, s.loadParentless().size());
            s.saveRootUuid(a);
            assertEquals(a, s.loadRootUuid().orElseThrow());
            s.saveRootUuid(b);
            assertEquals(b, s.loadRootUuid().orElseThrow());
            s.saveRootUuid(null);
            assertTrue(s.loadRootUuid().isEmpty());
        }
    }
}
