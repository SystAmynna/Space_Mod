import java.util.*;

/**
 * B3 — Groupes de rencontre avec hystérésis (prototype hors jeu).
 * Une arête (paire d'objets du même système) apparaît à distance < E et ne disparaît qu'à distance >= X (X > E).
 * Un groupe est une composante connexe du graphe d'arêtes (étiquette = plus petit identifiant, donc déterministe).
 */
public class B3 {
    record Body(int id, int system) {}

    static final class Tracker {
        final double enter, exit;
        final Set<Long> edges = new HashSet<>();
        Tracker(double enter, double exit) { this.enter = enter; this.exit = exit; }

        static long key(int a, int b) { return ((long) Math.min(a, b) << 32) | Math.max(a, b); }

        /** Met à jour les arêtes puis renvoie id -> étiquette de groupe. L'ordre de la liste n'a aucun effet. */
        Map<Integer, Integer> update(List<Body> bodies, Map<Integer, double[]> pos) {
            for (int i = 0; i < bodies.size(); i++) for (int j = i + 1; j < bodies.size(); j++) {
                Body a = bodies.get(i), b = bodies.get(j);
                if (a.system != b.system) { edges.remove(key(a.id, b.id)); continue; }
                double d = dist(pos.get(a.id), pos.get(b.id));
                long k = key(a.id, b.id);
                if (edges.contains(k)) { if (d >= exit) edges.remove(k); } else if (d < enter) edges.add(k);
            }
            Map<Integer, Integer> parent = new HashMap<>();
            for (Body b : bodies) parent.put(b.id, b.id);
            for (long k : edges) union(parent, (int) (k >> 32), (int) (k & 0xffffffffL));
            Map<Integer, Integer> label = new TreeMap<>();
            for (Body b : bodies) label.put(b.id, find(parent, b.id));
            return label;
        }

        static int find(Map<Integer, Integer> p, int x) { while (p.get(x) != x) { p.put(x, p.get(p.get(x))); x = p.get(x); } return x; }
        static void union(Map<Integer, Integer> p, int a, int b) { int ra = find(p, a), rb = find(p, b); if (ra != rb) p.put(Math.max(ra, rb), Math.min(ra, rb)); }
    }

    static double dist(double[] a, double[] b) { return Math.sqrt((a[0]-b[0])*(a[0]-b[0]) + (a[1]-b[1])*(a[1]-b[1]) + (a[2]-b[2])*(a[2]-b[2])); }
    static Map<Integer, double[]> at(double... xs) { Map<Integer, double[]> m = new HashMap<>(); for (int i = 0; i < xs.length; i++) m.put(i + 1, new double[] { xs[i], 0, 0 }); return m; }

    static final StringBuilder OUT = new StringBuilder();
    static int fails;
    static void say(String s) { System.out.println(s); OUT.append(s).append('\n'); }
    static void check(String what, boolean ok) { if (!ok) fails++; say((ok ? "OK      " : "ÉCHEC   ") + what); }

    static boolean grouped(Map<Integer, Integer> g, int a, int b) { return g.get(a).equals(g.get(b)); }

    public static void main(String[] args) throws Exception {
        double E = 100, X = 150;
        List<Body> three = List.of(new Body(1, 0), new Body(2, 0), new Body(3, 0));
        Tracker t = new Tracker(E, X);

        say("== scénarios (entrée E=100, sortie X=150) ==");
        var g = t.update(three, at(0, 500, 1000));
        check("trois objets éloignés : trois groupes", new HashSet<>(g.values()).size() == 3);
        g = t.update(three, at(0, 90, 1000));
        check("A rejoint B (90 < E)", grouped(g, 1, 2) && !grouped(g, 1, 3));
        g = t.update(three, at(0, 90, 140));
        check("C approche à 50 de B mais reste hors de A : C rejoint le groupe via B (chaîne A–B–C avec A et C à 140)", grouped(g, 2, 3) && grouped(g, 1, 3));
        g = t.update(three, at(0, 120, 140));
        check("B s'éloigne de A à 120 (entre E et X) : le groupe est conservé (hystérésis)", grouped(g, 1, 2));
        g = t.update(three, at(0, 160, 200));
        check("B passe à 160 > X : A quitte le groupe ; C reste avec B (40 d'écart)", !grouped(g, 1, 2) && grouped(g, 2, 3));
        List<Body> four = List.of(new Body(1, 0), new Body(2, 0), new Body(3, 0), new Body(4, 0));
        Tracker f = new Tracker(E, X);
        Map<Integer, double[]> p = at(0, 50, 400, 450);
        var g4 = f.update(four, p);
        check("deux groupes distincts {A,B} et {C,D}", grouped(g4, 1, 2) && grouped(g4, 3, 4) && !grouped(g4, 1, 3));
        p = at(0, 50, 100, 150);
        g4 = f.update(four, p);
        check("fusion : B–C à 50 relie les deux groupes en un seul", grouped(g4, 1, 4));
        p = at(0, 50, 400, 450);
        g4 = f.update(four, p);
        check("scission : retour à deux groupes", grouped(g4, 1, 2) && grouped(g4, 3, 4) && !grouped(g4, 1, 3));
        List<Body> sys = List.of(new Body(1, 0), new Body(2, 1));
        var gs = new Tracker(E, X).update(sys, at(0, 0));
        check("objets de systèmes différents aux mêmes coordonnées : jamais groupés", !grouped(gs, 1, 2));

        say("\n== tests à propriétés : indépendance de l'ordre (graine 99) ==");
        Random r = new Random(99);
        int ticks = 3000, orderFails = 0, crossSystem = 0;
        List<Body> bodies = new ArrayList<>();
        for (int i = 1; i <= 40; i++) bodies.add(new Body(i, i % 3 == 0 ? 1 : 0));
        Map<Integer, double[]> pos = new HashMap<>();
        for (Body b : bodies) pos.put(b.id, new double[] { r.nextDouble() * 600, r.nextDouble() * 600, 0 });
        Tracker t1 = new Tracker(E, X), t2 = new Tracker(E, X);
        long changes = 0;
        Map<Integer, Integer> prev = null;
        for (int k = 0; k < ticks; k++) {
            for (double[] v : pos.values()) { v[0] += r.nextGaussian() * 6; v[1] += r.nextGaussian() * 6; }
            List<Body> shuffled = new ArrayList<>(bodies);
            Collections.shuffle(shuffled, r);
            var a = t1.update(bodies, pos);
            var b = t2.update(shuffled, pos);
            if (!a.equals(b)) orderFails++;
            for (Body x : bodies) for (Body y : bodies) if (x.system != y.system && a.get(x.id).equals(a.get(y.id))) crossSystem++;
            if (prev != null) for (int id : a.keySet()) if (!a.get(id).equals(prev.get(id))) changes++;
            prev = a;
        }
        check("ordre d'itération : " + orderFails + " divergence(s) sur " + ticks + " ticks", orderFails == 0);
        check("aucun groupe contenant deux systèmes différents (" + crossSystem + " cas)", crossSystem == 0);
        say("changements d'étiquette observés : " + changes);

        say("\n== bruit autour du seuil : A oscille autour de E d'un petit bruit gaussien ==");
        for (double[] cfg : new double[][] { { 100, 100 }, { 100, 150 } }) {
            Tracker n = new Tracker(cfg[0], cfg[1]);
            Random rn = new Random(5);
            List<Body> two = List.of(new Body(1, 0), new Body(2, 0));
            int flips = 0;
            boolean last = false;
            for (int k = 0; k < 20000; k++) {
                // approche lente de 130 à 70 puis retour, bruit de ±3 ; franchit E=100 deux fois
                double base = 130 - 60 * Math.sin(Math.PI * k / 20000.0 * 1.0);
                var gg = n.update(two, at(0, base + rn.nextGaussian() * 3));
                boolean now = grouped(gg, 1, 2);
                if (now != last) flips++;
                last = now;
            }
            say(String.format("E=%.0f X=%.0f : %d changement(s) de groupe pour un franchissement aller-retour (%s)", cfg[0], cfg[1], flips, cfg[0] == cfg[1] ? "sans hystérésis" : "avec hystérésis"));
            if (cfg[0] != cfg[1]) check("avec hystérésis : au plus 2 changements (entrée puis sortie) pour un aller-retour", flips <= 2);
        }
        say("\nFIN B3 : " + (fails == 0 ? "RÉUSSI" : "ÉCHEC (" + fails + ")"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("../test-results/B3/b3-run.log"), OUT.toString());
    }
}
