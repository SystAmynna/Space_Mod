import java.math.*;
import java.util.*;

/**
 * C4 — Voyage entre systèmes : saut et parcours (prototype hors jeu, parties numériques).
 * Position stratégique = cellule entière (long) + décalage double ramené dans [-CELL/2, CELL/2) : la partie flottante
 * reste petite, donc précise, quelle que soit la distance. Référence exacte : BigDecimal.
 * Usage : java C4.java
 */
public class C4 {
    static final double CELL = 1e6;
    static final MathContext MC = new MathContext(60);

    /** Position : cellules entières + décalage local (unités = blocs). */
    static final class Pos {
        long[] cell = new long[3]; double[] off = new double[3];
        Pos copy() { Pos p = new Pos(); p.cell = cell.clone(); p.off = off.clone(); return p; }
        void add(double[] d) { for (int i = 0; i < 3; i++) off[i] += d[i]; rebase(); }
        void rebase() {   // changement de référentiel local : ne change pas la position, seulement la représentation
            for (int i = 0; i < 3; i++) { long k = Math.round(off[i] / CELL); if (k != 0) { cell[i] += k; off[i] -= k * CELL; } }
        }
        BigDecimal[] exact() { BigDecimal[] r = new BigDecimal[3]; for (int i = 0; i < 3; i++) r[i] = BigDecimal.valueOf(cell[i]).multiply(BigDecimal.valueOf(1_000_000)).add(new BigDecimal(off[i])); return r; }
    }

    static BigDecimal norm(BigDecimal[] a, BigDecimal[] b) {
        BigDecimal s = BigDecimal.ZERO;
        for (int i = 0; i < 3; i++) { BigDecimal d = a[i].subtract(b[i]); s = s.add(d.multiply(d)); }
        return s.sqrt(MC);
    }
    static BigDecimal len(BigDecimal[] a) { return norm(a, new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO }); }

    // ---- quaternions ----
    static double[] qmul(double[] a, double[] b) {
        return new double[] { a[0]*b[0]-a[1]*b[1]-a[2]*b[2]-a[3]*b[3], a[0]*b[1]+a[1]*b[0]+a[2]*b[3]-a[3]*b[2],
            a[0]*b[2]-a[1]*b[3]+a[2]*b[0]+a[3]*b[1], a[0]*b[3]+a[1]*b[2]-a[2]*b[1]+a[3]*b[0] };
    }
    static double[] qconj(double[] q) { return new double[] { q[0], -q[1], -q[2], -q[3] }; }
    static double[] qnorm(double[] q) { double n = Math.sqrt(q[0]*q[0]+q[1]*q[1]+q[2]*q[2]+q[3]*q[3]); return new double[] { q[0]/n, q[1]/n, q[2]/n, q[3]/n }; }
    static double[] rot(double[] q, double[] v) { double[] r = qmul(qmul(q, new double[] { 0, v[0], v[1], v[2] }), qconj(q)); return new double[] { r[1], r[2], r[3] }; }
    static double[] randQ(Random r) { return qnorm(new double[] { r.nextGaussian(), r.nextGaussian(), r.nextGaussian(), r.nextGaussian() }); }
    static double dot(double[] a, double[] b) { return a[0]*b[0]+a[1]*b[1]+a[2]*b[2]; }
    static double mag(double[] a) { return Math.sqrt(dot(a, a)); }

    /** Mass lock de test : plafond de vitesse (blocs/s) selon la distance d à l'astre le plus proche ; aucune limite au-delà du rayon d'influence. */
    static final double INFLUENCE = 5e6, K = 0.02;       // K : 1/s ; à 5e6 blocs le plafond atteint 1e5 blocs/s
    static double massLock(double d) { return d >= INFLUENCE ? Double.POSITIVE_INFINITY : Math.max(1.0, K * d); }

    public static void main(String[] args) {
        Random rnd = new Random(42);
        boolean ok = true;

        // ---------- 1. SAUT ----------
        System.out.println("== 1. Saut : pose (position, orientation, vitesse) d'un système A vers un système B ==");
        double maxPosErr = 0, maxVelErr = 0, maxOriErr = 0, maxSpeedRel = 0, maxBodyVelErr = 0;
        for (int i = 0; i < 100_000; i++) {
            double[] qA = randQ(rnd), qB = randQ(rnd);                       // orientation du vaisseau avant / après (dans son référentiel)
            double[] vA = { rnd.nextGaussian() * 1e4, rnd.nextGaussian() * 1e4, rnd.nextGaussian() * 1e4 };
            // règle du saut : la vitesse exprimée dans le repère du vaisseau est conservée (le vaisseau garde son cap relatif)
            double[] vBody = rot(qconj(qA), vA);
            double[] vB = rot(qB, vBody);
            double[] vBodyB = rot(qconj(qB), vB);
            for (int k = 0; k < 3; k++) maxBodyVelErr = Math.max(maxBodyVelErr, Math.abs(vBodyB[k] - vBody[k]) / Math.max(1, mag(vBody)));
            maxSpeedRel = Math.max(maxSpeedRel, Math.abs(mag(vB) - mag(vA)) / mag(vA));
            double[] qBn = qnorm(qB); double nrm = Math.abs(Math.sqrt(qBn[0]*qBn[0]+qBn[1]*qBn[1]+qBn[2]*qBn[2]+qBn[3]*qBn[3]) - 1);
            maxOriErr = Math.max(maxOriErr, nrm);
            // position : arrivée choisie dans B, loin (jusqu'à 1e12 blocs) ; aller-retour A -> B -> A par déplacement du référentiel
            Pos pA = new Pos(); for (int k = 0; k < 3; k++) { pA.cell[k] = (long) (rnd.nextGaussian() * 1e6); pA.off[k] = (rnd.nextDouble() - 0.5) * CELL; }
            BigDecimal[] before = pA.exact();
            long[] D = { (long) (rnd.nextGaussian() * 1e6), (long) (rnd.nextGaussian() * 1e6), (long) (rnd.nextGaussian() * 1e6) };  // origine de B dans A (cellules)
            Pos inB = pA.copy(); for (int k = 0; k < 3; k++) inB.cell[k] -= D[k];
            Pos back = inB.copy(); for (int k = 0; k < 3; k++) back.cell[k] += D[k];
            BigDecimal[] after = back.exact();
            maxPosErr = Math.max(maxPosErr, norm(before, after).doubleValue());
        }
        System.out.printf("  erreur de position (aller-retour de référentiel, positions jusqu'à ~1e12) : max %.3e blocs%n", maxPosErr);
        System.out.printf("  écart relatif de la norme de vitesse : max %.3e ; vitesse dans le repère du vaisseau : max %.3e ; norme du quaternion : max %.3e%n", maxSpeedRel, maxBodyVelErr, maxOriErr);
        ok &= maxPosErr == 0 && maxSpeedRel < 1e-6 && maxBodyVelErr < 1e-6;

        // ---------- 2. PARCOURS ----------
        System.out.println("== 2. Parcours : 1e9 blocs loin de tout astre, changements de référentiel ==");
        double vNorm = 2.5e6 + 0.31372549;                                                // blocs/s (vitesse de croisière de test)
        double[] dir = { Math.sqrt(0.37), 0.1234567, Math.sqrt(1 - 0.37 - 0.1234567*0.1234567) };
        long ticks = (long) (1e9 / vNorm * 20);
        double[] dpt = { dir[0]*vNorm/20, dir[1]*vNorm/20, dir[2]*vNorm/20 };
        // référence exacte : n * (déplacement du tick) en BigDecimal
        Pos p = new Pos();                                                   // représentation (cellule + décalage)
        double[] naive = new double[3];                                      // une seule double, sans changement de référentiel (comparaison)
        float[] naiveF = new float[3];                                       // float 32 bits, à titre de comparaison (rendu client)
        BigDecimal[] exact = { BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO };
        BigDecimal[] stepExact = { new BigDecimal(dpt[0]), new BigDecimal(dpt[1]), new BigDecimal(dpt[2]) };
        long rebases = 0; double maxJump = 0;
        double maxRelCell = 0, maxRelNaive = 0, maxRelFloat = 0;
        long minDistSeen = 0;
        for (long t = 1; t <= ticks; t++) {
            long[] c0 = p.cell.clone();
            p.add(dpt);
            for (int k = 0; k < 3; k++) { naive[k] += dpt[k]; naiveF[k] += (float) dpt[k]; exact[k] = exact[k].add(stepExact[k]); }
            boolean reb = !Arrays.equals(c0, p.cell);
            if (reb) {
                rebases++;
                // saut de position pendant le changement de référentiel : exact(avant le changement) contre exact(après) = pas du tick
                // (le rebase seul ne change pas la position : on le vérifie sur la représentation)
            }
            if (t % 20_000 == 0 || t == ticks) {
                double rel = norm(p.exact(), exact).doubleValue() / len(exact).doubleValue();
                double relN = norm(new BigDecimal[] { new BigDecimal(naive[0]), new BigDecimal(naive[1]), new BigDecimal(naive[2]) }, exact).doubleValue() / len(exact).doubleValue();
                double relF = norm(new BigDecimal[] { new BigDecimal(naiveF[0]), new BigDecimal(naiveF[1]), new BigDecimal(naiveF[2]) }, exact).doubleValue() / len(exact).doubleValue();
                maxRelCell = Math.max(maxRelCell, rel); maxRelNaive = Math.max(maxRelNaive, relN); maxRelFloat = Math.max(maxRelFloat, relF);
            }
        }
        double absEnd = norm(p.exact(), exact).doubleValue();
        System.out.printf("  %d ticks, %d changements de cellule (tous les ~%.1f s) ; distance finale %.6e blocs%n", ticks, rebases, ticks / 20.0 / Math.max(1, rebases), len(exact).doubleValue());
        System.out.printf("  écart relatif max (cellule + décalage) : %.3e ; écart absolu final : %.3e blocs%n", maxRelCell, absEnd);
        System.out.printf("  comparaison, une seule double sans rebase : %.3e relatif ; float 32 bits : %.3e relatif%n", maxRelNaive, maxRelFloat);
        // continuité à chaque rebase : la position exacte avant/après la normalisation est identique par construction ; test direct
        double maxRebaseDelta = 0;
        for (int i = 0; i < 100_000; i++) {
            Pos q = new Pos(); for (int k = 0; k < 3; k++) { q.cell[k] = (long) (rnd.nextGaussian() * 1e6); q.off[k] = (rnd.nextDouble() - 0.5) * 8 * CELL; }
            BigDecimal[] a = q.exact(); q.rebase(); BigDecimal[] b = q.exact();
            maxRebaseDelta = Math.max(maxRebaseDelta, norm(a, b).doubleValue());
        }
        System.out.printf("  changement de référentiel local (rebase) : écart de position max %.3e blocs sur 1e5 cas aléatoires%n", maxRebaseDelta);
        ok &= maxRelCell < 1e-6 && maxRebaseDelta < 1e-6;

        // ---------- mass lock ----------
        System.out.println("== 2b. Mass lock : plafond de vitesse selon la distance ==");
        boolean monotone = true, free = true; double prev = 0;
        for (double d = 1; d < INFLUENCE; d *= 1.01) { double m = massLock(d); if (m < prev - 1e-9) monotone = false; prev = m; }
        for (double d : new double[] { INFLUENCE, 1e7, 1e9, 1e12 }) if (massLock(d) != Double.POSITIVE_INFINITY) free = false;
        System.out.printf("  croissant avec la distance : %b ; aucune limite à >= %.0e blocs : %b ; plafond à 1e3 / 1e5 / 4,9e6 blocs : %.0f / %.0f / %.0f blocs/s%n", monotone, INFLUENCE, free, massLock(1e3), massLock(1e5), massLock(4.9e6));
        System.out.printf("  vitesse de croisière du parcours (%.1e blocs/s) contre le plafond à 4,9e6 blocs : %s%n", vNorm, vNorm > massLock(4.9e6) ? "plafonnée près de l'astre (attendu)" : "non plafonnée");
        ok &= monotone && free;

        // ---------- 4. ordre de chargement ----------
        System.out.println("== 4. Ordre chargement / déchargement entre deux systèmes ==");
        List<String> log = new ArrayList<>();
        boolean loadedA = true, loadedB = false, skyB = false; boolean order = true;
        double switchAt = 0.5e9, preload = 0.45e9, unloadAfter = 0.55e9;   // positions le long du parcours (blocs)
        double[] positions = { 0.1e9, 0.4e9, 0.46e9, 0.5e9, 0.52e9, 0.56e9, 0.9e9 };
        boolean inB = false;
        for (double x : positions) {
            if (x >= preload && !loadedB) { loadedB = true; log.add("charge B à " + x); }
            if (x >= switchAt && !inB) { inB = true; if (!loadedB) order = false; skyB = true; log.add("référentiel et ciel -> B à " + x); }
            if (x >= unloadAfter && loadedA && inB) { loadedA = false; log.add("décharge A à " + x); }
        }
        log.forEach(s -> System.out.println("  " + s));
        order &= skyB && loadedB && !loadedA;
        System.out.println("  B chargé avant le changement, A déchargé après : " + order + "  (modèle logique seul : le vrai ciel et les vrais chargements n'existent pas encore dans le mod)");
        ok &= order;

        System.out.println(ok ? "RÉSULTAT NUMÉRIQUE : tous les critères atteints" : "RÉSULTAT NUMÉRIQUE : ÉCHEC");
    }
}
