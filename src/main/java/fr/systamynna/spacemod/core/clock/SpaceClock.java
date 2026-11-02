package fr.systamynna.spacemod.core.clock;

import net.minecraft.world.level.Level;

/**
 * Horloge de jeu linéaire par morceaux : {@code temps(n) = baseTime + (n - baseNanos) * multiplier}, n = nanoTime du serveur.
 * {@link #SERVER} est l'autorité ; {@link #CLIENT} en est la réplique (état reçu + décalage nanoTime mesuré par ping/pong).
 * Deux instances car en solo client et serveur partagent la JVM. Unité du temps : ms.
 */
public final class SpaceClock {
    public static final SpaceClock SERVER = new SpaceClock();
    public static final SpaceClock CLIENT = new SpaceClock();

    /** Segment courant : à {@code baseNanos} (nanoTime serveur) l'horloge valait {@code baseTime} ms, et avance à {@code multiplier}. */
    public record State(double baseTime, long baseNanos, double multiplier) {}

    private volatile State state = new State(0, System.nanoTime(), 1);
    /** nanoTime serveur ≈ nanoTime local + offset (toujours 0 côté serveur). */
    private volatile long offset;
    private volatile long bestRtt = Long.MAX_VALUE;

    public static SpaceClock of(Level level) {
        return level.isClientSide ? CLIENT : SERVER;
    }

    public State state() {
        return state;
    }

    long offset() {
        return offset;
    }

    public double multiplier() {
        return state.multiplier;
    }

    /** Temps de l'horloge (ms) à l'instant présent. */
    public double now() {
        return timeAt(System.nanoTime() + offset);
    }

    /** Temps de l'horloge (ms) à un nanoTime serveur donné. */
    public double timeAt(long serverNanos) {
        State s = state;
        return s.baseTime + (serverNanos - s.baseNanos) / 1e6 * s.multiplier;
    }

    /** Repart de {@code baseTime} maintenant (chargement du monde). */
    public synchronized void reset(double baseTime, double multiplier) {
        state = new State(baseTime, System.nanoTime(), multiplier);
    }

    /** Change la vitesse sans saut de temps. */
    public synchronized void setMultiplier(double m) {
        if (!(m >= 0) || Double.isInfinite(m)) throw new IllegalArgumentException("multiplier must be finite and >= 0: " + m);
        state = new State(now(), System.nanoTime() + offset, m);
    }

    /** Client : nouvel état serveur, la mesure de décalage est à refaire. */
    public synchronized void applySync(State s) {
        state = s;
        bestRtt = Long.MAX_VALUE;
    }

    /** Client : échantillon ping/pong ({@code t2} = nanoTime local à la réception) ; on garde celui au plus petit RTT. */
    public synchronized void applyPong(long clientNanos, long serverNanos, long t2) {
        long rtt = t2 - clientNanos;
        if (rtt < bestRtt) {
            bestRtt = rtt;
            offset = serverNanos + rtt / 2 - t2;
        }
    }
}
