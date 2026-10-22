package fr.systamynna.spacemod.spacetest;

import fr.systamynna.dynamicdimensions.api.DynamicDimensionRegistry;
import fr.systamynna.dynamicdimensions.api.PlayerRemover;
import fr.systamynna.dynamicdimensions.api.DynamicDimensionProperties;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;

/** DynamicDimensions (fork intégré, fr.systamynna.dynamicdimensions) : unload sépare de delete, delete supprime les fichiers ; jamais de retrait avec un joueur dedans. */
final class DdBackend implements DimBackend {
    private static DynamicDimensionRegistry reg(MinecraftServer s) {
        return DynamicDimensionRegistry.from(s);
    }

    public String name() { return "DynamicDimensions"; }

    public boolean exists(MinecraftServer s, ResourceLocation id) {
        return reg(s).anyDimensionExists(id);
    }

    public ServerLevel create(MinecraftServer s, ResourceLocation id, ChunkGenerator g, DimensionType t) {
        return reg(s).createDynamicDimension(id, g, t, props(id.getPath().endsWith("gravity") ? -11f : 0f));
    }

    public boolean unload(MinecraftServer s, ResourceLocation id) {
        return reg(s).unloadDynamicDimension(id, PlayerRemover.REFUSE_IF_OCCUPIED);
    }

    public ServerLevel load(MinecraftServer s, ResourceLocation id, ChunkGenerator g, DimensionType t) {
        return reg(s).loadDynamicDimension(id, g, t, props(id.getPath().endsWith("gravity") ? -11f : 0f));
    }

    public boolean delete(MinecraftServer s, ResourceLocation id) {
        return reg(s).deleteDynamicDimension(id, PlayerRemover.REFUSE_IF_OCCUPIED);
    }

    public boolean deletesFiles() { return true; }

    private static DynamicDimensionProperties props(float gy) {
        return new DynamicDimensionProperties() {
            public int priority() { return 100; }
            public Vector3f baseGravity() { return new Vector3f(0, gy, 0); }
            public double basePressure() { return gy == 0f ? 0.0 : 1.0; }
            public float universalDrag() { return 0f; }
            public Vector3f magneticNorth() { return new Vector3f(0, 0, 1); }
        };
    }
}
