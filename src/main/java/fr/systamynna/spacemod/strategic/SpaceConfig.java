package fr.systamynna.spacemod.strategic;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration commune du mod, écrite dans {@code config/amynnaspacemod-common.toml}. Les valeurs ne sont lisibles qu'après
 * le chargement de la configuration (avant {@code FMLCommonSetupEvent}) : la couche les lit à
 * {@code ServerAboutToStartEvent}. Les modifications du fichier en cours de partie ne sont pas relues par la couche :
 * la base est ouverte une fois, au démarrage du serveur.
 */
public final class SpaceConfig {
    /** Marque, dans l'URL de la base, remplacée par le dossier du monde. */
    public static final String WORLD_PLACEHOLDER = "{world}";

    /** Description de la configuration, enregistrée auprès de NeoForge par le constructeur du mod. */
    public static final ModConfigSpec SPEC;

    /** URL JDBC de la base de la couche stratégique ; {@code {world}} est remplacé par le dossier du monde. */
    public static final ModConfigSpec.ConfigValue<String> DATABASE_URL;
    /** Utilisateur de la base (vide pour SQLite). */
    public static final ModConfigSpec.ConfigValue<String> DATABASE_USER;
    /** Mot de passe de la base (vide pour SQLite). Écrit en clair dans le fichier de configuration. */
    public static final ModConfigSpec.ConfigValue<String> DATABASE_PASSWORD;
    /** Préfixe des tables, pour partager une base entre plusieurs mondes. */
    public static final ModConfigSpec.ConfigValue<String> TABLE_PREFIX;
    /** Classe du pilote JDBC d'un SGBD autre que SQLite (vide pour SQLite). */
    public static final ModConfigSpec.ConfigValue<String> DRIVER_CLASS;
    /** Nom du jar du pilote, dans {@code config/amynnaspacemod/drivers/} (vide si le pilote est déjà sur le chemin de classes). */
    public static final ModConfigSpec.ConfigValue<String> DRIVER_JAR;
    /** Ticks sans accès ni joueur après lesquels les corps d'un système sont déchargés. */
    public static final ModConfigSpec.IntValue UNLOAD_DELAY_TICKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Base de données de la couche stratégique (objets de l'espace).",
                "SQLite est intégré au mod. Pour un autre SGBD (MariaDB, MySQL, PostgreSQL), renseignez l'URL et déposez le",
                "pilote JDBC dans config/amynnaspacemod/drivers/. Seul SQLite est testé.").push("database");
        DATABASE_URL = builder
                .comment("URL JDBC. {world} est remplacé par le dossier du monde.")
                .define("url", "jdbc:sqlite:" + WORLD_PLACEHOLDER + "/amynnaspacemod/strategic.db");
        DATABASE_USER = builder.comment("Utilisateur (vide pour SQLite).").define("user", "");
        DATABASE_PASSWORD = builder
                .comment("Mot de passe (vide pour SQLite). Attention : stocké en clair dans ce fichier.")
                .define("password", "");
        TABLE_PREFIX = builder
                .comment("Préfixe des tables (lettres, chiffres et _ ; 32 caractères au plus), pour partager une base entre mondes.")
                .define("tablePrefix", "spacemod_");
        DRIVER_CLASS = builder
                .comment("Classe du pilote JDBC d'un autre SGBD, par exemple org.mariadb.jdbc.Driver (vide pour SQLite).")
                .define("driverClass", "");
        DRIVER_JAR = builder
                .comment("Nom du jar du pilote dans config/amynnaspacemod/drivers/ (vide si le pilote est déjà chargé).")
                .define("driverJar", "");
        builder.pop();

        builder.comment("Chargement des objets de l'espace.").push("layer");
        UNLOAD_DELAY_TICKS = builder
                .comment("Ticks sans accès ni joueur après lesquels les corps d'un système sont déchargés (6000 = 5 minutes).")
                .defineInRange("unloadDelayTicks", 6000, 20, Integer.MAX_VALUE);
        builder.pop();

        SPEC = builder.build();
    }

    /** Classe utilitaire : uniquement des constantes. */
    private SpaceConfig() {}
}
