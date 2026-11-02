package fr.systamynna.spacemod.core.store;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Configuration du SGBD (config/AmynnaSpaceMod/server.toml). */
public final class StoreConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> URL;
    public static final ModConfigSpec.ConfigValue<String> USER;
    public static final ModConfigSpec.ConfigValue<String> PASSWORD;
    public static final ModConfigSpec.ConfigValue<String> DRIVER_CLASS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("database");
        URL = b.comment("URL JDBC. Vide = SQLite dans le dossier du monde.").define("url", "");
        USER = b.define("user", "");
        PASSWORD = b.define("password", "");
        DRIVER_CLASS = b.comment("Classe du pilote JDBC, chargé depuis config/amynnaspacemod/drivers/*.jar. Vide pour SQLite.")
                .define("driverClass", "");
        b.pop();
        SPEC = b.build();
    }

    private StoreConfig() {}
}
