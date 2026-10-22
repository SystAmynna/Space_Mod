package fr.systamynna.dynamicdimensions;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** La bibliothèque src/dyndim ne doit jamais dépendre du code du mod (le classpath de Gradle l'interdit déjà, ceci protège les ressources et les futurs changements de build). */
class SeparationTest {
    @Test
    void dyndimNeReferencePasLeMod() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of(System.getProperty("project.dir", "."), "src", "dyndim"))) {
            List<Path> bad = files.filter(Files::isRegularFile).filter(p -> (p.toString().endsWith(".java") || p.toString().endsWith(".json"))).filter(p -> {
                try {
                    return Files.readString(p).contains("fr.systamynna.spacemod");
                } catch (IOException e) {
                    return true;
                }
            }).toList();
            assertTrue(bad.isEmpty(), "src/dyndim référence le mod : " + bad);
        }
    }
}
