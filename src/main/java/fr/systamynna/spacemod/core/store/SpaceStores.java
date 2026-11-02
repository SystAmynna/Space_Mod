package fr.systamynna.spacemod.core.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Stream;

import net.neoforged.fml.loading.FMLPaths;

/** Ouvre le store selon {@link StoreConfig}. */
public final class SpaceStores {
    private SpaceStores() {}

    public static SpaceStore open(Path worldDir) {
        try {
            String url = StoreConfig.URL.get();
            if (url.isBlank()) {
                Path dir = Files.createDirectories(worldDir.resolve("amynnaspacemod"));
                url = "jdbc:sqlite:" + dir.resolve("space.db");
            }
            String driverClass = StoreConfig.DRIVER_CLASS.get();
            if (!driverClass.isBlank()) registerExternalDriver(driverClass);
            else Class.forName("org.sqlite.JDBC"); // enregistrement explicite : le ServiceLoader ne voit pas le Jar-in-Jar
            Connection con = DriverManager.getConnection(url, StoreConfig.USER.get(), StoreConfig.PASSWORD.get());
            return new JdbcSpaceStore(con);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (SQLException | ReflectiveOperationException e) {
            throw new UncheckedIOException(new IOException("Base de données inaccessible : " + e.getMessage(), e));
        }
    }

    private static void registerExternalDriver(String driverClass) throws IOException, ReflectiveOperationException, SQLException {
        Path dir = FMLPaths.CONFIGDIR.get().resolve("amynnaspacemod/drivers");
        Files.createDirectories(dir);
        URL[] jars;
        try (Stream<Path> s = Files.list(dir)) {
            jars = s.filter(p -> p.toString().endsWith(".jar")).map(p -> {
                try { return p.toUri().toURL(); } catch (IOException e) { throw new UncheckedIOException(e); }
            }).toArray(URL[]::new);
        }
        // Le loader reste ouvert : le pilote en a besoin tant que le store vit.
        URLClassLoader cl = new URLClassLoader(jars, SpaceStores.class.getClassLoader());
        Driver d = (Driver) Class.forName(driverClass, true, cl).getDeclaredConstructor().newInstance();
        DriverManager.registerDriver(new Driver() { // DriverManager refuse les pilotes d'un autre loader
            public Connection connect(String u, Properties p) throws SQLException { return d.connect(u, p); }
            public boolean acceptsURL(String u) throws SQLException { return d.acceptsURL(u); }
            public java.sql.DriverPropertyInfo[] getPropertyInfo(String u, Properties p) throws SQLException { return d.getPropertyInfo(u, p); }
            public int getMajorVersion() { return d.getMajorVersion(); }
            public int getMinorVersion() { return d.getMinorVersion(); }
            public boolean jdbcCompliant() { return d.jdbcCompliant(); }
            public Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException { return d.getParentLogger(); }
        });
    }
}
