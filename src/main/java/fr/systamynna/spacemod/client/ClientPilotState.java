package fr.systamynna.spacemod.client;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/** Vaisseau que ce client pilote, ou {@code null}. Aucune classe client de Minecraft : écrit par le paquet, lu par {@code PilotClient}. */
public final class ClientPilotState {
    private static volatile UUID ship;

    /** Classe utilitaire. */
    private ClientPilotState() {}

    /** @param shipId vaisseau concerné, ignoré quand {@code piloting} est faux */
    public static void set(boolean piloting, @Nullable UUID shipId) {
        ship = piloting ? shipId : null;
    }

    /** Vaisseau piloté, ou {@code null}. */
    public static @Nullable UUID ship() {
        return ship;
    }

    /** {@code true} si ce client pilote. */
    public static boolean piloting() {
        return ship != null;
    }
}
