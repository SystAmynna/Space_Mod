package fr.systamynna.spacemod.client.render;

import java.io.IOException;
import java.io.UncheckedIOException;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** Shaders du mod (côté client) ; {@code null} tant que les ressources ne sont pas chargées. */
public final class ClientShaders {
    private ClientShaders() {}

    @Nullable
    static ShaderInstance galaxyCloud;
    @Nullable
    static ShaderInstance galaxyStars;
    @Nullable
    static ShaderInstance star, blackHole, gammaEruption, ring, distorsion, distorsionBright, shadow, atmosphere;

    /** À brancher sur le bus du mod, côté client uniquement. */
    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(load(event, "galaxy_cloud"), sh -> galaxyCloud = sh);
            event.registerShader(load(event, "galaxy_stars"), sh -> galaxyStars = sh);
            event.registerShader(load(event, "star"), sh -> star = sh);
            event.registerShader(load(event, "black_hole"), sh -> blackHole = sh);
            event.registerShader(load(event, "gamma_eruption"), sh -> gammaEruption = sh);
            event.registerShader(load(event, "ring"), sh -> ring = sh);
            event.registerShader(load(event, "distorsion"), sh -> distorsion = sh);
            event.registerShader(load(event, "distorsion_bright"), sh -> distorsionBright = sh);
            event.registerShader(load(event, "shadow"), sh -> shadow = sh);
            event.registerShader(load(event, "atmosphere"), sh -> atmosphere = sh);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ShaderInstance load(RegisterShadersEvent event, String name) throws IOException {
        return new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, name), DefaultVertexFormat.POSITION);
    }
}
