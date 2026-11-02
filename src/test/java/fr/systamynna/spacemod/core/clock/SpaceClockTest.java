package fr.systamynna.spacemod.core.clock;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SpaceClockTest {
    private static final long MS = 1_000_000L;

    @Test
    void multipliers() {
        SpaceClock c = new SpaceClock();
        long b = c.state().baseNanos();
        assertEquals(1000, c.timeAt(b + 1000 * MS), 1e-6);
        c.reset(0, 0.25);
        b = c.state().baseNanos();
        assertEquals(250, c.timeAt(b + 1000 * MS), 1e-6);
        c.reset(10, 5.3);
        b = c.state().baseNanos();
        assertEquals(10 + 5300, c.timeAt(b + 1000 * MS), 1e-6);
        c.reset(10, 0);
        b = c.state().baseNanos();
        assertEquals(10, c.timeAt(b + 1000 * MS), 1e-6);
    }

    @Test
    void setMultiplierKeepsTimeContinuous() {
        SpaceClock c = new SpaceClock();
        double before = c.now();
        c.setMultiplier(0);
        assertEquals(before, c.now(), 50);
        double frozen = c.now();
        assertEquals(frozen, c.now(), 1e-9);
    }

    @Test
    void rejectsInvalid() {
        SpaceClock c = new SpaceClock();
        assertThrows(IllegalArgumentException.class, () -> c.setMultiplier(-1));
        assertThrows(IllegalArgumentException.class, () -> c.setMultiplier(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> c.setMultiplier(Double.POSITIVE_INFINITY));
    }

    @Test
    void pongKeepsSmallestRtt() {
        SpaceClock c = new SpaceClock();
        // serveur = local + 1000 ms, rtt 100 ms : le serveur répond à local 50 -> nanos serveur 1050
        c.applyPong(0, 1050 * MS, 100 * MS);
        assertEquals(1000 * MS, c.offset());
        c.applyPong(0, 1500 * MS, 400 * MS); // rtt plus grand : ignoré
        assertEquals(1000 * MS, c.offset());
    }
}
