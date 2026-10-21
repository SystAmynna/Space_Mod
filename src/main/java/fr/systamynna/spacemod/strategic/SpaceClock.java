package fr.systamynna.spacemod.strategic;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import net.minecraft.server.MinecraftServer;

/**
 * Temps spatial, en ticks (fractionnaires), tiré du temps de jeu de l'Overworld par un rythme réglable :
 * {@code espace = ancre + (tempsDeJeu − tempsDeJeuDeLAncre) × rythme}. 1.0 = temps spatial réel, 0 = temps figé.
 * Calculé à partir d'une ancre et non accumulé tick par tick : aucune dérive, et changer le rythme ne produit aucun saut
 * (l'ancre est déplacée au moment du changement). L'ancre et le rythme sont sauvés dans la base de la couche : le temps
 * spatial survit donc au redémarrage du serveur et, comme le temps de jeu de l'Overworld ne court pas pendant l'arrêt,
 * il ne court pas non plus pendant l'arrêt. Thread serveur.
 * <p>
 * Les positions des rails ne dépendent que de cette valeur : deux machines qui connaissent la même ancre et le même
 * rythme obtiennent le même temps spatial pour un temps de jeu donné. Les clients n'ont pas encore de copie de l'horloge
 * (la réplication n'est pas écrite) ; la formule est prévue pour l'être telle quelle.
 */
public final class SpaceClock {
    /** Temps de jeu de l'Overworld auquel l'ancre a été posée (création de l'horloge, ou dernier changement de rythme). */
    private long anchorGameTick;
    /** Temps spatial, en ticks fractionnaires, à l'instant de l'ancre. */
    private double anchorSpaceTicks;
    /** Ticks spatiaux écoulés par tick de jeu : 1.0 = réel, 0 = figé. Toujours fini et ≥ 0. */
    private double rate;
    /** {@code true} si l'état de l'horloge a changé depuis sa dernière écriture dans la base. */
    private boolean needsSave;

    /** Horloge neuve : ancre à zéro, temps spatial égal au temps de jeu (rythme 1.0, non marquée à sauver). */
    public SpaceClock() {
        this(0, 0, 1);
    }

    /**
     * Horloge avec une ancre donnée (relecture depuis la base).
     *
     * @throws IllegalArgumentException si le rythme est invalide
     */
    private SpaceClock(long anchorGameTick, double anchorSpaceTicks, double rate) {
        checkRate(rate);
        this.anchorGameTick = anchorGameTick;
        this.anchorSpaceTicks = anchorSpaceTicks;
        this.rate = rate;
    }

    /**
     * Horloge reconstruite depuis les valeurs sauvées par {@link #anchorGameTick()}, {@link #anchorSpaceTicks()} et
     * {@link #rate()}.
     *
     * @throws IllegalArgumentException si le rythme est invalide
     */
    public static SpaceClock restored(long anchorGameTick, double anchorSpaceTicks, double rate) {
        return new SpaceClock(anchorGameTick, anchorSpaceTicks, rate);
    }

    /** Temps de jeu auquel l'ancre a été posée. */
    public long anchorGameTick() {
        return anchorGameTick;
    }

    /** Temps spatial, en ticks fractionnaires, à l'instant de l'ancre. */
    public double anchorSpaceTicks() {
        return anchorSpaceTicks;
    }

    /** Temps de jeu de l'Overworld, base de l'horloge (indépendant de {@code /time set}, qui ne change que l'heure du jour). */
    public static long gameTime(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /** Rythme actuel : ticks spatiaux par tick de jeu (1.0 = temps spatial réel, 0 = figé). */
    public double rate() {
        return rate;
    }

    /**
     * Temps spatial au temps de jeu donné ; fractionnaire pour un rendu entre deux ticks ({@code getGameTime() + partialTick}).
     * <p>
     * Effet de bord, rare : si le temps de jeu est antérieur à l'ancre (temps de l'Overworld réinitialisé), l'ancre est
     * ramenée à ce temps de jeu en gardant sa valeur spatiale. Le temps spatial repart donc de celle du dernier
     * changement de rythme, au lieu de devenir négatif ou de rester figé jusqu'à ce que le temps de jeu rattrape l'ancre ;
     * l'horloge est alors marquée à sauver et un avertissement est journalisé.
     *
     * @param gameTime temps de jeu de l'Overworld, éventuellement fractionnaire (avec {@code partialTick})
     * @return le temps spatial, en ticks fractionnaires
     */
    public double spaceTime(double gameTime) {
        if (gameTime < anchorGameTick) {
            AmynnaSpaceMod.LOGGER.warn("Temps de jeu ({}) antérieur à l'ancre de l'horloge spatiale ({}) : ancre ramenée", gameTime, anchorGameTick);
            // on garde la valeur spatiale de l'ancre et on la rattache au nouveau temps de jeu (arrondi par le bas)
            anchorGameTick = (long) Math.floor(gameTime);
            needsSave = true;
        }
        // formule à ancre : une seule multiplication, donc aucune erreur qui s'accumule d'un tick à l'autre
        return anchorSpaceTicks + (gameTime - anchorGameTick) * rate;
    }

    /**
     * Change le rythme à partir de maintenant, sans saut du temps spatial : l'ancre est déplacée au temps de jeu donné,
     * avec le temps spatial courant comme valeur.
     *
     * @param newRate nouveau rythme : ticks spatiaux par tick de jeu (1.0 = réel, 0 = figé)
     * @param gameTime temps de jeu courant
     * @throws IllegalArgumentException si le rythme n'est pas un nombre fini ≥ 0
     */
    public void setRate(double newRate, long gameTime) {
        checkRate(newRate);
        // temps spatial à cet instant, calculé avec l'ancien rythme (et la remise en cohérence éventuelle de l'ancre)
        double now = spaceTime(gameTime);
        anchorGameTick = gameTime;
        anchorSpaceTicks = now;
        rate = newRate;
        needsSave = true;
    }

    /** {@code true} si l'horloge a changé depuis sa dernière écriture dans la base. Lu par {@link StrategicLayer#save()}. */
    boolean needsSave() {
        return needsSave;
    }

    /** À appeler une fois l'horloge écrite dans la base. */
    void clearNeedsSave() {
        needsSave = false;
    }

    /** @throws IllegalArgumentException si le rythme est NaN, infini ou négatif (0 est valide : temps figé) */
    private static void checkRate(double rate) {
        // `!(rate >= 0)` rejette aussi NaN, pour lequel toute comparaison est fausse
        if (!(rate >= 0) || Double.isInfinite(rate)) {
            throw new IllegalArgumentException("rythme du temps spatial invalide (fini et >= 0 attendu) : " + rate);
        }
    }
}
