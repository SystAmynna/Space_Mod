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

    /** À brancher sur le bus du mod, côté client uniquement. */
    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(load(event, "galaxy_cloud"), sh -> galaxyCloud = sh);
            event.registerShader(load(event, "galaxy_stars"), sh -> galaxyStars = sh);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ShaderInstance load(RegisterShadersEvent event, String name) throws IOException {
        return new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, name), DefaultVertexFormat.POSITION);
    }
}
