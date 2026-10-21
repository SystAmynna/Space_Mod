package fr.systamynna.spacemod.strategic.store;

/**
 * Échec d'accès à la base de la couche stratégique (connexion, requête, schéma ou version incompatibles). Non
 * récupérable : la couche ne continue jamais sur des données qu'elle n'a pas pu lire ou écrire.
 */
public class StrategicStoreException extends RuntimeException {
    /** Échec sans cause sous-jacente (version incompatible, configuration invalide…). */
    public StrategicStoreException(String message) {
        super(message);
    }

    /** Échec causé par une exception de la base ({@code SQLException}) ou du système. */
    public StrategicStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
