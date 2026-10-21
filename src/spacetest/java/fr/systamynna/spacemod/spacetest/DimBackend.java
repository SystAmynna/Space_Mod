package fr.systamynna.spacemod.spacetest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;

/** Bibliothèque de dimensions dynamiques utilisée par les tests : Madnessverse (DynamicDimensions abandonné, voir A1). */
interface DimBackend {
    String name();

    /** Vrai si une dimension de ce nom est déjà enregistrée (empêche de lancer le test). */
    boolean exists(MinecraftServer server, ResourceLocation id);

    ServerLevel create(MinecraftServer server, ResourceLocation id, ChunkGenerator gen, DimensionType type);

    /** Décharge en gardant les données. Renvoie false si la bibliothèque refuse. */
    boolean unload(MinecraftServer server, ResourceLocation id);

    ServerLevel load(MinecraftServer server, ResourceLocation id, ChunkGenerator gen, DimensionType type);

    /** Supprime la dimension (les fichiers ne sont supprimés que si la bibliothèque le permet). */
    boolean delete(MinecraftServer server, ResourceLocation id);

    /** Vrai si la bibliothèque supprime les fichiers de la dimension. */
    boolean deletesFiles();

    static DimBackend select() {
        return new MvBackend();
    }
}
