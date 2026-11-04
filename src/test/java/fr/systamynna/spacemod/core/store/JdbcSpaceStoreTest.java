package fr.systamynna.spacemod.core.store;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.Transform;
import fr.systamynna.spacemod.core.component.components.Sky;
import fr.systamynna.spacemod.core.sky.SkyParams;
import fr.systamynna.spacemod.core.sky.SkySummary;
import net.minecraft.nbt.CompoundTag;

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

    @Test
    void subsAndPlayerParent() throws Exception {
        try (SpaceStore s = new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:"))) {
            UUID p = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
            s.save(List.of(new SpaceObject(a, null, "a"), new SpaceObject(b, null, "b")));
            assertTrue(s.loadSubs(p).isEmpty());
            s.saveSubs(p, List.of(a, b));
            assertEquals(java.util.Set.of(a, b), s.loadSubs(p));
            s.saveSubs(p, List.of(a)); // remplacement
            assertEquals(java.util.Set.of(a), s.loadSubs(p));
            s.savePlayerParent(p, b);
            assertEquals(b, s.loadPlayerParent(p).orElseThrow());
            s.savePlayerParent(p, null);
            assertTrue(s.loadPlayerParent(p).isEmpty());
            s.delete(a); // purge les abonnements
            assertTrue(s.loadSubs(p).isEmpty());
        }
    }

    /** Système à (x ly, 0, 0) de {@code parent}, échelle = 1 al ; {@code sky} null : sans composant. */
    private static SpaceObject system(UUID uuid, UUID parent, double xLy, Sky sky) {
        Transform tr = new Transform();
        tr.x = xLy;
        CompoundTag t = new CompoundTag(), comps = new CompoundTag();
        t.putString("name", "s");
        t.put("transform", tr.save());
        t.putDouble("scale", SkyParams.LY_KM);
        if (sky != null) comps.put(Sky.ID, sky.save());
        t.put("components", comps);
        return SpaceObject.load(uuid, parent, t);
    }

    private static Sky sky(int color, double lum) {
        Sky s = new Sky();
        s.color = color;
        s.luminosity = lum;
        return s;
    }

    private static int rows(Connection con) throws Exception {
        try (var st = con.createStatement(); var rs = st.executeQuery("SELECT COUNT(*) FROM space_sky")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static final int[] C0 = {0, 0, 0}, C1 = {1, 1, 1}, ALL = {-9, -9, -9}, ALL_HI = {9, 9, 9};

    @Test
    void skySavedFilteredUpdatedDeletedAndBackfilled() throws Exception {
        Connection con = DriverManager.getConnection("jdbc:sqlite::memory:");
        JdbcSpaceStore s = new JdbcSpaceStore(con);
        UUID parent = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), none = UUID.randomUUID();
        // cellules de 64 al : a (10 al) -> 0, b (100 al) -> 1, c (-5 al) -> -1
        s.save(List.of(system(a, parent, 10, sky(0xFF0000, 1)), system(b, parent, 100, sky(0x00FF00, 50)), system(c, parent, -5, sky(0x0000FF, 0.1)),
                system(none, parent, 7, null), system(UUID.randomUUID(), null, 7, sky(1, 1)))); // sans composant, ou sans parent : pas de ligne
        assertEquals(3, rows(con));
        assertEquals(3, s.skyInBox(parent, ALL, ALL_HI, 0).size());
        assertEquals(0, s.skyInBox(UUID.randomUUID(), ALL, ALL_HI, 0).size(), "autre parent");
        List<SkySummary> cell0 = s.skyInBox(parent, C0, C0, 0);
        assertEquals(1, cell0.size());
        assertEquals(a, cell0.get(0).uuid());
        assertEquals(10 * SkyParams.LY_KM, cell0.get(0).x(), 1);
        assertEquals(SkyParams.LY_KM, cell0.get(0).scaleKm(), 1);
        assertEquals(0xFF0000, cell0.get(0).color());
        assertEquals(1f, cell0.get(0).lum());
        assertEquals(2, s.skyInBox(parent, C0, new int[] {1, 0, 0}, 0).size());
        assertEquals(0, s.skyInBox(parent, C1, C1, 0).size(), "cy/cz = 1 : aucun système");
        assertEquals(List.of(b), s.skyInBox(parent, ALL, ALL_HI, 5).stream().map(SkySummary::uuid).toList(), "minLum");

        s.save(List.of(system(a, parent, 10, sky(0xFF0000, 2)))); // mise à jour, pas de doublon
        assertEquals(3, rows(con));
        assertEquals(2f, s.skyInBox(parent, C0, C0, 0).get(0).lum());
        s.save(List.of(system(c, parent, -5, null))); // composant retiré : la ligne disparaît
        assertEquals(2, rows(con));
        s.delete(b);
        assertEquals(List.of(a), s.skyInBox(parent, ALL, ALL_HI, 0).stream().map(SkySummary::uuid).toList());

        // rattrapage : base sans space_sky rempli (ancienne base), une seule fois et sans doublon
        try (var st = con.createStatement()) {
            st.execute("DELETE FROM space_sky");
            st.execute("DELETE FROM space_meta WHERE k = 'schema:sky'");
        }
        new JdbcSpaceStore(con);
        assertEquals(List.of(a), s.skyInBox(parent, ALL, ALL_HI, 0).stream().map(SkySummary::uuid).toList());
        try (var st = con.createStatement()) {
            st.execute("DELETE FROM space_sky");
        }
        new JdbcSpaceStore(con); // clé meta présente : pas de nouveau rattrapage
        assertEquals(0, rows(con));
        for (int i = 0; i < 2; i++) { // rattrapage rejoué sur des lignes déjà présentes : même résultat
            try (var st = con.createStatement()) {
                st.execute("DELETE FROM space_meta WHERE k = 'schema:sky'");
            }
            new JdbcSpaceStore(con);
            assertEquals(1, rows(con), "idempotent");
        }
        s.close();
    }
}
