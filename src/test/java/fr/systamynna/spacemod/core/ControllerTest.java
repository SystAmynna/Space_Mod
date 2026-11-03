package fr.systamynna.spacemod.core;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.core.controller.SpaceController;
import fr.systamynna.spacemod.core.controller.controllers.MultipleController;
import fr.systamynna.spacemod.core.controller.controllers.OrbitalController;
import fr.systamynna.spacemod.core.store.JdbcSpaceStore;

class ControllerTest {
    private static OrbitalController orbit(String args) {
        OrbitalController c = new OrbitalController();
        c.edit(args);
        return c;
    }

    private static Transform at(SpaceController c, double ticks) {
        Transform t = new Transform();
        c.apply(ticks, t);
        return t;
    }

    private static StrategicLayer layer() throws Exception {
        StrategicLayer s = new StrategicLayer();
        s.start(new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:")));
        return s;
    }

    @Test
    void circularOrbit() {
        OrbitalController c = orbit("a=10 period=100");
        Transform t = at(c, 0);
        assertEquals(10, t.x, 1e-9);
        assertEquals(0, t.z, 1e-9);
        t = at(c, 25);
        assertEquals(0, t.x, 1e-9);
        assertEquals(10, t.z, 1e-9);
        assertEquals(-10, at(c, 50).x, 1e-9);
        assertEquals(at(c, 7).x, at(c, 107).x, 1e-9);
        assertEquals(at(c, 7).z, at(c, 1_000_000_007).z, 1e-6); // pas de dérive à grand ticks
    }

    @Test
    void ellipseAndInclination() {
        OrbitalController c = orbit("a=10 e=0.5 period=100");
        assertEquals(5, Math.hypot(at(c, 0).x, at(c, 0).z), 1e-9);   // périastre a(1-e)
        assertEquals(15, Math.hypot(at(c, 50).x, at(c, 50).z), 1e-9); // apoastre a(1+e)
        Transform t = at(orbit("a=10 i=90 period=100"), 25); // quart de tour : tout dans la hauteur
        assertEquals(10, t.y, 1e-9);
        assertEquals(0, t.z, 1e-9);
    }

    @Test
    void spin() {
        OrbitalController c = orbit("period=100 spin=100 tilt=20 spinphase=10");
        assertEquals(10, at(c, 0).yaw, 1e-9);
        assertEquals(100, at(c, 25).yaw, 1e-9);
        assertEquals(20, at(c, 25).roll, 1e-9);
        assertEquals(10, at(c, 1_000_000_000).yaw, 1e-4);
        assertEquals(270, at(orbit("period=100 spin=-100"), 25).yaw, 1e-9); // rétrograde, replié dans [0,360)
        Transform keep = new Transform();
        keep.yaw = 42;
        orbit("period=100").apply(33, keep);
        assertEquals(42, keep.yaw); // spin=0 : rotation intacte
    }

    @Test
    void editRejectsAndStaysConsistent() {
        OrbitalController c = orbit("a=3");
        assertThrows(SpaceObjectException.class, () -> c.edit("a=5 e=1"));
        assertThrows(SpaceObjectException.class, () -> c.edit("period=0"));
        assertThrows(SpaceObjectException.class, () -> c.edit("bogus=1"));
        assertEquals(3, c.a); // rien appliqué malgré "a=5" valide en tête
    }

    @Test
    void multipleSwitches() {
        MultipleController m = new MultipleController();
        Transform t = new Transform();
        t.x = 99;
        m.apply(10, t);
        assertEquals(99, t.x); // aucun actif
        m.edit("add near orbital");
        m.edit("add far orbital");
        m.edit("in near a=1 period=100");
        m.edit("in far a=2 period=100");
        m.edit("use near");
        assertEquals(1, at(m, 0).x, 1e-9);
        m.edit("use far");
        assertEquals(2, at(m, 0).x, 1e-9);
        assertThrows(SpaceObjectException.class, () -> m.edit("add x multiple"));
        assertThrows(SpaceObjectException.class, () -> m.edit("use nope"));
        m.edit("remove far");
        assertNull(m.active());
    }

    @Test
    void saveLoadRoundTrip() throws Exception {
        StrategicLayer s = layer();
        SpaceObject o = s.add("planet", s.tree().resolve("universe"));
        MultipleController m = new MultipleController();
        m.edit("add a orbital");
        m.edit("in a a=7 e=0.2 spin=50");
        m.edit("use a");
        s.setController(o.getUuid(), m);
        s.tickControllers(12);
        SpaceObject back = SpaceObject.load(o.getUuid(), o.getParent(), o.save());
        assertInstanceOf(MultipleController.class, back.getController());
        assertEquals(m.describe(), back.getController().describe());
        back.resume(12);
        assertEquals(o.transform.x, back.transform.x, 1e-12);
        // anciennes données / contrôleur inconnu : pas de crash
        net.minecraft.nbt.CompoundTag t = o.save();
        t.getCompound("controller").putString("type", "inconnu");
        assertNull(SpaceObject.load(o.getUuid(), o.getParent(), t).getController());
        t.remove("controller");
        assertNull(SpaceObject.load(o.getUuid(), o.getParent(), t).getController());
    }

    @Test
    void resumeReceivesSavedTime() {
        double[] seen = new double[2];
        SpaceController spy = new OrbitalController() {
            @Override public void resume(Transform t, double saved, double now) { seen[0] = saved; seen[1] = now; }
        };
        SpaceObject o = new SpaceObject(java.util.UUID.randomUUID(), null, "x");
        o.setController(spy);
        o.step(5);
        // le controller anonyme n'est pas enregistré : on teste resume directement sur l'objet
        o.resume(9);
        assertArrayEquals(new double[] {5, 9}, seen);
    }

    @Test
    void deferredPersistenceImmediateReplication() throws Exception {
        StrategicLayer s = new StrategicLayer();
        JdbcSpaceStore store = new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:"));
        s.start(store);
        SpaceObject o = s.add("a", s.tree().resolve("universe"));
        AtomicInteger up = new AtomicInteger();
        s.setListener(new SpaceListener() { @Override public void updated(SpaceObject x) { up.incrementAndGet(); } });

        s.editTransform(o.getUuid(), t -> t.edit("pos 1 2 3"));
        assertEquals(1, up.get());
        assertEquals(0, store.load(o.getUuid()).orElseThrow().transform.x); // pas encore écrit
        s.flush();
        assertEquals(1, store.load(o.getUuid()).orElseThrow().transform.x);
        assertEquals(1, up.get()); // flush ne réplique pas une seconde fois

        s.setController(o.getUuid(), orbit("a=5"));
        assertNull(store.load(o.getUuid()).orElseThrow().getController());
        s.add("b", s.tree().resolve("universe")); // opération d'arbre : flush tout
        assertNotNull(store.load(o.getUuid()).orElseThrow().getController());
    }

    @Test
    void tickDoesNotPersistNorReplicate() throws Exception {
        StrategicLayer s = layer();
        SpaceObject o = s.add("a", s.tree().resolve("universe"));
        s.setController(o.getUuid(), orbit("a=5 period=100"));
        s.flush();
        AtomicInteger up = new AtomicInteger();
        s.setListener(new SpaceListener() { @Override public void updated(SpaceObject x) { up.incrementAndGet(); } });
        s.tickControllers(25);
        assertEquals(0, up.get());
        assertEquals(5, o.transform.z, 1e-9);
        // rien de dirty : un flush ne change pas la base
        s.flush();
    }

    @Test
    void tildeKeepsValue() {
        OrbitalController c = orbit("a=3 period=100");
        c.edit("a=~ e=0.3");
        assertEquals(3, c.a);
        assertEquals(0.3, c.e);
        assertThrows(SpaceObjectException.class, () -> c.edit("bogus=~"));
        assertThrows(SpaceObjectException.class, () -> c.edit("e=~ a=-1"));
        assertEquals(3, c.a);
        assertTrue(c.suggest("a=").contains("a=~"));
        assertTrue(c.suggest("").contains("period="));
        MultipleController m = new MultipleController();
        m.edit("add near orbital");
        m.edit("in near a=4");
        m.edit("in near a=~ period=50");
        assertEquals(4, at(m.get("near"), 0).x, 1e-9 * 4 + 1e-9); // a conservé (m0=0 → x = a)
        m.edit("use near");
        assertEquals(4, at(m, 0).x, 1e-9);
    }
}
