package fr.systamynna.spacemod.client;

/** État « connaissance de l'espace » de ce client. Aucune classe client de Minecraft : écrit par le paquet, lu par le rendu. */
public final class ClientKnowledgeState {
    private static volatile boolean on;

    /** Classe utilitaire. */
    private ClientKnowledgeState() {}

    public static void set(boolean value) {
        on = value;
    }

    public static boolean on() {
        return on;
    }
}
