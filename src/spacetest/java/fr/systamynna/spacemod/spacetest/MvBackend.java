package fr.systamynna.spacemod.spacetest;

import dev.lukamadness.madnessverse.api.MadnessverseAPI;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

/** Madnessverse (port d'Infiniverse) : pas de suppression de fichiers, « unload » = désenregistrement. */
final class MvBackend implements DimBackend {
    private static ResourceKey<Level> key(ResourceLocation id) {
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    public String name() { return "Madnessverse"; }

    public boolean exists(MinecraftServer s, ResourceLocation id) {
        return s.getLevel(key(id)) != null;
    }

    public ServerLevel create(MinecraftServer s, ResourceLocation id, ChunkGenerator g, DimensionType t) {
        // Le type doit être un type enregistré (datapack spacemod_test:void) : un Holder.direct ne peut pas être
        // envoyé aux clients (« Can't find id for Direct{DimensionType… } » à la connexion). Le paramètre t est ignoré.
        Holder<DimensionType> type = s.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE)
            .getHolderOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE, ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, id.getPath().startsWith("short") ? "short" : id.getPath().startsWith("mid") ? "mid" : "void")));
        return MadnessverseAPI.get().getOrCreateLevel(s, key(id), () -> new LevelStem(type, g));
    }

    public boolean unload(MinecraftServer s, ResourceLocation id) {
        MadnessverseAPI.get().markDimensionForUnregistration(s, key(id));
        return true;
    }

    public ServerLevel load(MinecraftServer s, ResourceLocation id, ChunkGenerator g, DimensionType t) {
        return create(s, id, g, t);
    }

    public boolean delete(MinecraftServer s, ResourceLocation id) {
        return unload(s, id);
    }

    public boolean deletesFiles() { return false; }
}
