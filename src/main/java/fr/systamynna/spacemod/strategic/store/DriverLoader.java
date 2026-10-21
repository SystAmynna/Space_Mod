package fr.systamynna.spacemod.strategic.store;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;

import org.jetbrains.annotations.Nullable;

/**
 * Charge le pilote JDBC d'un SGBD autre que SQLite, déposé en jar par l'administrateur. {@code DriverManager} ne voit pas
 * les jars hors du chemin de classes du jeu : on instancie donc le pilote nous-mêmes et on l'utilise directement.
 */
public final class DriverLoader {
    private DriverLoader() {}

    /**
     * Instancie le pilote {@code driverClass} depuis {@code jar}.
     *
     * @param driverClass nom complet de la classe du pilote (par exemple {@code org.mariadb.jdbc.Driver})
     * @param jar jar du pilote ; {@code null} si le pilote est déjà sur le chemin de classes
     * @throws StrategicStoreException si le jar est absent, ou si la classe est introuvable ou n'est pas un pilote
     */
    public static Driver load(String driverClass, @Nullable Path jar) {
        try {
            ClassLoader loader = DriverLoader.class.getClassLoader();
            if (jar != null) {
                if (!Files.isRegularFile(jar)) throw new StrategicStoreException("Jar du pilote JDBC introuvable : " + jar);
                // le parent est le chargeur du mod, pour que le pilote voie java.sql et ses dépendances éventuelles
                loader = new URLClassLoader(new URL[] { jar.toUri().toURL() }, loader);
            }
            Object driver = Class.forName(driverClass, true, loader).getDeclaredConstructor().newInstance();
            if (driver instanceof Driver jdbc) return jdbc;
            throw new StrategicStoreException(driverClass + " n'est pas un java.sql.Driver");
        } catch (ReflectiveOperationException | MalformedURLException e) {
            throw new StrategicStoreException("Pilote JDBC " + driverClass + " impossible à charger" + (jar != null ? " depuis " + jar : ""), e);
        }
    }
}
