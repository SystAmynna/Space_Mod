package fr.systamynna.spacemod.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Hystérésis du choix de l'astre parent. */
class ShipNavigatorTest {
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    void staysWhenNotClearlyCloser() {
        assertNull(ShipNavigator.chooseParent(a, Map.of(a, 100.0, b, 90.0), 0.2));
        assertNull(ShipNavigator.chooseParent(a, Map.of(a, 100.0, b, 80.0), 0.2));
    }

    @Test
    void switchesWhenClearlyCloser() {
        assertEquals(b, ShipNavigator.chooseParent(a, Map.of(a, 100.0, b, 70.0), 0.2));
    }

    @Test
    void nothingToChangeWhenCurrentIsNearest() {
        assertNull(ShipNavigator.chooseParent(a, Map.of(a, 10.0, b, 70.0), 0.2));
    }

    @Test
    void parentOutsideTheCandidatesYieldsToTheNearest() {
        assertEquals(b, ShipNavigator.chooseParent(UUID.randomUUID(), Map.of(a, 100.0, b, 90.0), 0.2));
        assertNull(ShipNavigator.chooseParent(a, Map.of(), 0.2));
    }
}
