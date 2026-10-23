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

    /** Rayon (années-lumière) autour de l'hôte dans lequel les étoiles voisines sont envoyées au client pour le ciel. */
    public static final ModConfigSpec.DoubleValue STAR_RADIUS_LY;

    /** Période, en ticks, de la surveillance des dimensions d'objets. */
    public static final ModConfigSpec.IntValue DIMENSION_CHECK_PERIOD_TICKS;
    /** Ticks sans joueur après lesquels une dimension d'objet est fermée. */
    public static final ModConfigSpec.IntValue DIMENSION_IDLE_DELAY_TICKS;
    /** Mise à jour de la base du monde depuis des données du web (pas encore implémenté : sans effet pour le moment). */
    public static final ModConfigSpec.BooleanValue WEB_PROVIDER;

    /** Vitesse maximale d'un vaisseau, en unités du parent (mètres) par tick. */
    public static final ModConfigSpec.DoubleValue SHIP_MAX_SPEED;
    /** Accélération (et freinage) d'un vaisseau, en mètres par tick au carré. */
    public static final ModConfigSpec.DoubleValue SHIP_ACCELERATION;
    /** Rotation d'un vaisseau à pleine déflexion du joystick, en degrés par tick. */
    public static final ModConfigSpec.DoubleValue SHIP_TURN_RATE_DEG;
    /** Vitesse de roulis d'un vaisseau, en degrés par tick. */
    public static final ModConfigSpec.DoubleValue SHIP_ROLL_RATE_DEG;
    /** Marge, en mètres, entre le volume d'exclusion de l'objet voisin et celui d'un vaisseau qui apparaît à côté. */
    public static final ModConfigSpec.DoubleValue SHIP_SPAWN_MARGIN;
    /** Période, en ticks, de la recherche de l'astre le plus proche d'un vaisseau (son parent). */
    public static final ModConfigSpec.IntValue SHIP_PARENT_CHECK_PERIOD_TICKS;
    /** Un autre astre devient le parent d'un vaisseau s'il est plus proche d'au moins cette fraction (0,2 = 20 %). */
    public static final ModConfigSpec.DoubleValue SHIP_PARENT_HYSTERESIS;

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

        builder.comment("Rendu du ciel.").push("sky");
        STAR_RADIUS_LY = builder
                .comment("Rayon, en années-lumière, des étoiles voisines dessinées dans le ciel.")
                .defineInRange("starRadiusLy", 100.0, 1.0, 100000.0);
        builder.pop();

        builder.comment("Dimensions des objets de l'espace.").push("dimensions");
        DIMENSION_CHECK_PERIOD_TICKS = builder
                .comment("Période, en ticks, de la surveillance des dimensions (volumes de déclenchement, fermeture).")
                .defineInRange("checkPeriodTicks", 200, 1, Integer.MAX_VALUE);
        DIMENSION_IDLE_DELAY_TICKS = builder
                .comment("Ticks sans aucun joueur après lesquels une dimension d'objet est fermée (6000 = 5 minutes).")
                .defineInRange("idleDelayTicks", 6000, 20, Integer.MAX_VALUE);
        builder.pop();

        builder.comment("Fournisseurs d'objets de l'espace (système solaire, datapacks, web).").push("provider");
        WEB_PROVIDER = builder
                .comment("Met à jour la base du monde depuis des bases de données en ligne (non implémenté pour le moment).")
                .define("WEB_PROVIDER", false);
        builder.pop();

        builder.comment("Vaisseaux pilotés (unités : mètres et ticks).").push("ships");
        SHIP_MAX_SPEED = builder.comment("Vitesse maximale, en mètres par tick.")
                .defineInRange("maxSpeed", 1.0e6, 1.0, 1.0e12);
        SHIP_ACCELERATION = builder.comment("Accélération et freinage, en mètres par tick au carré.")
                .defineInRange("acceleration", 5.0e3, 0.001, 1.0e12);
        SHIP_TURN_RATE_DEG = builder.comment("Lacet et tangage à pleine déflexion, en degrés par tick.")
                .defineInRange("turnRateDegrees", 1.5, 0.01, 30.0);
        SHIP_ROLL_RATE_DEG = builder.comment("Roulis, en degrés par tick.")
                .defineInRange("rollRateDegrees", 2.0, 0.01, 30.0);
        SHIP_SPAWN_MARGIN = builder.comment("Marge d'apparition à côté d'un objet, en mètres.")
                .defineInRange("spawnMargin", 1000.0, 0.0, 1.0e12);
        SHIP_PARENT_CHECK_PERIOD_TICKS = builder.comment("Période, en ticks, de la recherche de l'astre parent le plus proche.")
                .defineInRange("parentCheckPeriodTicks", 20, 1, Integer.MAX_VALUE);
        SHIP_PARENT_HYSTERESIS = builder.comment("Un astre devient le parent s'il est plus proche d'au moins cette fraction que l'actuel.")
                .defineInRange("parentHysteresis", 0.2, 0.0, 0.9);
        builder.pop();

        SPEC = builder.build();
    }

    /** Classe utilitaire : uniquement des constantes. */
    private SpaceConfig() {}
}
