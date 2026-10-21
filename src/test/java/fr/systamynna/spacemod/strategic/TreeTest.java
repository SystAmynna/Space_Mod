package fr.systamynna.spacemod.strategic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.strategic.component.Pilotable;
import fr.systamynna.spacemod.strategic.component.RailFollower;
import fr.systamynna.spacemod.strategic.component.Render;
import fr.systamynna.spacemod.strategic.component.StellarSystem;
import fr.systamynna.spacemod.strategic.component.Transform;
import fr.systamynna.spacemod.strategic.store.JdbcStrategicStore;
import net.minecraft.resources.ResourceLocation;

/** Arbre Couche → Galaxie → Système → planète → lune → vaisseau, parrainage et changement de parent. */
class TreeTest {
    private static final ResourceLocation MODEL = ResourceLocation.parse("amynnaspacemod:proxy/rock");
    private static final long YEAR = 1_728_000L * 365;
    private static final long MONTH = 1_728_000L * 27;

    private final StrategicLayer layer = new StrategicLayer(JdbcStrategicStore.inMemory());
    private final SpaceObject galaxy = layer.addObject(null, Archetypes.galaxy("Voie lactée", Transform.ORIGIN, null));
    private final SpaceObject sol = layer.addObject(galaxy.id(), Archetypes.system("Sol", Transform.at(2.6e17, 0, 0), null));
    private final SpaceObject sun = layer.addObject(sol.id(), Archetypes.star("Soleil", Transform.ORIGIN, null, new Render(MODEL, 7e8), 1.4e9));
    private final SpaceObject terra = layer.addObject(sol.id(), Archetypes.planet("Terra", RailFollower.orbit(YEAR, 1.5e8, 100, 0.1),
            new Render(MODEL, 6.4e6), ResourceLocation.parse("amynnaspacemod:terra"), 512, List.of()));
    private final SpaceObject luna = layer.addObject(terra.id(), Archetypes.planet("Luna", RailFollower.orbit(MONTH, 3.8e5, 7, 0.3),
            new Render(MODEL, 1.7e6), ResourceLocation.parse("amynnaspacemod:luna"), 256, List.of()));
    private final SpaceObject cruiser = layer.addObject(terra.id(), Archetypes.cruiser("Albatros",
            Transform.at(2.0e6, 1.0e5, -3.0e5), ResourceLocation.parse("amynnaspacemod:albatros"), 64));

    @Test
    void theTreeIsNavigableInBothDirections() {
        assertEquals(List.of(galaxy), layer.galaxies());
        assertEquals(java.util.Set.of(sol.id()), galaxy.children());
        assertEquals(java.util.Set.of(sun.id(), terra.id()), sol.children());
        assertEquals(java.util.Set.of(luna.id(), cruiser.id()), terra.children());
        assertEquals(java.util.Set.of(), sun.children());
        assertNull(layer.parentOf(galaxy.id()));
        assertSame(terra, layer.parentOf(cruiser.id()));
        assertSame(galaxy, layer.galaxyOf(cruiser.id()));
        assertSame(galaxy, layer.galaxyOf(galaxy.id()));
    }

    @Test
    void parentsAndChildrenListsAnswerEachOtherAfterEveryChange() {
        assertEquals(terra.id(), luna.parent());
        assertTrue(terra.children().contains(luna.id()));
        // changement de parent : l'ancien parent oublie l'enfant, le nouveau l'inscrit, l'enfant pointe vers le nouveau
        layer.reparent(luna.id(), sol.id(), 0, RailFollower.orbit(MONTH, 1e6, 0, 0));
        assertEquals(sol.id(), luna.parent());
        assertFalse(terra.children().contains(luna.id()));
        assertTrue(sol.children().contains(luna.id()));
        // un objet devenu galaxie n'a plus de parent et n'est plus listé nulle part
        layer.reparent(cruiser.id(), null, 0);
        assertNull(cruiser.parent());
        assertFalse(terra.children().contains(cruiser.id()));
        assertTrue(layer.galaxies().contains(cruiser));
        // et inversement
        layer.reparent(cruiser.id(), sol.id(), 0);
        assertTrue(sol.children().contains(cruiser.id()));
        assertFalse(layer.galaxies().contains(cruiser));
    }

    @Test
    void childrenCannotBeModifiedFromOutside() {
        assertThrows(UnsupportedOperationException.class, () -> sol.children().add(UUID.randomUUID()));
        assertThrows(UnsupportedOperationException.class, () -> sol.children().clear());
    }

    @Test
    void systemOfFindsTheNearestStellarSystem() {
        assertSame(sol, layer.systemOf(cruiser.id()));
        assertSame(sol, layer.systemOf(sol.id()));
        assertNull(layer.systemOf(galaxy.id()));
        sol.put(new StellarSystem(sun.id()));
        assertEquals(sun.id(), layer.systemOf(luna.id()).get(fr.systamynna.spacemod.strategic.component.ComponentTypes.STELLAR_SYSTEM).mainStar());
    }

    @Test
    void anObjectCanBeAddedUnderAnyParentButNotTwice() {
        SpaceObject station = Archetypes.station("Relais", RailFollower.orbit(72_000, 2_000, 0, 0),
                ResourceLocation.parse("amynnaspacemod:relais"), 128);
        layer.addObject(luna.id(), station);
        assertSame(luna, layer.parentOf(station.id()));
        assertThrows(IllegalArgumentException.class, () -> layer.addObject(terra.id(), station));
        assertThrows(IllegalArgumentException.class, () -> layer.addObject(UUID.randomUUID(), new SpaceObject(UUID.randomUUID())));
    }

    @Test
    void reparentKeepsTheAbsolutePositionOfAFreeObject() {
        double t = 123_456.75;
        double[] before = RailMath.positionAt(layer, cruiser, t);
        layer.reparent(cruiser.id(), luna.id(), t);
        assertSame(luna, layer.parentOf(cruiser.id()));
        assertTrue(luna.children().contains(cruiser.id()));
        assertFalse(terra.children().contains(cruiser.id()));
        double[] after = RailMath.positionAt(layer, cruiser, t);
        for (int i = 0; i < 3; i++) assertEquals(before[i], after[i], 1e-3);
        // le vaisseau est désormais relatif à la lune : il en suit le déplacement, au lieu de la voir s'éloigner
        double[] lunaThen = RailMath.positionAt(layer, luna, t);
        double[] lunaLater = RailMath.positionAt(layer, luna, t + 10_000);
        double[] later = RailMath.positionAt(layer, cruiser, t + 10_000);
        for (int i = 0; i < 3; i++) assertEquals(after[i] - lunaThen[i], later[i] - lunaLater[i], 1e-3);
    }

    @Test
    void reparentAcrossFramesOfDifferentScales() {
        double h = Math.sqrt(0.5);
        SpaceObject proxima = layer.addObject(galaxy.id(),
                Archetypes.system("Proxima", new Transform(-4e16, 5e15, 0, 0, 0, h, h, 1, 1, 1), null));
        double t = 99.5;
        double[] before = RailMath.positionAt(layer, cruiser, t);
        layer.reparent(cruiser.id(), proxima.id(), t);
        assertSame(proxima, layer.systemOf(cruiser.id()));
        double[] after = RailMath.positionAt(layer, cruiser, t);
        // les deux systèmes sont à ~1e17 l'un de l'autre : la précision relative d'un double (1e-16 de cette distance)
        // est la seule limite de la conversion, sur chaque coordonnée
        double tolerance = 1e-14 * Math.max(Math.abs(before[0]), Math.max(Math.abs(before[1]), Math.abs(before[2])));
        for (int i = 0; i < 3; i++) assertEquals(before[i], after[i], tolerance);
    }

    @Test
    void railObjectNeedsANewControllerToChangeParent() {
        assertThrows(IllegalArgumentException.class, () -> layer.reparent(luna.id(), sol.id(), 0));
        assertSame(terra, layer.parentOf(luna.id()), "refusé : rien n'a changé");

        layer.reparent(luna.id(), sol.id(), 0, RailFollower.orbit(MONTH, 1e6, 0, 0));
        assertSame(sol, layer.parentOf(luna.id()));
        assertEquals(1e6, ((RailFollower) luna.controller()).orbit().radius());
    }

    @Test
    void leavingARailPreservesPosition() {
        double t = 5_000;
        double[] before = RailMath.positionAt(layer, luna, t);
        layer.reparent(luna.id(), sol.id(), t, Pilotable.AT_REST);
        double[] after = RailMath.positionAt(layer, luna, t);
        for (int i = 0; i < 3; i++) assertEquals(before[i], after[i], 1e-3);
        assertEquals(after[0], RailMath.positionAt(layer, luna, t + 1_000)[0], "plus de rail : la lune est figée");
    }

    @Test
    void cyclesAndUnknownParentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> layer.reparent(terra.id(), luna.id(), 0, Pilotable.AT_REST));
        assertThrows(IllegalArgumentException.class, () -> layer.reparent(terra.id(), terra.id(), 0, Pilotable.AT_REST));
        assertThrows(IllegalArgumentException.class, () -> layer.reparent(cruiser.id(), UUID.randomUUID(), 0));
        assertThrows(IllegalArgumentException.class, () -> layer.reparent(UUID.randomUUID(), sol.id(), 0));
        assertSame(sol, layer.parentOf(terra.id()));
    }

    @Test
    void anObjectCanBecomeAGalaxyRoot() {
        double t = 10;
        double[] before = RailMath.positionAt(layer, cruiser, t);
        layer.reparent(cruiser.id(), null, t);
        assertNull(layer.parentOf(cruiser.id()));
        assertTrue(layer.galaxies().contains(cruiser));
        double[] after = RailMath.positionAt(layer, cruiser, t);
        for (int i = 0; i < 3; i++) assertEquals(before[i], after[i], 1e-3);
    }

    @Test
    void indestructibleObjectsRefuseDestruction() {
        assertThrows(IllegalArgumentException.class, () -> layer.destroy(terra.id()));
        assertThrows(IllegalArgumentException.class, () -> layer.destroy(sun.id()));
        assertThrows(IllegalArgumentException.class, () -> layer.restore(terra.id()));
        assertFalse(terra.isDestroyed());

        layer.destroy(cruiser.id());
        assertTrue(cruiser.isDestroyed());
        assertEquals(cruiser, layer.hostOf(ResourceLocation.parse("amynnaspacemod:albatros")), "la dimension reste attribuée");
        layer.restore(cruiser.id());
        assertFalse(cruiser.isDestroyed());
    }
}
