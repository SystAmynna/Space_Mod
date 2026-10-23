package fr.systamynna.spacemod.strategic.store;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import fr.systamynna.spacemod.strategic.SpaceClock;
import fr.systamynna.spacemod.strategic.SpaceObject;
import fr.systamynna.spacemod.strategic.component.ComponentTypes;
import fr.systamynna.spacemod.strategic.component.HostedDimension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;

/**
 * Magasin JDBC : deux tables, {@code <préfixe>meta} (version de format, horloge) et {@code <préfixe>objects} (un objet par
 * ligne). L'objet entier est conservé en NBT compressé dans {@code data} (composants et contrôleurs de tiers compris) ;
 * les colonnes {@code unit_id} et {@code hosted_dimension} ne servent qu'à retrouver et indexer les
 * lignes. Toutes les requêtes sont du SQL standard ; {@link Dialect} ne couvre que le type binaire, les index et les
 * réglages d'ouverture.
 * <p>
 * Une seule connexion, utilisée depuis le thread serveur. L'atomicité d'une sauvegarde vient de la transaction ; une base
 * illisible ou d'une version plus récente fait échouer l'ouverture plutôt que d'être recréée.
 */
public final class JdbcStrategicStore implements StrategicStore {
    /** Version du schéma et du format des lignes ; une base plus récente est refusée à l'ouverture. */
    public static final int FORMAT_VERSION = 1;

    /** Préfixe de table autorisé : lettres, chiffres et {@code _} seulement (il est inséré tel quel dans le SQL). */
    private static final Pattern PREFIX = Pattern.compile("[A-Za-z0-9_]{0,32}");

    /** Clé de {@code meta} : version du format. */
    private static final String FORMAT_KEY = "format_version";
    /** Clé de {@code meta} : temps de jeu de l'ancre de l'horloge. */
    private static final String CLOCK_TICK_KEY = "clock_anchor_game_tick";
    /** Clé de {@code meta} : temps spatial de l'ancre de l'horloge. */
    private static final String CLOCK_SPACE_KEY = "clock_anchor_space_ticks";
    /** Clé de {@code meta} : rythme de l'horloge. */
    private static final String CLOCK_RATE_KEY = "clock_rate";

    /** Connexion unique, en validation automatique sauf pendant {@link #save}. */
    private final Connection connection;
    /** Nom de la table des métadonnées (préfixe compris). */
    private final String metaTable;
    /** Nom de la table des objets (préfixe compris). */
    private final String objectsTable;
    /** {@code true} après {@link #close()}. */
    private boolean closed;

    /**
     * Prépare la base sur une connexion ouverte : réglages du SGBD, création des tables et index manquants, vérification
     * de la version de format.
     *
     * @param prefix préfixe de table, déjà validé
     * @throws StrategicStoreException si la préparation échoue ou si la base est d'une version plus récente
     */
    private JdbcStrategicStore(Connection connection, Dialect dialect, String prefix) {
        this.connection = connection;
        this.metaTable = prefix + "meta";
        this.objectsTable = prefix + "objects";
        try (Statement statement = connection.createStatement()) {
            for (String sql : dialect.openStatements()) statement.execute(sql);
            for (String sql : dialect.schema(prefix)) statement.execute(sql);
        } catch (SQLException e) {
            throw new StrategicStoreException("Initialisation de la base stratégique", e);
        }
        checkFormatVersion();
    }

    /**
     * Ouvre (et crée si besoin) la base.
     *
     * @param url URL JDBC ({@code jdbc:sqlite:}, {@code jdbc:mysql:}, {@code jdbc:mariadb:} ou {@code jdbc:postgresql:})
     * @param user utilisateur, ou {@code null} / vide si le SGBD n'en demande pas
     * @param password mot de passe, ou {@code null} / vide
     * @param tablePrefix préfixe des tables, pour séparer plusieurs mondes dans une même base
     * @param driver pilote chargé par {@link DriverLoader}, ou {@code null} pour SQLite (intégré) ou un pilote déjà
     *        enregistré auprès de {@code DriverManager}
     * @throws StrategicStoreException si le préfixe est invalide, si la connexion échoue, ou si la base est d'une version
     *         plus récente
     */
    public static JdbcStrategicStore open(String url, @Nullable String user, @Nullable String password,
            String tablePrefix, @Nullable java.sql.Driver driver) {
        if (!PREFIX.matcher(tablePrefix).matches()) {
            throw new StrategicStoreException("Préfixe de table invalide (lettres, chiffres et _ seulement) : " + tablePrefix);
        }
        Dialect dialect = Dialect.fromUrl(url);
        Properties properties = new Properties();
        if (user != null && !user.isEmpty()) properties.setProperty("user", user);
        if (password != null && !password.isEmpty()) properties.setProperty("password", password);
        try {
            Connection connection;
            if (driver != null) connection = driver.connect(url, properties);
            else if (dialect == Dialect.SQLITE) connection = new org.sqlite.JDBC().connect(url, properties);
            else connection = DriverManager.getConnection(url, properties);
            if (connection == null) throw new StrategicStoreException("Le pilote JDBC n'accepte pas l'URL : " + url);
            return new JdbcStrategicStore(connection, dialect, tablePrefix);
        } catch (SQLException e) {
            throw new StrategicStoreException("Connexion à la base stratégique impossible : " + url, e);
        }
    }

    /**
     * SQLite dans un fichier (créé avec ses dossiers parents), préfixe de table {@code spacemod_}. Pratique pour les tests ;
     * le mod lui-même passe par {@link #open} avec sa configuration.
     */
    public static JdbcStrategicStore sqlite(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new StrategicStoreException("Création du dossier de la base : " + file, e);
        }
        return open("jdbc:sqlite:" + file.toAbsolutePath(), null, null, "spacemod_", null);
    }

    /** SQLite en mémoire, perdue à la fermeture, préfixe de table {@code spacemod_}. Pratique pour les tests. */
    public static JdbcStrategicStore inMemory() {
        return open("jdbc:sqlite::memory:", null, null, "spacemod_", null);
    }

    /**
     * Écrit la version de format dans une base neuve, ou refuse une base plus récente que ce mod.
     *
     * @throws StrategicStoreException si la base est d'une version plus récente
     */
    private void checkFormatVersion() {
        String stored = readMeta(FORMAT_KEY);
        if (stored == null) {
            writeMeta(FORMAT_KEY, Integer.toString(FORMAT_VERSION));
            return;
        }
        int version = Integer.parseInt(stored);
        if (version > FORMAT_VERSION) {
            throw new StrategicStoreException("Base stratégique en version " + version
                    + ", ce mod gère jusqu'à la version " + FORMAT_VERSION);
        }
    }

    /** Relit les trois valeurs de l'horloge dans {@code meta} ; horloge neuve si l'une manque. */
    @Override
    public SpaceClock loadClock() {
        String tick = readMeta(CLOCK_TICK_KEY);
        String space = readMeta(CLOCK_SPACE_KEY);
        String rate = readMeta(CLOCK_RATE_KEY);
        if (tick == null || space == null || rate == null) return new SpaceClock();
        // Double.toString / parseDouble font l'aller-retour exact : l'horloge garde ses bits
        return SpaceClock.restored(Long.parseLong(tick), Double.parseDouble(space), Double.parseDouble(rate));
    }

    /** Relit les objets dont {@code unit_id} est nul : cadres et corps hors de tout système. */
    @Override
    public List<StoredObject> loadResident() {
        return query("SELECT unit_id, data FROM " + objectsTable + " WHERE unit_id IS NULL", null);
    }

    /** Relit les objets dont {@code unit_id} est le système donné (index sur cette colonne). */
    @Override
    public List<StoredObject> loadUnit(UUID unitId) {
        return query("SELECT unit_id, data FROM " + objectsTable + " WHERE unit_id = ?", unitId.toString());
    }

    /** Lit la seule colonne {@code unit_id} de la ligne de l'objet, sans désérialiser l'objet. */
    @Override
    @Nullable
    public Placement placementOf(UUID objectId) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT unit_id FROM " + objectsTable + " WHERE id = ?")) {
            statement.setString(1, objectId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                String unit = rows.getString(1);
                return new Placement(unit == null ? null : UUID.fromString(unit));
            }
        } catch (SQLException e) {
            throw new StrategicStoreException("Lecture de l'emplacement de " + objectId, e);
        }
    }

    @Override
    public void delete(UUID objectId) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + objectsTable + " WHERE id = ?")) {
            statement.setString(1, objectId.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StrategicStoreException("Suppression de " + objectId, e);
        }
    }

    /** Lit les colonnes {@code hosted_dimension} et {@code id} des seules lignes qui hébergent une dimension. */
    @Override
    public Map<ResourceLocation, UUID> hostedDimensions() {
        Map<ResourceLocation, UUID> result = new HashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT hosted_dimension, id FROM " + objectsTable
                        + " WHERE hosted_dimension IS NOT NULL")) {
            while (rows.next()) result.put(ResourceLocation.parse(rows.getString(1)), UUID.fromString(rows.getString(2)));
        } catch (SQLException e) {
            throw new StrategicStoreException("Lecture des dimensions hébergées", e);
        }
        return result;
    }

    /**
     * Une transaction : mise à jour ou insertion de chaque objet, puis de l'horloge si elle est fournie ; validée en bloc,
     * annulée en entier au premier échec (par exemple dimension hébergée par deux lignes, refusée par la contrainte d'unicité).
     */
    @Override
    public void save(Collection<StoredObject> objects, @Nullable SpaceClock clock) {
        try {
            connection.setAutoCommit(false);
            try {
                for (StoredObject stored : objects) upsert(stored);
                if (clock != null) {
                    writeMeta(CLOCK_TICK_KEY, Long.toString(clock.anchorGameTick()));
                    writeMeta(CLOCK_SPACE_KEY, Double.toString(clock.anchorSpaceTicks()));
                    writeMeta(CLOCK_RATE_KEY, Double.toString(clock.rate()));
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                // rien n'est écrit : la transaction est annulée en entier
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new StrategicStoreException("Sauvegarde de la couche stratégique", e);
        }
    }

    /** Ferme la connexion ; les appels suivants sont sans effet. */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            connection.close();
        } catch (SQLException e) {
            throw new StrategicStoreException("Fermeture de la base stratégique", e);
        }
    }

    /**
     * Met à jour la ligne de l'objet ou, si elle n'existe pas (aucune ligne modifiée), l'insère. Deux requêtes plutôt
     * qu'une syntaxe d'upsert, qui diffère d'un SGBD à l'autre.
     */
    private void upsert(StoredObject stored) throws SQLException {
        SpaceObject object = stored.object();
        HostedDimension hosted = object.get(ComponentTypes.HOSTED_DIMENSION);
        String dimension = hosted == null || hosted.dimension() == null ? null : hosted.dimension().toString();
        String unit = stored.unitId() == null ? null : stored.unitId().toString();
        byte[] data = toBytes(object.save());
        try (PreparedStatement update = connection.prepareStatement("UPDATE " + objectsTable
                + " SET unit_id = ?, hosted_dimension = ?, data = ? WHERE id = ?")) {
            update.setString(1, unit);
            update.setString(2, dimension);
            update.setBytes(3, data);
            update.setString(4, object.id().toString());
            if (update.executeUpdate() > 0) return;
        }
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + objectsTable
                + " (id, unit_id, hosted_dimension, data) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, object.id().toString());
            insert.setString(2, unit);
            insert.setString(3, dimension);
            insert.setBytes(4, data);
            insert.executeUpdate();
        }
    }

    /**
     * Exécute une requête {@code SELECT unit_id, data} avec au plus un paramètre texte et relit les objets.
     *
     * @param parameter valeur du paramètre {@code ?} unique de la requête, ou {@code null} si elle n'en a pas
     */
    private List<StoredObject> query(String sql, @Nullable String parameter) {
        List<StoredObject> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameter != null) statement.setString(1, parameter);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String unit = rows.getString(1);
                    result.add(new StoredObject(SpaceObject.load(fromBytes(rows.getBytes(2))), unit == null ? null : UUID.fromString(unit)));
                }
            }
        } catch (SQLException e) {
            throw new StrategicStoreException("Lecture des objets stratégiques", e);
        }
        return result;
    }

    /** Valeur d'une clé de {@code meta}, ou {@code null} si elle n'existe pas. */
    @Nullable
    @Override
    public String meta(String key) {
        return readMeta(key);
    }

    @Override
    public void setMeta(String key, String value) {
        writeMeta(key, value);
    }

    /** Valeur d'une clé de {@code meta}, ou {@code null} si elle n'existe pas. */
    @Nullable
    private String readMeta(String key) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT v FROM " + metaTable + " WHERE k = ?")) {
            statement.setString(1, key);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new StrategicStoreException("Lecture de la métadonnée " + key, e);
        }
    }

    /** Écrit une clé de {@code meta} (mise à jour, sinon insertion), dans la transaction en cours s'il y en a une. */
    private void writeMeta(String key, String value) {
        try {
            try (PreparedStatement update = connection.prepareStatement("UPDATE " + metaTable + " SET v = ? WHERE k = ?")) {
                update.setString(1, value);
                update.setString(2, key);
                if (update.executeUpdate() > 0) return;
            }
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + metaTable + " (k, v) VALUES (?, ?)")) {
                insert.setString(1, key);
                insert.setString(2, value);
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new StrategicStoreException("Écriture de la métadonnée " + key, e);
        }
    }

    /** Sérialise un NBT compressé (gzip, comme les fichiers {@code .dat} de Minecraft). */
    private static byte[] toBytes(CompoundTag tag) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(tag, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new StrategicStoreException("Sérialisation d'un objet", e);
        }
    }

    /** Relit un NBT écrit par {@link #toBytes}. @throws StrategicStoreException si les octets sont corrompus */
    private static CompoundTag fromBytes(byte[] bytes) {
        try {
            return NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new StrategicStoreException("Objet illisible dans la base stratégique", e);
        }
    }
}
