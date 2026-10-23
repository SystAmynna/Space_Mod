package fr.systamynna.spacemod.ship;

import fr.systamynna.spacemod.strategic.SkyFrame;
import fr.systamynna.spacemod.strategic.SpaceConfig;
import fr.systamynna.spacemod.strategic.component.Transform;

/**
 * Vol d'un vaisseau, un tick à la fois, sans aucune dépendance au jeu (testable seul). Repère du vaisseau : avant = −Z
 * (le nord de Minecraft), droite = +X, haut = +Y ; le quaternion du Transform fait passer ce repère à celui du parent.
 * Vol inertiel : sans commande, la vitesse est conservée ; lacet, tangage et roulis agissent directement sur l'orientation.
 */
public final class ShipFlight {
    /** Classe utilitaire. */
    private ShipFlight() {}

    /**
     * Commandes d'un tick.
     *
     * @param throttle −1 (S) à +1 (Z) : poussée vers l'avant, ou décélération puis marche arrière
     * @param roll −1 (gauche) à +1 (droite)
     * @param yaw −1 (gauche) à +1 (droite) : déflexion du joystick, le nez tourne vers la droite si positif
     * @param pitch −1 (bas) à +1 (haut) : le nez monte si positif
     * @param brake frein (espace) : ramène la vitesse vers zéro
     */
    public record Input(double throttle, double roll, double yaw, double pitch, boolean brake) {
        /** Aucune commande. */
        public static final Input ZERO = new Input(0, 0, 0, 0, false);

        /** Copie dont chaque axe est borné à [−1, 1] (un client ne choisit pas ses limites). */
        public Input clamp() {
            return new Input(c(throttle), c(roll), c(yaw), c(pitch), brake);
        }

        private static double c(double v) {
            return Double.isNaN(v) ? 0 : Math.max(-1, Math.min(1, v));
        }
    }

    /**
     * Limites du vol.
     *
     * @param maxSpeed vitesse maximale, mètres par tick
     * @param acceleration accélération et freinage, mètres par tick au carré
     * @param turnRad lacet et tangage à pleine déflexion, radians par tick
     * @param rollRad roulis, radians par tick
     */
    public record Limits(double maxSpeed, double acceleration, double turnRad, double rollRad) {
        /** Limites de la configuration (lisible une fois la configuration chargée). */
        public static Limits fromConfig() {
            return new Limits(SpaceConfig.SHIP_MAX_SPEED.get(), SpaceConfig.SHIP_ACCELERATION.get(),
                    Math.toRadians(SpaceConfig.SHIP_TURN_RATE_DEG.get()), Math.toRadians(SpaceConfig.SHIP_ROLL_RATE_DEG.get()));
        }
    }

    /**
     * État du vol.
     *
     * @param pose position et orientation dans le référentiel du parent
     * @param velocity vitesse dans le référentiel du parent, mètres par tick
     * @param reverseLocked vrai tant que S est maintenu après avoir ramené l'avancée à zéro : il faut relâcher puis
     *        ré-appuyer pour passer en marche arrière
     */
    public record State(Transform pose, double[] velocity, boolean reverseLocked) {}

    /** Avance le vaisseau d'un tick : orientation d'abord, puis vitesse (dans le nouvel axe), puis position. */
    public static State step(State state, Input rawInput, Limits limits) {
        Input input = rawInput.clamp();
        Transform pose = state.pose();
        double[] q = SkyFrame.normalize(new double[] { pose.qx(), pose.qy(), pose.qz(), pose.qw() });
        double[] turn = SkyFrame.mul(SkyFrame.mul(axis(0, 1, 0, -input.yaw() * limits.turnRad()),
                axis(1, 0, 0, input.pitch() * limits.turnRad())), axis(0, 0, 1, -input.roll() * limits.rollRad()));
        q = SkyFrame.normalize(SkyFrame.mul(q, turn));
        double[] forward = new Transform(0, 0, 0, q[0], q[1], q[2], q[3], 1, 1, 1).linear(new double[] { 0, 0, -1 });

        double[] v = state.velocity().clone();
        boolean locked = state.reverseLocked() && input.throttle() < -0.5;
        double a = limits.acceleration();
        if (input.brake()) {
            double speed = norm(v);
            if (speed > 0) {
                double dec = Math.min(a, speed) / speed;
                for (int i = 0; i < 3; i++) v[i] -= v[i] * dec;
            }
        } else if (input.throttle() > 0) {
            for (int i = 0; i < 3; i++) v[i] += forward[i] * a * input.throttle();
        } else if (input.throttle() < 0 && !locked) {
            double along = v[0] * forward[0] + v[1] * forward[1] + v[2] * forward[2];
            double dv = a * -input.throttle();
            if (along > 0 && along <= dv) {
                // l'avancée tombe à zéro : S s'arrête là, la marche arrière demande un nouvel appui
                for (int i = 0; i < 3; i++) v[i] -= forward[i] * along;
                locked = true;
            } else {
                for (int i = 0; i < 3; i++) v[i] -= forward[i] * dv;
            }
        }
        double speed = norm(v);
        if (speed > limits.maxSpeed()) for (int i = 0; i < 3; i++) v[i] *= limits.maxSpeed() / speed;

        Transform next = new Transform(pose.tx() + v[0], pose.ty() + v[1], pose.tz() + v[2], q[0], q[1], q[2], q[3],
                pose.sx(), pose.sy(), pose.sz());
        return new State(next, v, locked);
    }

    /** Vitesse (norme de la vitesse), mètres par tick. */
    public static double norm(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    /** Quaternion {@code {x, y, z, w}} d'une rotation de {@code angle} radians autour d'un axe unitaire. */
    private static double[] axis(double x, double y, double z, double angle) {
        double s = Math.sin(angle / 2);
        return new double[] { x * s, y * s, z * s, Math.cos(angle / 2) };
    }
}
