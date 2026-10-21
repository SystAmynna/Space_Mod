package fr.systamynna.spacemod.strategic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fr.systamynna.spacemod.strategic.component.ComponentTypes;
import fr.systamynna.spacemod.strategic.component.RailFollower;
import fr.systamynna.spacemod.strategic.component.Render;
import fr.systamynna.spacemod.strategic.component.Transform;
import fr.systamynna.spacemod.strategic.store.Dialect;
import fr.systamynna.spacemod.strategic.store.DriverLoader;
import fr.systamynna.spacemod.strategic.store.JdbcStrategicStore;
import fr.systamynna.spacemod.strategic.store.StoredObject;
import fr.systamynna.spacemod.strategic.store.StrategicStoreException;
import net.minecraft.resources.ResourceLocation;

/** Persistance de la couche dans une base SQLite : aller-retour, écriture par objet, robustesse, préfixes, pilotes. */
class StrategicStoreTest {
    private static final ResourceLocation TERRA = ResourceLocation.parse("amynnaspacemod:terra");
    private static final ResourceLocation MODEL = ResourceLocation.parse("amynnaspacemod:proxy/rock");

    @TempDir
    Path dir;

    private SpaceObject milkyWay;
    private SpaceObject andromeda;
    private SpaceObject sol;
    private SpaceObject sun;
    private SpaceObject terra;
    private StrategicLayer open;

    private Path db() {
        return dir.resolve("amynnaspacemod/strategic.db");
    }

    /** Ouvre une couche sur la base ; la précédente est fermée pour simuler un redémarrage. */
    private StrategicLayer open() {
        if (open != null) open.close();
        open = new StrategicLayer(JdbcStrategicStore.sqlite(db()));
        return open;
    }

    @BeforeEach
    void populate() {
        StrategicLayer layer = open();
        milkyWay = layer.addObject(null, Archetypes.galaxy("Voie lactée", Transform.ORIGIN, null));
        andromeda = layer.addObject(null, Archetypes.galaxy("Andromède", Transform.at(2.4e22, 0, 0), null));
        sol = layer.addObject(milkyWay.id(), Archetypes.system("Sol", Transform.at(2.6e17, 0, 0), null));
        sun = layer.addObject(sol.id(), Archetypes.star("Soleil", Transform.ORIGIN, null, new Render(MODEL, 7e8), 1.4e9));
        terra = layer.addObject(sol.id(), Archetypes.planet("Terra",
                RailFollower.orbit(1_728_000L * 365, 1.5e8, 12345, 0.1), new Render(MODEL, 6.4e6), TERRA, 512, List.of()));
        assertEquals(5, layer.save());
    }

    @AfterEach
    void close() {
        if (open != null) open.close();
    }

    @Test
    void layerComesBackIdenticalAfterRestart() {
        StrategicLayer reloaded = open();
        assertEquals(Set.of(milkyWay.id(), andromeda.id()), Set.copyOf(reloaded.galaxies().stream().map(SpaceObject::id).toList()));

        SpaceObject back = reloaded.find(terra.id());
        assertNotNull(back);
        assertEquals(Set.copyOf(terra.components()), Set.copyOf(back.components()));
        assertEquals(terra.controller(), back.controller());
        assertEquals(sol.id(), back.parent());
        assertEquals(Set.of(sun.id(), terra.id()), reloaded.find(sol.id()).children());
        assertEquals(milkyWay.id(), reloaded.galaxyOf(terra.id()).id());
    }

    @Test
    void anyDimensionFindsItsHostObject() {
        StrategicLayer reloaded = open();
        assertEquals(Set.of(TERRA), reloaded.hostedDimensions());
        assertEquals(terra.id(), reloaded.hostOf(TERRA).id());
        assertNull(reloaded.hostOf(ResourceLocation.parse("amynnaspacemod:inconnue")));
    }

    @Test
    void onlyModifiedObjectsAreWritten() {
        StrategicLayer layer = open();
        assertEquals(0, layer.save());

        layer.find(sun.id()).put(Transform.at(1, 2, 3));
        assertEquals(1, layer.save());
        assertEquals(0, layer.save());
        assertEquals(1.0, open().find(sun.id()).get(ComponentTypes.TRANSFORM).tx());
    }

    @Test
    void movingASystemBetweenGalaxiesIsPersisted() {
        StrategicLayer layer = open();
        layer.reparent(sol.id(), andromeda.id(), 0);
        assertEquals(3, layer.save(), "le système et ses deux galaxies (listes d'enfants) ; ses corps ne bougent pas");

        StrategicLayer reloaded = open();
        assertEquals(andromeda.id(), reloaded.galaxyOf(terra.id()).id());
        assertEquals(andromeda.id(), reloaded.parentOf(sol.id()).id());
        assertEquals(Set.of(), reloaded.find(milkyWay.id()).children());
    }

    @Test
    void destroyedObjectIsKeptAndRestorable() {
        StrategicLayer layer = open();
        SpaceObject cruiser = layer.addObject(sol.id(), Archetypes.cruiser("Albatros", Transform.ORIGIN,
                ResourceLocation.parse("amynnaspacemod:albatros"), 32));
        layer.destroy(cruiser.id());
        assertEquals(2, layer.save(), "le croiseur et son système, dont la liste d'enfants a changé");

        StrategicLayer reloaded = open();
        assertTrue(reloaded.find(cruiser.id()).isDestroyed());
        assertNotNull(reloaded.hostOf(ResourceLocation.parse("amynnaspacemod:albatros")), "la dimension d'un objet détruit reste attribuée");

        reloaded.restore(cruiser.id());
        reloaded.save();
        assertFalse(open().find(cruiser.id()).isDestroyed());
    }

    @Test
    void unreadableDatabaseFailsLoudlyInsteadOfBeingRecreated() throws IOException {
        open.close();
        open = null;
        Files.writeString(db(), "pas une base SQLite, assez long pour ne pas être pris pour un fichier vide");
        assertThrows(StrategicStoreException.class, this::open);
        assertTrue(Files.readString(db()).startsWith("pas une base"));
    }

    @Test
    void newerFormatIsRefused() throws Exception {
        open.close();
        open = null;
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db()); Statement s = c.createStatement()) {
            s.execute("UPDATE spacemod_meta SET v = '" + (JdbcStrategicStore.FORMAT_VERSION + 1) + "' WHERE k = 'format_version'");
        }
        assertThrows(StrategicStoreException.class, this::open);
    }

    @Test
    void invalidAdditionsAreRejected() {
        StrategicLayer layer = open();
        assertThrows(IllegalArgumentException.class, () -> layer.addObject(UUID.randomUUID(), new SpaceObject(UUID.randomUUID())));
        assertThrows(IllegalArgumentException.class, () -> layer.addObject(sol.id(), terra));
        assertThrows(IllegalArgumentException.class, () -> layer.addObject(sol.id(),
                Archetypes.station("Double", RailFollower.orbit(100, 1, 0, 0), TERRA, 64)),
                "une dimension n'a qu'un hôte");
        assertThrows(IllegalArgumentException.class, () -> layer.destroy(UUID.randomUUID()));
    }

    @Test
    void sameDimensionHostedUnderAnotherSystemIsRejected() {
        StrategicLayer layer = open();
        SpaceObject other = layer.addObject(andromeda.id(), Archetypes.system("Messier", Transform.ORIGIN, null));
        SpaceObject intruder = Archetypes.cruiser("Intrus", Transform.ORIGIN, TERRA, 10);
        assertThrows(IllegalArgumentException.class, () -> layer.addObject(other.id(), intruder));
    }

    @Test
    void theDatabaseItselfRefusesTwoHostsForOneDimensionAndRollsBack() {
        try (JdbcStrategicStore store = JdbcStrategicStore.inMemory()) {
            SpaceObject a = Archetypes.station("A", RailFollower.orbit(100, 1, 0, 0), TERRA, 64);
            SpaceObject b = Archetypes.station("B", RailFollower.orbit(100, 1, 0, 0), TERRA, 64);
            SpaceObject innocent = new SpaceObject(UUID.randomUUID());
            assertThrows(StrategicStoreException.class,
                    () -> store.save(List.of(new StoredObject(innocent, null), new StoredObject(a, null), new StoredObject(b, null)), null));
            assertEquals(0, store.loadResident().size(), "la transaction a été annulée en entier");
        }
    }

    @Test
    void tablePrefixesKeepWorldsApartInOneDatabase() {
        open.close();
        open = null;
        try (JdbcStrategicStore other = JdbcStrategicStore.open("jdbc:sqlite:" + db(), null, null, "autre_", null)) {
            assertEquals(0, other.loadResident().size());
        }
        assertThrows(StrategicStoreException.class, () -> JdbcStrategicStore.open("jdbc:sqlite:" + db(), null, null, "x; DROP TABLE", null));
        assertEquals(Set.of(sun.id(), terra.id()), open().find(sol.id()).children(), "la base d'origine est intacte");
    }

    @Test
    void dialectIsDeducedFromTheUrl() {
        assertEquals(Dialect.SQLITE, Dialect.fromUrl("jdbc:sqlite:/x.db"));
        assertEquals(Dialect.MYSQL, Dialect.fromUrl("jdbc:mariadb://h/db"));
        assertEquals(Dialect.MYSQL, Dialect.fromUrl("jdbc:mysql://h/db"));
        assertEquals(Dialect.POSTGRESQL, Dialect.fromUrl("jdbc:postgresql://h/db"));
        assertThrows(StrategicStoreException.class, () -> Dialect.fromUrl("jdbc:oracle:thin:@h"));
    }

    @Test
    void driverLoaderReportsClearErrors() {
        assertThrows(StrategicStoreException.class, () -> DriverLoader.load("org.exemple.Inexistant", null));
        assertThrows(StrategicStoreException.class, () -> DriverLoader.load("org.sqlite.JDBC", dir.resolve("absent.jar")));
        assertThrows(StrategicStoreException.class, () -> DriverLoader.load("java.lang.String", null));
        assertNotNull(DriverLoader.load("org.sqlite.JDBC", null));
    }
}
