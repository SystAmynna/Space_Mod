import java.util.*;
import java.util.concurrent.*;

/**
 * B1 — Rails et temps commun : déterminisme (prototype hors jeu).
 * Orbite = f(ticks entier long) : phase par modulo exact de la période, jamais accumulée.
 * Usage : java B1.java [accumulation-1e12]   (le dernier argument active l'accumulation sur 10^12 ticks, ~20 min)
 * Sortie : tableaux d'écarts et empreinte (hash) des positions pour comparaison entre machines virtuelles.
 */
public class B1 {
    record Orbit(long period, double radius, long phaseTicks, double inclination) {
        // angle exact : entier long ramené modulo la période, puis un seul calcul flottant
        double angle(long ticks, boolean strict) {
            long m = Math.floorMod(ticks + phaseTicks, period);
            return 2 * Math.PI * ((double) m / (double) period);
        }
        double[] pos(long ticks, boolean strict) {
            double a = angle(ticks, strict);
            double c = strict ? StrictMath.cos(a) : Math.cos(a), s = strict ? StrictMath.sin(a) : Math.sin(a);
            double ci = strict ? StrictMath.cos(inclination) : Math.cos(inclination), si = strict ? StrictMath.sin(inclination) : Math.sin(inclination);
            return new double[] { radius * c, radius * s * ci, radius * s * si };
        }
    }

    static final Orbit STAR_PLANET = new Orbit(1_728_000L * 365, 1.5e8, 12345, 0.1);   // période 1 an de jeu (en ticks)
    static final Orbit PLANET_MOON = new Orbit(1_728_000L * 27, 3.8e5, 777, 0.3);       // lune ~27 jours
    static final Orbit MOON_STATION = new Orbit(72_000L, 2.0e3, 5, 0.9);                // station, 1 heure

    static double[] nested(long ticks, boolean strict) {
        double[] a = STAR_PLANET.pos(ticks, strict), b = PLANET_MOON.pos(ticks, strict), c = MOON_STATION.pos(ticks, strict);
        return new double[] { a[0] + b[0] + c[0], a[1] + b[1] + c[1], a[2] + b[2] + c[2] };
    }

    static long hash(boolean strict, long[] sample) {
        long h = 1125899906842597L;
        for (long t : sample) for (double v : nested(t, strict)) h = 31 * h + Double.doubleToLongBits(v);
        return h;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("JVM " + System.getProperty("java.version") + " " + System.getProperty("os.arch"));
        long[] horizons = { 1_000_000L, 1_000_000_000L, 1_000_000_000_000L };

        // 1) modulo contre accumulation (flottant 32 bits, flottant 64 bits) sur l'angle de l'orbite Lune/planète
        Orbit o = PLANET_MOON;
        System.out.println("\n== écart d'angle (radians) : accumulation par tick contre modulo exact ==");
        for (long h : horizons) {
            if (h == 1_000_000_000_000L && !(args.length > 0 && args[0].equals("accumulation-1e12"))) {
                System.out.println("10^12 ticks : accumulation non exécutée (option accumulation-1e12 requise)");
                continue;
            }
            float af = 0; double ad = 0;
            double step = 2 * Math.PI / o.period();
            float stepF = (float) step;
            long start = o.phaseTicks();
            ad = 2 * Math.PI * ((double) (start % o.period()) / o.period());
            af = (float) ad;
            for (long t = 0; t < h; t++) { af += stepF; ad += step; }
            double ref = o.angle(h, true);
            // comparaison modulo 2π
            double dd = wrap(ad - ref), df = wrap(af - ref);
            System.out.printf("%,d ticks : double accumulé écart=%.3e rad (%.3e blocs au rayon %.0f) ; float accumulé écart=%.3e rad (%.3e blocs)%n",
                h, dd, Math.abs(dd) * o.radius(), o.radius(), df, Math.abs(df) * o.radius());
        }

        // 2) stabilité du modulo aux grandes valeurs : écart entre ticks et ticks + k*période (doit être nul à l'ulp près)
        System.out.println("\n== stabilité du modulo aux grandes valeurs ==");
        double worst = 0;
        Random r = new Random(42);
        for (int i = 0; i < 100000; i++) {
            long t = Math.abs(r.nextLong()) % 1_000_000_000_000_000L;
            long k = 1 + r.nextInt(1000);
            double[] p1 = nested(t, true), p2 = nested(t + k * lcm3(), true);
            for (int j = 0; j < 3; j++) worst = Math.max(worst, Math.abs(p1[j] - p2[j]));
        }
        System.out.printf("écart max entre t et t + k·(multiple commun des périodes), 10^5 tirages jusqu'à 10^15 ticks : %.3e blocs%n", worst);

        // 3) déterminisme : fils multiples et exécutions répétées (même JVM)
        long[] sample = new long[200000];
        for (int i = 0; i < sample.length; i++) sample[i] = Math.abs(r.nextLong()) % 1_000_000_000_000_000L;
        long ref = hash(true, sample), refFast = hash(false, sample);
        ExecutorService ex = Executors.newFixedThreadPool(16);
        List<Future<long[]>> fs = new ArrayList<>();
        for (int i = 0; i < 64; i++) fs.add(ex.submit(() -> new long[] { hash(true, sample), hash(false, sample) }));
        boolean same = true;
        for (Future<long[]> f : fs) { long[] v = f.get(); same &= v[0] == ref && v[1] == refFast; }
        ex.shutdown();
        System.out.println("\n== déterminisme (64 tâches sur 16 fils, même JVM) : " + (same ? "identique" : "DIFFÉRENT"));
        System.out.printf("EMPREINTE StrictMath=%016x  Math=%016x%n", ref, refFast);
    }

    static double wrap(double a) {
        double t = 2 * Math.PI;
        a = a % t;
        if (a > Math.PI) a -= t;
        if (a < -Math.PI) a += t;
        return a;
    }

    static long gcd(long a, long b) { return b == 0 ? a : gcd(b, a % b); }

    static long lcm3() {
        // grand multiple commun possible sans débordement : produit des périodes divisé par leurs pgcd (borné)
        long a = STAR_PLANET.period(), b = PLANET_MOON.period(), c = MOON_STATION.period();
        long l1 = a / gcd(a, b) * b;
        return l1 / gcd(l1, c) * c;
    }
}
