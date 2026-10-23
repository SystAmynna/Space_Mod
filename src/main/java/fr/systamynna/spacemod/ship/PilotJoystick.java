package fr.systamynna.spacemod.ship;

/**
 * Joystick virtuel du pilote : les mouvements de la souris s'accumulent en un écart angulaire par rapport à l'avant, borné
 * à un cône de {@value #CONE_DEG}°. Sous {@value #DEAD_ZONE_DEG}° (zone morte) le vaisseau ne tourne pas ; au-delà, la
 * déflexion croît linéairement jusqu'au bord du cône. Aucune classe client : testable seul.
 */
public final class PilotJoystick {
    /** Rayon du cône, en degrés. */
    public static final double CONE_DEG = 30;
    /** Rayon de la zone morte, en degrés. */
    public static final double DEAD_ZONE_DEG = 2;

    private double devYaw;
    private double devPitch;

    /** Ajoute un mouvement de souris (degrés : droite et bas positifs, comme la rotation du joueur) et borne l'écart au cône. */
    public void move(double yawDelta, double pitchDelta) {
        devYaw += yawDelta;
        devPitch += pitchDelta;
        double r = Math.hypot(devYaw, devPitch);
        if (r > CONE_DEG) {
            devYaw *= CONE_DEG / r;
            devPitch *= CONE_DEG / r;
        }
    }

    /** Remet le joystick au centre. */
    public void reset() {
        devYaw = 0;
        devPitch = 0;
    }

    private double deflection() {
        return Math.max(0, Math.min(1, (Math.hypot(devYaw, devPitch) - DEAD_ZONE_DEG) / (CONE_DEG - DEAD_ZONE_DEG)));
    }

    /** Lacet demandé, de −1 (gauche) à 1 (droite). */
    public double yaw() {
        double r = Math.hypot(devYaw, devPitch);
        return r == 0 ? 0 : devYaw / r * deflection();
    }

    /** Tangage demandé, de −1 (bas) à 1 (haut) : la souris vers le haut fait monter le nez. */
    public double pitch() {
        double r = Math.hypot(devYaw, devPitch);
        return r == 0 ? 0 : -devPitch / r * deflection();
    }
}
