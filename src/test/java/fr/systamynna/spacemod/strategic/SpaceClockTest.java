package fr.systamynna.spacemod.strategic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fr.systamynna.spacemod.strategic.store.JdbcStrategicStore;

class SpaceClockTest {
    @TempDir
    Path dir;

    @Test
    void realTimeByDefault() {
        SpaceClock clock = new SpaceClock();
        assertEquals(1.0, clock.rate());
        assertEquals(0, clock.spaceTime(0));
        assertEquals(123_456, clock.spaceTime(123_456));
        assertEquals(10.25, clock.spaceTime(10.25), "sous-tick, pour le rendu entre deux ticks");
    }

    @Test
    void fractionalAndZeroRates() {
        SpaceClock clock = new SpaceClock();
        clock.setRate(0.5, 0);
        assertEquals(50, clock.spaceTime(100));
        clock.setRate(2.5, 0);
        assertEquals(250, clock.spaceTime(100));
        clock.setRate(0, 0);
        assertEquals(0, clock.spaceTime(100_000));
    }

    @Test
    void changingTheRateDoesNotJump() {
        SpaceClock clock = new SpaceClock();
        clock.setRate(3, 100);
        double before = clock.spaceTime(1_000);
        clock.setRate(0.25, 1_000);
        assertEquals(before, clock.spaceTime(1_000));
        assertEquals(before + 25, clock.spaceTime(1_100));
        clock.setRate(0, 1_100);
        double frozen = clock.spaceTime(1_100);
        assertEquals(frozen, clock.spaceTime(9_999_999));
        clock.setRate(1, 5_000_000);
        assertEquals(frozen + 10, clock.spaceTime(5_000_010), "le temps reprend là où il s'était arrêté");
    }

    @Test
    void invalidRatesAreRejected() {
        SpaceClock clock = new SpaceClock();
        for (double bad : new double[] { -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            assertThrows(IllegalArgumentException.class, () -> clock.setRate(bad, 0), "rythme " + bad);
        }
        assertEquals(1.0, clock.rate());
    }

    @Test
    void survivesARestart() {
        StrategicLayer layer = new StrategicLayer(JdbcStrategicStore.sqlite(dir.resolve("strategic.db")));
        layer.clock().setRate(0.5, 2_000);
        double before = layer.clock().spaceTime(4_000);
        layer.save();
        assertFalse(layer.clock().needsSave());
        layer.close();

        // « redémarrage » : nouvelle couche sur la même base
        SpaceClock reloaded = new StrategicLayer(JdbcStrategicStore.sqlite(dir.resolve("strategic.db"))).clock();
        assertEquals(0.5, reloaded.rate());
        assertEquals(before, reloaded.spaceTime(4_000));
        assertEquals(before + 100, reloaded.spaceTime(4_200));
    }

    @Test
    void clockIsOnlyRewrittenWhenItChanged() {
        StrategicLayer layer = new StrategicLayer(JdbcStrategicStore.sqlite(dir.resolve("strategic.db")));
        layer.save();
        assertFalse(layer.clock().needsSave());
        layer.clock().setRate(2, 10);
        assertTrue(layer.clock().needsSave());
    }

    @Test
    void gameTimeGoingBackwardsReanchorsInsteadOfFreezingOrGoingNegative() {
        SpaceClock clock = new SpaceClock();
        clock.setRate(2, 1_000_000);
        assertEquals(1_001_000, clock.spaceTime(1_000_500));
        // le temps de l'Overworld a été réinitialisé : l'horloge repart de la valeur de son ancre (1 000 000)
        assertEquals(1_000_000, clock.spaceTime(10));
        assertEquals(1_000_180, clock.spaceTime(100));
        assertTrue(clock.needsSave());
    }
}
