package fr.systamynna.spacemod.spaceprovider;

import java.util.List;

import net.minecraft.server.packs.resources.ResourceManager;

/** Source d'objets de l'espace (ressources du mod, datapack, web). Les entrées ne sont qu'une entrée intermédiaire : la base du monde fait foi. */
public interface SpaceProvider {
    /** Données d'une source : sa version (la plus haute des fichiers lus) et ses entrées. */
    record Data(int version, List<ProviderEntry> entries) {
        public static final Data EMPTY = new Data(0, List.of());
    }

    /** Nom stable de la source, clé de la version mémorisée dans la base du monde. */
    String name();

    /** Lit la source ; une entrée invalide est ignorée et journalisée, sans interrompre la lecture. */
    Data load(ResourceManager resources);
}
