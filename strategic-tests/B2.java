import java.util.*;
import java.util.stream.*;

/**
 * B2 — Rattrapage de simulation et états détruits (prototype hors jeu).
 * Hypothèses (à valider par l'utilisateur) : astres sur rails circulaires ; Minis sans joueur et Micros en trajectoire
 * rectiligne uniforme, sans attraction des astres ; collision = chevauchement de deux sphères à un tick entier.
 * Règles : astre/Mini -> Mini détruite ; Mini/Mini -> les deux détruites ; Mini/Micro -> Mini détruite et Micro oubliée.
 * Le rattrapage est événementiel (premier contact par paire, puis tri chronologique) ; la référence est la simulation tick par tick.
 */
public class B2 {
    enum Kind { ASTRE, MINI, MICRO }

    record Obj(int id, Kind kind, double radius, double[] p0, double[] v,           // mouvement linéaire : p0 + v*t
               double[] center, double orbitR, long period, long phase) {            // mouvement circulaire (astre)
        boolean rail() { return kind == Kind.ASTRE; }

        double[] pos(long t) {
            if (!rail()) return new double[] { p0[0] + v[0] * t, p0[1] + v[1] * t, p0[2] + v[2] * t };
            double a = 2 * Math.PI * ((double) Math.floorMod(t + phase, period) / period);
            return new double[] { center[0] + orbitR * Math.cos(a), center[1] + orbitR * Math.sin(a), center[2] };
        }

        double vmax() { return rail() ? 2 * Math.PI * orbitR / period : Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); }
    }

    record Event(long tick, Obj a, Obj b) {}
    record Destroyed(long tick, Obj o, double[] posAtDestruction) {}

    static Obj mini(int id, double r, double[] p, double[] v) { return new Obj(id, Kind.MINI, r, p, v, null, 0, 0, 0); }
    static Obj micro(int id, double r, double[] p, double[] v) { return new Obj(id, Kind.MICRO, r, p, v, null, 0, 0, 0); }
    static Obj astre(int id, double r, double[] c, double orbitR, long period, long phase) { return new Obj(id, Kind.ASTRE, r, null, null, c, orbitR, period, phase); }

    static boolean relevant(Obj a, Obj b) {
        if (a.kind == Kind.ASTRE) return b.kind == Kind.MINI;
        if (a.kind == Kind.MINI) return b.kind != Kind.ASTRE || true;
        return b.kind == Kind.MINI;
    }

    static double dist(double[] a, double[] b) { return Math.sqrt((a[0]-b[0])*(a[0]-b[0]) + (a[1]-b[1])*(a[1]-b[1]) + (a[2]-b[2])*(a[2]-b[2])); }

    /** Premier tick entier dans [0, T] où les sphères se chevauchent, ou -1. */
    static long firstContact(Obj a, Obj b, long T) {
        double R = a.radius + b.radius;
        if (!a.rail() && !b.rail()) { // quadratique exacte
            double[] dp = { a.p0[0]-b.p0[0], a.p0[1]-b.p0[1], a.p0[2]-b.p0[2] }, dv = { a.v[0]-b.v[0], a.v[1]-b.v[1], a.v[2]-b.v[2] };
            double A = dv[0]*dv[0] + dv[1]*dv[1] + dv[2]*dv[2], B = 2 * (dp[0]*dv[0] + dp[1]*dv[1] + dp[2]*dv[2]), C = dp[0]*dp[0] + dp[1]*dp[1] + dp[2]*dp[2] - R * R;
            if (C <= 0) return 0;
            if (A == 0) return -1;
            double disc = B * B - 4 * A * C;
            if (disc < 0) return -1;
            double sq = Math.sqrt(disc), t1 = (-B - sq) / (2 * A), t2 = (-B + sq) / (2 * A);
            if (t2 < 0) return -1;
            long t = (long) Math.ceil(Math.max(0, t1));
            return (t <= T && t <= Math.floor(t2)) ? t : -1;
        }
        double vrel = a.vmax() + b.vmax();
        long t = 0;
        while (t <= T) { // traçage de sphère : jamais de saut au-dessus d'un contact
            double d = dist(a.pos(t), b.pos(t)) - R;
            if (d <= 0) return t;
            t += Math.max(1, (long) Math.floor(d / Math.max(vrel, 1e-12)));
        }
        return -1;
    }

    static void resolve(Obj a, Obj b, Set<Integer> dead, List<Integer> destroyedIds, Set<Integer> forgotten, long t, Map<Integer, Destroyed> graveyard) {
        Obj mini1 = a.kind == Kind.MINI ? a : (b.kind == Kind.MINI ? b : null);
        if (a.kind == Kind.MINI && b.kind == Kind.MINI) {
            kill(a, t, dead, destroyedIds, graveyard); kill(b, t, dead, destroyedIds, graveyard);
        } else if (mini1 != null) {
            Obj other = mini1 == a ? b : a;
            kill(mini1, t, dead, destroyedIds, graveyard);
            if (other.kind == Kind.MICRO) { dead.add(other.id); forgotten.add(other.id); }
        }
    }

    static void kill(Obj o, long t, Set<Integer> dead, List<Integer> ids, Map<Integer, Destroyed> graveyard) {
        dead.add(o.id); ids.add(o.id); graveyard.put(o.id, new Destroyed(t, o, o.pos(t)));
    }

    record Result(List<String> log, Map<Integer, Destroyed> graveyard, Set<Integer> forgotten) {}

    /** Rattrapage événementiel sur [0, T]. */
    static Result catchUp(List<Obj> objs, long T) {
        List<Event> events = new ArrayList<>();
        int n = objs.size();
        events = IntStream.range(0, n).parallel().boxed().flatMap(i -> {
            List<Event> local = new ArrayList<>();
            Obj a = objs.get(i);
            for (int j = i + 1; j < n; j++) {
                Obj b = objs.get(j);
                if (!relevantPair(a, b)) continue;
                long t = firstContact(a, b, T);
                if (t >= 0) local.add(new Event(t, a, b));
            }
            return local.stream();
        }).sorted(Comparator.comparingLong(Event::tick).thenComparingInt(e -> Math.min(e.a.id, e.b.id)).thenComparingInt(e -> Math.max(e.a.id, e.b.id))).collect(Collectors.toList());
        Set<Integer> dead = new HashSet<>(), forgotten = new HashSet<>();
        List<Integer> ids = new ArrayList<>();
        Map<Integer, Destroyed> graveyard = new TreeMap<>();
        List<String> log = new ArrayList<>();
        // à tick égal : un objet détruit à ce tick participe encore aux autres événements du même tick
        int i = 0;
        while (i < events.size()) {
            int j = i;
            while (j < events.size() && events.get(j).tick == events.get(i).tick) j++;
            Set<Integer> deadBefore = new HashSet<>(dead);
            for (int k = i; k < j; k++) {
                Event e = events.get(k);
                if (deadBefore.contains(e.a.id) || deadBefore.contains(e.b.id)) continue;
                int before = ids.size();
                resolve(e.a, e.b, dead, ids, forgotten, e.tick, graveyard);
                log.add(e.tick + " " + e.a.kind + e.a.id + "×" + e.b.kind + e.b.id);
            }
            i = j;
        }
        return new Result(log, graveyard, forgotten);
    }

    static boolean relevantPair(Obj a, Obj b) {
        if (a.kind == Kind.ASTRE && b.kind == Kind.ASTRE) return false;
        if (a.kind == Kind.MICRO && b.kind == Kind.MICRO) return false;
        if (a.kind == Kind.MICRO && b.kind == Kind.ASTRE || a.kind == Kind.ASTRE && b.kind == Kind.MICRO) return false;
        return true;
    }

    /** Référence : simulation tick par tick (mêmes règles, mêmes conventions de tick égal). */
    static Result stepped(List<Obj> objs, long T) {
        Set<Integer> dead = new HashSet<>(), forgotten = new HashSet<>();
        List<Integer> ids = new ArrayList<>();
        Map<Integer, Destroyed> graveyard = new TreeMap<>();
        List<String> log = new ArrayList<>();
        int n = objs.size();
        for (long t = 0; t <= T; t++) {
            Set<Integer> deadBefore = new HashSet<>(dead);
            double[][] pos = new double[n][];
            for (int i = 0; i < n; i++) if (!deadBefore.contains(objs.get(i).id)) pos[i] = objs.get(i).pos(t);
            for (int i = 0; i < n; i++) {
                if (pos[i] == null) continue;
                for (int j = i + 1; j < n; j++) {
                    if (pos[j] == null) continue;
                    Obj a = objs.get(i), b = objs.get(j);
                    if (!relevantPair(a, b)) continue;
                    if (dist(pos[i], pos[j]) <= a.radius + b.radius) {
                        resolve(a, b, dead, ids, forgotten, t, graveyard);
                        log.add(t + " " + a.kind + a.id + "×" + b.kind + b.id);
                    }
                }
            }
        }
        return new Result(log, graveyard, forgotten);
    }

    static final StringBuilder OUT = new StringBuilder();
    static void say(String s) { System.out.println(s); OUT.append(s).append('\n'); }

    static boolean same(Result a, Result b) { return a.log.equals(b.log) && a.forgotten.equals(b.forgotten) && a.graveyard.keySet().equals(b.graveyard.keySet()); }

    public static void main(String[] args) throws Exception {
        long H = 72_000L, D = 24 * H, M = 30 * D;
        long[] durations = { H, D, M };
        String[] names = { "1 heure", "1 jour", "1 mois" };
        double[] z = { 0, 0, 0 };
        int fails = 0;

        // scénarios (a) à (e) ; vitesses petites devant les rayons : pas de traversée entre deux ticks
        Map<String, List<Obj>> sc = new LinkedHashMap<>();
        Obj planete = astre(1, 100, new double[] { 0, 0, 0 }, 50_000, 4 * D, 0);
        double[] cible = planete.pos(30_000), vm = { 0.3, 0.1, 0 }; // la Mini est lancée pour croiser l'astre au tick 30 000
        sc.put("(a) Mini contre astre", List.of(planete, mini(2, 5, new double[] { cible[0] - vm[0] * 30_000 + 20, cible[1] - vm[1] * 30_000, 0 }, vm)));
        sc.put("(b) Mini contre Mini", List.of(mini(1, 5, new double[] { 0, 0, 0 }, new double[] { 0.1, 0, 0 }), mini(2, 5, new double[] { 5000, 3, 0 }, new double[] { -0.1, 0, 0 })));
        sc.put("(c) Mini contre Micro", List.of(mini(1, 5, new double[] { 0, 0, 0 }, new double[] { 0.1, 0, 0 }), micro(2, 1, new double[] { 3000, 2, 0 }, new double[] { -0.2, 0, 0 })));
        sc.put("(d) trois objets, collisions multiples", List.of(mini(1, 5, new double[] { 0, 0, 0 }, new double[] { 0.1, 0, 0 }), mini(2, 5, new double[] { 2000, 0, 0 }, new double[] { -0.1, 0, 0 }),
            mini(3, 5, new double[] { 1000, 4, 0 }, new double[] { 0.0, 0, 0 }), micro(4, 1, new double[] { 1500, 100, 0 }, new double[] { 0, -0.05, 0 })));
        sc.put("(e) aucune collision", List.of(mini(1, 5, z, new double[] { 0.1, 0, 0 }), mini(2, 5, new double[] { 0, 1000, 0 }, new double[] { 0.1, 0, 0 }), astre(3, 100, new double[] { 0, 5000, 0 }, 100000, 5 * D, 3)));

        say("== scénarios : rattrapage événementiel contre simulation tick par tick ==");
        for (var e : sc.entrySet()) for (int k = 0; k < 3; k++) {
            long t0 = System.nanoTime();
            Result fast = catchUp(e.getValue(), durations[k]);
            double msFast = (System.nanoTime() - t0) / 1e6;
            t0 = System.nanoTime();
            Result ref = stepped(e.getValue(), durations[k]);
            double msRef = (System.nanoTime() - t0) / 1e6;
            boolean ok = same(fast, ref);
            if (!ok) fails++;
            say(String.format("%s sur %s : événements=%s ; référence=%s ; durée rattrapage %.2f ms (référence %.0f ms) -> %s",
                e.getKey(), names[k], fast.log, ref.log, msFast, msRef, ok ? "IDENTIQUE" : "DIFFÉRENT"));
        }

        // validation aléatoire : petits groupes, graine fixée
        Random r = new Random(2026);
        int randomFails = 0, randomEvents = 0, runs = 60;
        for (int run = 0; run < runs; run++) {
            List<Obj> l = new ArrayList<>();
            int id = 1;
            for (int i = 0; i < 6; i++) l.add(mini(id++, 4 + r.nextInt(4), new double[] { r.nextDouble() * 4000, r.nextDouble() * 4000, 0 }, new double[] { (r.nextDouble() - 0.5) * 0.4, (r.nextDouble() - 0.5) * 0.4, 0 }));
            for (int i = 0; i < 3; i++) l.add(micro(id++, 1, new double[] { r.nextDouble() * 4000, r.nextDouble() * 4000, 0 }, new double[] { (r.nextDouble() - 0.5) * 0.4, (r.nextDouble() - 0.5) * 0.4, 0 }));
            l.add(astre(id++, 60, new double[] { 2000, 2000, 0 }, 1500, 2 * H, r.nextInt(1000)));
            Result a = catchUp(l, D), b = stepped(l, D);
            randomEvents += b.log.size();
            if (!same(a, b)) randomFails++;
        }
        say(String.format("%n== validation aléatoire (graine 2026) : %d scénarios de 10 objets sur 1 jour, %d événements au total, %d divergence(s) ==", runs, randomEvents, randomFails));
        fails += randomFails;

        // restauration : état antérieur à la destruction
        List<Obj> l = sc.get("(b) Mini contre Mini");
        Result res = catchUp(l, M);
        Destroyed d1 = res.graveyard.get(1);
        double[] expected = l.get(0).pos(d1.tick);
        boolean restoreOk = Arrays.equals(d1.posAtDestruction, expected) && res.graveyard.containsKey(2) && d1.o.v[0] == 0.1;
        say("restauration d'une Mini détruite : état conservé (tick " + d1.tick + ", position " + Arrays.toString(d1.posAtDestruction) + ", vitesse " + d1.o.v[0] + ") -> " + (restoreOk ? "OK" : "ÉCHEC"));
        List<Obj> lc = sc.get("(c) Mini contre Micro");
        Result rc = catchUp(lc, M);
        say("Mini contre Micro : Mini restaurable=" + rc.graveyard.containsKey(1) + ", Micro oubliée=" + rc.forgotten.contains(2) + " (non restaurable par conception)");
        if (!restoreOk) fails++;

        // performance : 10 000 Minis, 1 000 Micros, 20 astres, 1 mois
        Random rp = new Random(7);
        List<Obj> big = new ArrayList<>();
        int id = 1;
        double box = 3e6;
        for (int i = 0; i < 10_000; i++) big.add(mini(id++, 5, new double[] { rp.nextDouble() * box, rp.nextDouble() * box, rp.nextDouble() * box }, new double[] { (rp.nextDouble() - .5), (rp.nextDouble() - .5), (rp.nextDouble() - .5) }));
        for (int i = 0; i < 1_000; i++) big.add(micro(id++, 1, new double[] { rp.nextDouble() * box, rp.nextDouble() * box, rp.nextDouble() * box }, new double[] { (rp.nextDouble() - .5), (rp.nextDouble() - .5), (rp.nextDouble() - .5) }));
        for (int i = 0; i < 20; i++) big.add(astre(id++, 5000, new double[] { rp.nextDouble() * box, rp.nextDouble() * box, rp.nextDouble() * box }, 1e6 + rp.nextDouble() * 1e7, 100_000L + rp.nextInt(1_000_000), rp.nextInt(100000)));
        for (int pass = 0; pass < 3; pass++) {
            long t0 = System.nanoTime();
            Result rb = catchUp(big, M);
            double s = (System.nanoTime() - t0) / 1e9;
            say(String.format("performance, 10 000 Minis + 1 000 Micros + 20 astres sur 1 mois (essai %d) : %.2f s, %d événements (critère proposé : moins de 1 s)", pass + 1, s, rb.log.size()));
        }
        say("\nFIN B2 : " + (fails == 0 ? "exactitude RÉUSSIE" : "ÉCHEC (" + fails + ")"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("../test-results/B2/b2-run.log"), OUT.toString());
    }
}
