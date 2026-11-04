package fr.systamynna.spacemod.core;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.core.store.JdbcSpaceStore;

class TransformTest {
    private static void pos(StrategicLayer s, SpaceObject o, double x, double y, double z) {
        s.editTransform(o.getUuid(), t -> t.edit("pos " + x + " " + y + " " + z));
    }

    private static void assertPos(double x, double y, double z, Transform t) {
        assertEquals(x, t.x, 1e-9);
        assertEquals(y, t.y, 1e-9);
        assertEquals(z, t.z, 1e-9);
    }

    @Test
    void scaleAndConversion() throws Exception {
        StrategicLayer s = new StrategicLayer();
        s.start(new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:")));
        SpaceObject root = s.tree().resolve("universe");
        SpaceObject planet = s.add("planet", root), moon = s.add("moon", planet);

        pos(s, planet, 10, 10, 10);
        s.setScale(planet.getUuid(), 10); // 1.0 du transform de la planète = 10 km
        assertPos(100, 100, 100, s.tree().worldTransform(planet)); // en km
        pos(s, moon, 1, 2, 3); // échelle 1 : en km
        assertPos(101, 102, 103, s.tree().worldTransform(moon));

        // vaisseau (1,1,1) km dans le référentiel de la lune (2,4,6 km) → (3,5,7) km chez la planète
        pos(s, moon, 2, 4, 6);
        Transform t = s.tree().convert(new Transform() {{ x = y = z = 1; }}, 1, moon, planet);
        assertPos(3, 5, 7, t);
        assertPos(1, 1, 1, s.tree().convert(t, 1, planet, moon)); // aller-retour
        assertPos(1.2, 1.4, 1.6, s.tree().convert(new Transform() {{ x = y = z = 1; }}, 10, moon, planet)); // même chose en unités de 10 km

        assertThrows(SpaceObjectException.class, () -> s.setScale(planet.getUuid(), 0));
        assertThrows(SpaceObjectException.class, () -> s.setScale(planet.getUuid(), Double.NaN));

        // persistance
        SpaceObject back = SpaceObject.load(planet.getUuid(), root.getUuid(), planet.save());
        assertEquals(10, back.getScale());
        assertPos(10, 10, 10, back.transform);
        s.stop();
    }

    @Test
    void setParentTranspose() throws Exception {
        StrategicLayer s = new StrategicLayer();
        s.start(new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:")));
        SpaceObject star = s.add("star", s.tree().resolve("universe")), planet = s.add("planet", star), ship = s.add("ship", planet);
        pos(s, planet, 10, 10, 10);
        pos(s, ship, 1, 2, 3);

        s.setParent(ship, star, false);
        assertPos(1, 2, 3, ship.transform);
        s.setParent(ship, planet, false);
        s.setParent(ship, star, true);
        assertPos(11, 12, 13, ship.transform);

        s.setParent(ship, planet, true); // retour : aller-retour exact
        assertPos(1, 2, 3, ship.transform);
        s.setScale(planet.getUuid(), 0.1); // la planète est à 1 km de l'étoile
        s.setParent(ship, star, true);
        assertPos(2, 3, 4, ship.transform);
        s.stop();
    }

    @Test
    void roundTripIsExact() throws Exception {
        StrategicLayer s = new StrategicLayer();
        s.start(new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:")));
        SpaceObject star = s.add("star", s.tree().resolve("universe")), planet = s.add("planet", star), ship = s.add("ship", planet);
        pos(s, planet, 10, 10, 10);
        for (double scale : new double[] {0.1, 0.3, 0.7})
            for (double v : new double[] {0.5, 0.3, 0.7, 1.1}) {
                s.setScale(planet.getUuid(), scale);
                s.setParent(ship, planet, false);
                pos(s, ship, v, v, v);
                s.setParent(ship, star, true);
                s.setParent(ship, planet, true);
                assertEquals(v, ship.transform.x, 0);
                assertEquals(v, ship.transform.z, 0);
            }
        s.stop();
    }

    @Test
    void editAllChainsAndIsAtomic() {
        Transform t = new Transform();
        t.edit("pos 5 6 7");
        t.editAll("pos 1 ~ 3 rot ~ 90 ~");
        assertPos(1, 6, 3, t);
        assertEquals(90, t.pitch);
        assertEquals(0, t.yaw);

        assertThrows(SpaceObjectException.class, () -> t.editAll("pos 9 9 9 rot 1 2"));
        assertThrows(SpaceObjectException.class, () -> t.editAll("pos 9 9 9 rot 1 2 x"));
        assertPos(1, 6, 3, t); // rien d'appliqué

        assertEquals(java.util.List.of("pos", "rot"), t.suggest(""));
        assertEquals(java.util.List.of("~"), t.suggest("pos 1 "));
        assertEquals(java.util.List.of("pos", "rot"), t.suggest("pos 1 2 3 "));
    }
}
