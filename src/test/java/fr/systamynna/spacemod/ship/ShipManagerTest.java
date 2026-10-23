package fr.systamynna.spacemod.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fr.systamynna.spacemod.strategic.Archetypes;
import fr.systamynna.spacemod.strategic.SpaceObject;
import fr.systamynna.spacemod.strategic.StrategicLayer;
import fr.systamynna.spacemod.strategic.component.ComponentTypes;
import fr.systamynna.spacemod.strategic.component.Pilotable;
import fr.systamynna.spacemod.strategic.component.Render;
import fr.systamynna.spacemod.strategic.component.Transform;
import fr.systamynna.spacemod.strategic.store.JdbcStrategicStore;
import net.minecraft.resources.ResourceLocation;

/** Création, unicité, persistance et suppression des vaisseaux. */
class ShipManagerTest {
    private static final ResourceLocation MODEL = ResourceLocation.parse("amynnaspacemod:sphere");

    @TempDir
    Path dir;

    private StrategicLayer layer;
    private SpaceObject system;
    private SpaceObject moon;

    private StrategicLayer open() {
        if (layer != null) layer.close();
        return layer = new StrategicLayer(JdbcStrategicStore.sqlite(dir.resolve("strategic.db")));
    }

    @BeforeEach
    void setUp() {
        open();
        SpaceObject galaxy = layer.addObject(null, Archetypes.galaxy("g", Transform.ORIGIN, null));
        system = layer.addObject(galaxy.id(), Archetypes.system("s", Transform.ORIGIN, null));
        moon = layer.addObject(system.id(), Archetypes.star("lune", Transform.ORIGIN, null, new Render(MODEL, 100), 300));
        layer.save();
    }

    @AfterEach
    void tearDown() {
        layer.close();
    }

    @Test
    void shipIsParentedToOtherAndPlacedOutsideItsExclusion() {
        SpaceObject ship = ShipManager.spawn(layer, "UFC-077-Deep_Explorer", moon, true, 50);
        assertEquals(moon.id(), ship.parent());
        assertEquals("ship:UFC-077-Deep_Explorer", ship.get(ComponentTypes.IDENTITY).id());
        assertTrue(ship.controller() instanceof Pilotable);
        // exclusion de la lune (300) + exclusion du vaisseau (2 x 512) + marge
        assertEquals(300 + 1024 + 50, ship.transform().tx());
    }

    @Test
    void nameIsUniqueIgnoringCase() {
        ShipManager.spawn(layer, "Alpha", moon, true, 0);
        assertThrows(IllegalArgumentException.class, () -> ShipManager.spawn(layer, "ALPHA", moon, true, 0));
        assertThrows(IllegalArgumentException.class, () -> ShipManager.spawn(layer, "mauvais nom", moon, true, 0));
    }

    @Test
    void parentMustBeABodyWithExclusion() {
        assertThrows(IllegalArgumentException.class, () -> ShipManager.spawn(layer, "A", system, true, 0));
    }

    @Test
    void shipSurvivesReopeningAndUnloading() {
        ShipManager.spawn(layer, "Alpha", moon, false, 0);
        open();
        assertEquals(List.of("Alpha"), ShipRegistry.names(layer));
        SpaceObject ship = ShipRegistry.find(layer, "alpha");
        assertNotNull(ship);
        assertEquals(Archetypes.SHUTTLE, ship.get(ComponentTypes.IDENTITY).archetype());
        layer.unloadIdleUnits(0, java.util.Set.of());
        assertFalse(layer.isLoaded(ship.id()));
        assertNotNull(ShipRegistry.find(layer, "Alpha"));
    }

    @Test
    void despawnRemovesRowIndexAndChildLink() {
        SpaceObject ship = ShipManager.spawn(layer, "Alpha", moon, true, 0);
        ShipManager.despawn(layer, "Alpha");
        assertNull(layer.find(ship.id()));
        assertFalse(layer.parentOf(moon.id()).children().contains(ship.id()));
        assertTrue(ShipRegistry.names(layer).isEmpty());
        open();
        assertNull(layer.find(ship.id()));
        assertThrows(IllegalArgumentException.class, () -> ShipManager.despawn(layer, "Alpha"));
    }

    @Test
    void despawnRefusesShipWithChildren() {
        SpaceObject first = ShipManager.spawn(layer, "Alpha", moon, true, 0);
        ShipManager.spawn(layer, "Beta", first, true, 0);
        assertThrows(IllegalArgumentException.class, () -> ShipManager.despawn(layer, "Alpha"));
    }
}
