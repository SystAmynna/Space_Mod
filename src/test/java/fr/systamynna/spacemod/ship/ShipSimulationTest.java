package fr.systamynna.spacemod.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.strategic.Archetypes;
import fr.systamynna.spacemod.strategic.SpaceObject;
import fr.systamynna.spacemod.strategic.StrategicLayer;
import fr.systamynna.spacemod.strategic.component.Pilotable;
import fr.systamynna.spacemod.strategic.component.Render;
import fr.systamynna.spacemod.strategic.component.Transform;
import fr.systamynna.spacemod.strategic.store.JdbcStrategicStore;
import net.minecraft.resources.ResourceLocation;

/** Un tick de vie d'un vaisseau : dérive, contacts, navette, re-parentage. Astres A (origine) et B (x = 10 000), exclusion 100. */
class ShipSimulationTest {
    private static final ResourceLocation MODEL = ResourceLocation.parse("amynnaspacemod:sphere");
    private static final ShipFlight.Limits LIMITS = new ShipFlight.Limits(1e6, 10, 0.1, 0.1);

    private StrategicLayer layer;
    private SpaceObject a;
    private SpaceObject b;

    @BeforeEach
    void setUp() {
        layer = new StrategicLayer(JdbcStrategicStore.inMemory());
        SpaceObject galaxy = layer.addObject(null, Archetypes.galaxy("g", Transform.ORIGIN, null));
        SpaceObject system = layer.addObject(galaxy.id(), Archetypes.system("s", Transform.ORIGIN, null));
        a = layer.addObject(system.id(), Archetypes.star("A", Transform.ORIGIN, null, new Render(MODEL, 50), 100));
        b = layer.addObject(system.id(), Archetypes.star("B", Transform.at(10_000, 0, 0), null, new Render(MODEL, 50), 100));
    }

    @AfterEach
    void tearDown() {
        layer.close();
    }

    private boolean tick(SpaceObject ship, boolean checkParent) {
        return ShipSimulation.tick(layer, ship, ShipFlight.Input.ZERO, LIMITS, 0, checkParent, 0.2);
    }

    @Test
    void releasedShipDriftsAtConstantVelocity() {
        SpaceObject ship = ShipManager.spawn(layer, "Alpha", a, true, 5000);
        double x = ship.transform().tx();
        ship.controller(new Pilotable(0, 7, 0));
        for (int i = 0; i < 3; i++) assertTrue(tick(ship, false));
        assertEquals(21, ship.transform().ty(), 1e-9);
        assertEquals(x, ship.transform().tx(), 1e-9);
    }

    @Test
    void largeShipIsStoppedByExclusionButShuttlePassesThrough() {
        SpaceObject large = ShipManager.spawn(layer, "Large", a, true, 0);
        SpaceObject shuttle = ShipManager.spawn(layer, "Navette", a, false, 0);
        double start = large.transform().tx();
        large.controller(new Pilotable(-500, 0, 0));
        shuttle.controller(new Pilotable(-500, 0, 0));
        assertFalse(tick(large, false), "arrêté par la zone");
        assertEquals(start, large.transform().tx(), 1e-6);
        double shuttleStart = shuttle.transform().tx();
        assertTrue(tick(shuttle, false));
        assertEquals(shuttleStart - 500, shuttle.transform().tx(), 1e-9);
    }

    @Test
    void reparentsToTheNearestBodyWithoutJumping() {
        SpaceObject ship = ShipManager.spawn(layer, "Alpha", a, true, 0);
        ship.put(Transform.at(8000, 0, 0));
        ship.controller(new Pilotable(1, 0, 0));
        assertTrue(tick(ship, true));
        assertEquals(b.id(), ship.parent());
        // position absolue 8001, B à 10 000
        assertEquals(-1999, ship.transform().tx(), 1e-9);
    }

    @Test
    void hysteresisKeepsTheParentWhenBarelyCloser() {
        SpaceObject ship = ShipManager.spawn(layer, "Alpha", a, true, 0);
        ship.put(Transform.at(5100, 0, 0));
        ship.controller(new Pilotable(1, 0, 0));
        tick(ship, true);
        assertEquals(a.id(), ship.parent());
    }
}
