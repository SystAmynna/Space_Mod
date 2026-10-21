package fr.systamynna.spacemod.strategic.store;

import java.util.List;

/**
 * Particularités des SGBD pour le seul SQL qui n'est pas portable : le type de colonne binaire, la création des index
 * et les réglages à l'ouverture. Le reste des requêtes est du SQL standard. Déduit du préfixe de l'URL JDBC.
 * <p>
 * Seul {@link #SQLITE} est testé par ce mod ; {@link #MYSQL} (aussi MariaDB) et {@link #POSTGRESQL} sont fournis sans
 * avoir été éprouvés sur un serveur.
 */
public enum Dialect {
    /** SQLite, intégré au mod. */
    SQLITE("BLOB"),
    /** MySQL et MariaDB. */
    MYSQL("LONGBLOB"),
    /** PostgreSQL. */
    POSTGRESQL("BYTEA");

    /** Type SQL d'une colonne d'octets bruts pour ce SGBD. */
    private final String binaryType;

    /** @param binaryType type SQL d'une colonne d'octets bruts pour ce SGBD */
    Dialect(String binaryType) {
        this.binaryType = binaryType;
    }

    /**
     * Dialecte correspondant à une URL JDBC.
     *
     * @throws StrategicStoreException si le SGBD n'est pas géré
     */
    public static Dialect fromUrl(String url) {
        if (url.startsWith("jdbc:sqlite:")) return SQLITE;
        if (url.startsWith("jdbc:mysql:") || url.startsWith("jdbc:mariadb:")) return MYSQL;
        if (url.startsWith("jdbc:postgresql:")) return POSTGRESQL;
        throw new StrategicStoreException("SGBD non géré (jdbc:sqlite:, jdbc:mysql:, jdbc:mariadb: ou jdbc:postgresql: attendu) : " + url);
    }

    /** Requêtes exécutées à l'ouverture de chaque connexion (réglages propres au SGBD). */
    List<String> openStatements() {
        // WAL : les lectures ne bloquent pas l'écriture ; NORMAL : sûr avec WAL, sans fsync à chaque transaction
        return this == SQLITE ? List.of("PRAGMA journal_mode=WAL", "PRAGMA synchronous=NORMAL") : List.of();
    }

    /**
     * Requêtes de création du schéma (idempotentes) pour un préfixe de table donné.
     * <p>
     * {@code objects} : {@code id} identifiant (36 caractères), {@code unit_id} unité de
     * chargement, {@code hosted_dimension} dimension hébergée (unique), {@code data} l'objet en NBT compressé.
     */
    List<String> schema(String prefix) {
        String meta = "CREATE TABLE IF NOT EXISTS " + prefix + "meta (k VARCHAR(64) PRIMARY KEY, v VARCHAR(255) NOT NULL)";
        String columns = "id VARCHAR(36) PRIMARY KEY, unit_id VARCHAR(36), "
                + "hosted_dimension VARCHAR(255) UNIQUE, data " + binaryType + " NOT NULL";
        if (this == MYSQL) {
            // MySQL n'a pas CREATE INDEX IF NOT EXISTS : les index sont déclarés dans la table
            return List.of(meta, "CREATE TABLE IF NOT EXISTS " + prefix + "objects (" + columns
                    + ", INDEX " + prefix + "objects_unit (unit_id))");
        }
        return List.of(meta,
                "CREATE TABLE IF NOT EXISTS " + prefix + "objects (" + columns + ")",
                "CREATE INDEX IF NOT EXISTS " + prefix + "objects_unit ON " + prefix + "objects (unit_id)");
    }
}
