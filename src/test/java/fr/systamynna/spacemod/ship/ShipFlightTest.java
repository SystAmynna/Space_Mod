package fr.systamynna.spacemod.ship;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.ship.ShipFlight.Input;
import fr.systamynna.spacemod.ship.ShipFlight.Limits;
import fr.systamynna.spacemod.ship.ShipFlight.State;
import fr.systamynna.spacemod.strategic.component.Transform;

/** Vol inertiel : poussée, freinage, marche arrière à deux appuis, rotations. */
class ShipFlightTest {
    private static final Limits LIMITS = new Limits(100, 10, 0.1, 0.2);
    private static final double EPS = 1e-9;

    private static State at(double vx, double vy, double vz, boolean locked) {
        return new State(Transform.ORIGIN, new double[] { vx, vy, vz }, locked);
    }

    private static Input throttle(double t) {
        return new Input(t, 0, 0, 0, false);
    }

    private static double[] forward(State s) {
        return s.pose().linear(new double[] { 0, 0, -1 });
    }

    @Test
    void throttleAcceleratesAlongMinusZAndIsCapped() {
        State s = ShipFlight.step(at(0, 0, 0, false), throttle(1), LIMITS);
        assertArrayEquals(new double[] { 0, 0, -10 }, s.velocity(), EPS);
        assertEquals(-10, s.pose().tz(), EPS);
        for (int i = 0; i < 50; i++) s = ShipFlight.step(s, throttle(1), LIMITS);
        assertEquals(100, ShipFlight.norm(s.velocity()), EPS);
    }

    @Test
    void noInputKeepsVelocity() {
        State s = ShipFlight.step(at(1, 2, 3, false), Input.ZERO, LIMITS);
        assertArrayEquals(new double[] { 1, 2, 3 }, s.velocity(), EPS);
        assertEquals(1, s.pose().tx(), EPS);
    }

    @Test
    void backStopsAtZeroThenNeedsANewPressToReverse() {
        State s = at(0, 0, -15, false);
        s = ShipFlight.step(s, throttle(-1), LIMITS);
        assertEquals(-5, s.velocity()[2], EPS);
        s = ShipFlight.step(s, throttle(-1), LIMITS);
        assertEquals(0, ShipFlight.norm(s.velocity()), EPS);
        assertTrue(s.reverseLocked());
        s = ShipFlight.step(s, throttle(-1), LIMITS);
        assertEquals(0, ShipFlight.norm(s.velocity()), EPS);
        s = ShipFlight.step(s, Input.ZERO, LIMITS);
        assertFalse(s.reverseLocked());
        s = ShipFlight.step(s, throttle(-1), LIMITS);
        assertEquals(10, s.velocity()[2], EPS);
    }

    @Test
    void brakeBringsSpeedBackToZeroFromBothDirections() {
        Input brake = new Input(0, 0, 0, 0, true);
        assertEquals(-15, ShipFlight.step(at(0, 0, -25, false), brake, LIMITS).velocity()[2], EPS);
        assertEquals(5, ShipFlight.step(at(0, 0, 15, false), brake, LIMITS).velocity()[2], EPS);
        assertEquals(0, ShipFlight.norm(ShipFlight.step(at(0, 0, 4, false), brake, LIMITS).velocity()), EPS);
    }

    @Test
    void yawPitchRollSigns() {
        State s = at(0, 0, 0, false);
        assertTrue(forward(ShipFlight.step(s, new Input(0, 0, 1, 0, false), LIMITS))[0] > 0, "lacet à droite : le nez va vers +X");
        assertTrue(forward(ShipFlight.step(s, new Input(0, 0, 0, 1, false), LIMITS))[1] > 0, "tangage haut : le nez monte");
        State rolled = ShipFlight.step(s, new Input(0, 1, 0, 0, false), LIMITS);
        double[] right = rolled.pose().linear(new double[] { 1, 0, 0 });
        assertTrue(right[1] < 0, "roulis droit : l'aile droite descend");
    }

    @Test
    void orientationStaysNormalized() {
        State s = at(0, 0, 0, false);
        for (int i = 0; i < 10_000; i++) s = ShipFlight.step(s, new Input(0.3, 1, -0.7, 0.4, false), LIMITS);
        Transform t = s.pose();
        assertEquals(1, Math.sqrt(t.qx() * t.qx() + t.qy() * t.qy() + t.qz() * t.qz() + t.qw() * t.qw()), 1e-9);
    }
}
