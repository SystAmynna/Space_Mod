package fr.systamynna.spacemod.client;

import org.jetbrains.annotations.Nullable;

import fr.systamynna.spacemod.network.SkyContextPacket;

/**
 * Dernier contexte de ciel reçu du serveur (objet hôte et port d'arrivée). Aucune classe client importée : chargeable
 * partout. Lu par {@code SkyRenderer}.
 */
public final class SkyContext {
    private static volatile SkyContextPacket current;

    /** Classe utilitaire. */
    private SkyContext() {}

    /** Mémorise le contexte reçu à l'entrée dans une dimension d'objet. */
    public static void set(SkyContextPacket packet) {
        current = packet;
    }

    /** Contexte courant, ou {@code null} si aucun n'a été reçu. */
    public static @Nullable SkyContextPacket get() {
        return current;
    }
}
