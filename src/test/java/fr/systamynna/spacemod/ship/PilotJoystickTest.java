package fr.systamynna.spacemod.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Zone morte, déflexion et bord du cône du joystick virtuel. */
class PilotJoystickTest {
    @Test
    void deadZoneGivesNoCommand() {
        PilotJoystick stick = new PilotJoystick();
        stick.move(1.5, 0);
        assertEquals(0, stick.yaw(), 1e-12);
        assertEquals(0, stick.pitch(), 1e-12);
    }

    @Test
    void deflectionGrowsThenSaturatesAtTheConeEdge() {
        PilotJoystick stick = new PilotJoystick();
        stick.move(16, 0);
        assertEquals((16 - 2) / 28.0, stick.yaw(), 1e-12);
        stick.move(1000, 0);
        assertEquals(1, stick.yaw(), 1e-12);
    }

    @Test
    void mouseUpRaisesTheNoseAndMouseRightTurnsRight() {
        PilotJoystick stick = new PilotJoystick();
        stick.move(0, -20);
        assertTrue(stick.pitch() > 0);
        assertEquals(0, stick.yaw(), 1e-12);
        stick.reset();
        stick.move(20, 0);
        assertTrue(stick.yaw() > 0);
    }
}
