package fr.systamynna.spacemod.ship;

import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/** Choix du parent d'un vaisseau : l'astre le plus proche, avec une hystérésis contre les allers-retours. */
public final class ShipNavigator {
    /** Classe utilitaire. */
    private ShipNavigator() {}

    /**
     * Parent à adopter.
     *
     * @param current parent actuel
     * @param distances distance du vaisseau à chaque astre candidat
     * @param hysteresis fraction (0,2 = 20 %) dont un astre doit être plus proche que le parent actuel pour le remplacer
     * @return le nouveau parent, ou {@code null} s'il n'y a rien à changer
     */
    @Nullable
    public static UUID chooseParent(UUID current, Map<UUID, Double> distances, double hysteresis) {
        UUID nearest = null;
        for (Map.Entry<UUID, Double> e : distances.entrySet()) {
            if (nearest == null || e.getValue() < distances.get(nearest)) nearest = e.getKey();
        }
        if (nearest == null || nearest.equals(current)) return null;
        Double currentDistance = distances.get(current);
        // parent actuel hors de la liste (un vaisseau, par exemple) : le plus proche l'emporte
        if (currentDistance == null || distances.get(nearest) < currentDistance * (1 - hysteresis)) return nearest;
        return null;
    }
}
