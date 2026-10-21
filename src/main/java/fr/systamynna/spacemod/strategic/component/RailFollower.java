package fr.systamynna.spacemod.strategic.component;

import org.jetbrains.annotations.Nullable;

import fr.systamynna.spacemod.strategic.RailMath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Suit un rail : orbite autour du parent de l'objet et/ou rotation sur place, fonctions du temps spatial seul
 * ({@code floorMod(temps + phase, période)}, jamais accumulé). Sans orbite, la translation stockée est conservée.
 *
 * @param orbit orbite autour du parent, ou {@code null} pour ne pas orbiter
 * @param spin rotation sur place, ou {@code null} pour ne pas tourner
 */
public record RailFollower(@Nullable Orbit orbit, @Nullable Spin spin) implements Controller {

    /**
     * Orbite circulaire centrée sur le parent, inclinée autour de l'axe X.
     *
     * @param period durée d'un tour, en ticks de temps spatial, strictement positive
     * @param radius rayon de l'orbite, dans l'unité du parent
     * @param phaseTicks décalage de phase, en ticks : position à l'instant 0
     * @param inclination inclinaison du plan de l'orbite, en radians
     */
    public record Orbit(long period, double radius, long phaseTicks, double inclination) {
        /** @throws IllegalArgumentException si la période n'est pas strictement positive */
        public Orbit {
            if (period <= 0) throw new IllegalArgumentException("période <= 0 : " + period);
        }
    }

    /**
     * Rotation autour de l'axe Y.
     *
     * @param period durée d'un tour, en ticks de temps spatial, strictement positive
     * @param phaseTicks décalage de phase, en ticks
     */
    public record Spin(long period, long phaseTicks) {
        /** @throws IllegalArgumentException si la période n'est pas strictement positive */
        public Spin {
            if (period <= 0) throw new IllegalArgumentException("période <= 0 : " + period);
        }
    }

    /** Rail avec une orbite seule, sans rotation propre. */
    public static RailFollower orbit(long period, double radius, long phaseTicks, double inclination) {
        return new RailFollower(new Orbit(period, radius, phaseTicks, inclination), null);
    }

    /** Rail avec une rotation propre seule : l'objet reste en place et tourne sur lui-même. */
    public static RailFollower spin(long period, long phaseTicks) {
        return new RailFollower(null, new Spin(period, phaseTicks));
    }

    /** Identifiant du type : {@link ControllerTypes#RAIL_FOLLOWER}. */
    @Override
    public ResourceLocation typeId() {
        return ControllerTypes.RAIL_FOLLOWER;
    }

    /** Avec une orbite, la position ne dépend que du temps ; une simple rotation sur place laisse la translation stockée. */
    @Override
    public boolean determinesPosition() {
        return orbit != null;
    }

    /**
     * Remplace la translation stockée par le décalage d'orbite (si orbite), puis compose la rotation propre (si spin)
     * avec la rotation stockée.
     */
    @Override
    public Transform resolve(Transform stored, double spaceTime) {
        Transform result = stored;
        if (orbit != null) {
            double[] o = RailMath.orbitOffset(orbit, spaceTime);
            result = result.withTranslation(o[0], o[1], o[2]);
        }
        if (spin != null) result = result.spun(RailMath.spinAngle(spin, spaceTime));
        return result;
    }

    /** Écrit l'orbite et/ou la rotation en NBT ; une partie absente n'est pas écrite. */
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (orbit != null) {
            CompoundTag o = new CompoundTag();
            o.putLong("period", orbit.period());
            o.putDouble("radius", orbit.radius());
            o.putLong("phase", orbit.phaseTicks());
            o.putDouble("inclination", orbit.inclination());
            tag.put("orbit", o);
        }
        if (spin != null) {
            CompoundTag s = new CompoundTag();
            s.putLong("period", spin.period());
            s.putLong("phase", spin.phaseTicks());
            tag.put("spin", s);
        }
        return tag;
    }

    /** Relit un contrôleur écrit par {@link #save()} ; une partie absente du NBT reste {@code null}. */
    static RailFollower load(CompoundTag tag) {
        Orbit orbit = null;
        Spin spin = null;
        if (tag.contains("orbit")) {
            CompoundTag o = tag.getCompound("orbit");
            orbit = new Orbit(o.getLong("period"), o.getDouble("radius"), o.getLong("phase"), o.getDouble("inclination"));
        }
        if (tag.contains("spin")) {
            CompoundTag s = tag.getCompound("spin");
            spin = new Spin(s.getLong("period"), s.getLong("phase"));
        }
        return new RailFollower(orbit, spin);
    }
}
