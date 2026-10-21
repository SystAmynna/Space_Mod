package fr.systamynna.spacemod.strategic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.strategic.component.Pilotable;
import fr.systamynna.spacemod.strategic.component.RailFollower;
import fr.systamynna.spacemod.strategic.component.Transform;
import fr.systamynna.spacemod.strategic.store.JdbcStrategicStore;

/** Rejeu de B1 (strategic-tests/B1.java, test-results/B1) sur le code de production, puis temps continu et cadres. */
class RailMathTest {
    private static final long DAY = 1_728_000L;

    private final StrategicLayer layer = new StrategicLayer(JdbcStrategicStore.inMemory());
    private final SpaceObject galaxy = layer.addObject(null, Archetypes.galaxy("Voie lactée", Transform.ORIGIN, null));
    private final SpaceObject system = layer.addObject(galaxy.id(), Archetypes.system("Sol", Transform.ORIGIN, null));
    private final SpaceObject star = layer.addObject(system.id(),
            Archetypes.star("Soleil", Transform.ORIGIN, null, new fr.systamynna.spacemod.strategic.component.Render(
                    net.minecraft.resources.ResourceLocation.parse("amynnaspacemod:proxy/rock"), 1), 10));
    private final SpaceObject planet = rail(system, DAY * 365, 1.5e8, 12345, 0.1);
    private final SpaceObject moon = rail(planet, DAY * 27, 3.8e5, 777, 0.3);
    private final SpaceObject station = rail(moon, 72_000L, 2.0e3, 5, 0.9);

    private SpaceObject rail(SpaceObject parent, long period, double radius, long phase, double inclination) {
        return layer.addObject(parent.id(), new SpaceObject(UUID.randomUUID())
                .controller(RailFollower.orbit(period, radius, phase, inclination)));
    }

    private double[] stationAt(double ticks) {
        return RailMath.positionAt(layer, station, ticks);
    }

    @Test
    void accumulationDriftsButModuloDoesNot() {
        RailFollower.Orbit moonRail = ((RailFollower) moon.controller()).orbit();
        long horizon = 1_000_000_000L;
        double step = 2 * Math.PI / moonRail.period();
        double accumulated = 2 * Math.PI * ((double) (moonRail.phaseTicks() % moonRail.period()) / moonRail.period());
        for (long t = 0; t < horizon; t++) accumulated += step;

        double exact = 2 * Math.PI * RailMath.fraction(horizon, moonRail.phaseTicks(), moonRail.period());
        double drift = Math.abs(Math.IEEEremainder(accumulated - exact, 2 * Math.PI));
        // B1 : 0,91 bloc à 10^9 ticks pour un rayon de 380 000
        assertTrue(drift * moonRail.radius() > 0.5, "l'accumulation devait dériver, dérive = " + drift * moonRail.radius());

        // le modulo ne dérive pas : t et t + k·période donnent exactement la même fraction
        for (long k : new long[] { 1, 1000, 1_000_000_000L }) {
            assertEquals(RailMath.fraction(12345, moonRail.phaseTicks(), moonRail.period()),
                    RailMath.fraction(12345 + k * moonRail.period(), moonRail.phaseTicks(), moonRail.period()));
        }
    }

    @Test
    void nestedPositionIsPeriodicAtHugeTicks() {
        long common = lcm(lcm(DAY * 365, DAY * 27), 72_000L);
        Random r = new Random(42);
        for (int i = 0; i < 10_000; i++) {
            long t = Math.abs(r.nextLong()) % 1_000_000_000_000_000L;
            long k = 1 + r.nextInt(1000);
            double[] p1 = stationAt(t);
            double[] p2 = stationAt(t + k * common);
            assertEquals(p1[0], p2[0]);
            assertEquals(p1[1], p2[1]);
            assertEquals(p1[2], p2[2]);
        }
    }

    @Test
    void fingerprintMatchesPrototypeB1() throws Exception {
        // même suite de tirages que B1.main : 10^5 tirages de la section « stabilité », puis l'échantillon
        Random r = new Random(42);
        for (int i = 0; i < 100_000; i++) {
            r.nextLong();
            r.nextInt(1000);
        }
        long[] sample = new long[200_000];
        for (int i = 0; i < sample.length; i++) sample[i] = Math.abs(r.nextLong()) % 1_000_000_000_000_000L;

        long reference = fingerprint(sample);
        assertEquals(0x05e3acde9b5201f7L, reference, "empreinte StrictMath de test-results/B1/jvm21.txt");

        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (int i = 0; i < 32; i++) futures.add(pool.submit(() -> fingerprint(sample)));
            for (Future<Long> f : futures) assertEquals(reference, f.get());
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void negativeTicksStayInRange() {
        for (long t : new long[] { -1, -1_000_000_007L, Long.MIN_VALUE / 2 }) {
            double f = RailMath.fraction(t, 5, 72_000L);
            assertTrue(f >= 0 && f < 1, "fraction hors de [0, 1) : " + f);
        }
    }

    @Test
    void objectWithoutRailUsesItsTransform() {
        SpaceObject fixed = layer.addObject(system.id(), new SpaceObject(UUID.randomUUID()).put(Transform.at(1, 2, 3)).controller(Pilotable.AT_REST));
        SpaceObject still = layer.addObject(system.id(), new SpaceObject(UUID.randomUUID()).put(Transform.at(4, 5, 6)));
        assertArrayEquals(new double[] { 1, 2, 3 }, RailMath.positionAt(layer, fixed, 99));
        assertArrayEquals(new double[] { 4, 5, 6 }, RailMath.positionAt(layer, still, 99));
    }

    @Test
    void invalidPeriodIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RailFollower.orbit(0, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> RailFollower.spin(-5, 0));
    }

    @Test
    void integerTimeAsDoubleGivesTheSameBitsAsLongTime() {
        Random r = new Random(7);
        for (int i = 0; i < 1_000; i++) {
            long t = Math.abs(r.nextLong()) % 1_000_000_000_000_000L;
            assertEquals(RailMath.fraction(t, 5, 72_000L), RailMath.fraction((double) t, 5, 72_000L));
        }
    }

    @Test
    void motionIsContinuousBetweenTicks() {
        // la station fait un tour de 2 000 blocs de rayon en 72 000 ticks : au plus 2 000·2π/72 000 ≈ 0,175 bloc par tick
        double maxSpeed = 2.0e3 * 2 * Math.PI / 72_000L + 1e-9;
        double[] previous = stationAt(1_000_000.0);
        // la lune (rayon 3,8e5, période 27 jours) contribue aussi : 3,8e5·2π/(27·1 728 000) ≈ 0,051 bloc par tick
        double moonSpeed = 3.8e5 * 2 * Math.PI / (DAY * 27);
        double planetSpeed = 1.5e8 * 2 * Math.PI / (DAY * 365);
        double bound = (maxSpeed + moonSpeed + planetSpeed) / 100 * 1.001;
        for (int i = 1; i <= 5_000; i++) {
            double[] now = stationAt(1_000_000.0 + i / 100.0);
            double step = Math.sqrt(Math.pow(now[0] - previous[0], 2) + Math.pow(now[1] - previous[1], 2) + Math.pow(now[2] - previous[2], 2));
            assertTrue(step <= bound, "saut de " + step + " bloc entre deux échantillons à 0,01 tick");
            assertTrue(step > 0, "la station doit bouger entre deux échantillons");
            previous = now;
        }
    }

    @Test
    void movementCrossesTheTickBoundaryWithoutAJump() {
        double[] before = stationAt(1_000.999999);
        double[] at = stationAt(1_001.0);
        double[] after = stationAt(1_001.000001);
        for (int i = 0; i < 3; i++) {
            assertEquals(before[i], at[i], 1e-3);
            assertEquals(at[i], after[i], 1e-3);
        }
    }

    @Test
    void spinRotatesTheLocalTransformSmoothly() {
        SpaceObject spinning = layer.addObject(system.id(), new SpaceObject(UUID.randomUUID()).controller(RailFollower.spin(1_000, 0)));
        Transform quarter = RailMath.local(spinning, 250);
        double h = Math.sqrt(0.5);
        assertEquals(h, quarter.qy(), 1e-12);
        assertEquals(h, quarter.qw(), 1e-12);
        Transform half = RailMath.local(spinning, 500);
        assertEquals(1, half.qy(), 1e-12);
        assertEquals(0, RailMath.local(spinning, 0).qy(), 1e-12);
    }

    @Test
    void framesConvertWhileBodiesOnlyPassTheirTranslation() {
        // système à l'échelle 1e3 (1 unité de système = 1 000 unités de galaxie), tourné de 90° autour de Z, décalé de (10, 0, 0)
        double h = Math.sqrt(0.5);
        SpaceObject scaled = layer.addObject(galaxy.id(), Archetypes.system("Proxima", new Transform(10, 0, 0, 0, 0, h, h, 1e3, 1e3, 1e3), null));
        SpaceObject body = layer.addObject(scaled.id(), new SpaceObject(UUID.randomUUID()).put(Transform.at(1, 0, 0)
                .withRotation(0, 0, h, h)));
        SpaceObject child = layer.addObject(body.id(), new SpaceObject(UUID.randomUUID()).put(Transform.at(0, 1, 0)));

        double[] bodyPos = RailMath.positionAt(layer, body, 0);
        assertEquals(10, bodyPos[0], 1e-9);
        assertEquals(1e3, bodyPos[1], 1e-9);
        // la rotation propre du corps (90° aussi) n'entraîne pas son enfant : celui-ci est à (1, 1, 0) dans le système
        // (translation du corps + la sienne), soit (-990, 1000, 0) dans la galaxie après la conversion du cadre
        double[] childPos = RailMath.positionAt(layer, child, 0);
        assertEquals(10 - 1e3, childPos[0], 1e-9);
        assertEquals(1e3, childPos[1], 1e-9);
    }

    @Test
    void relativePositionKeepsPrecisionAcrossScales() {
        // deux vaisseaux à 1 bloc l'un de l'autre, à 1e20 de l'origine de la galaxie
        SpaceObject far = layer.addObject(galaxy.id(), Archetypes.system("Lointain", Transform.at(1e20, 0, 0), null));
        SpaceObject a = layer.addObject(far.id(), new SpaceObject(UUID.randomUUID()).put(Transform.at(3.25, 0, 0)));
        SpaceObject b = layer.addObject(far.id(), new SpaceObject(UUID.randomUUID()).put(Transform.at(2.25, 0, 0)));
        double[] relative = RailMath.relativePosition(layer, a, b, 0);
        assertEquals(1.0, relative[0]);
        // le calcul global, lui, a perdu l'unité
        assertEquals(0.0, RailMath.positionAt(layer, a, 0)[0] - RailMath.positionAt(layer, b, 0)[0]);
    }

    @Test
    void invalidTimeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RailMath.fraction(Double.NaN, 0, 100));
        assertThrows(IllegalArgumentException.class, () -> RailMath.fraction(Double.POSITIVE_INFINITY, 0, 100));
    }

    private long fingerprint(long[] sample) {
        long h = 1125899906842597L;
        for (long t : sample) {
            for (double v : stationAt(t)) h = 31 * h + Double.doubleToLongBits(v);
        }
        return h;
    }

    private static long gcd(long a, long b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    private static long lcm(long a, long b) {
        return a / gcd(a, b) * b;
    }
}
