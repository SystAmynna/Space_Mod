package fr.systamynna.spacemod.client.render;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** Core shaders du ciel : halo d'étoile et galaxie procédurale. Enregistrés par {@code ClientEvents}. */
public final class SkyShaders {
    private static ShaderInstance starGlow;
    private static ShaderInstance galaxy;

    /** Classe utilitaire. */
    private SkyShaders() {}

    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), id("star_glow"), DefaultVertexFormat.POSITION_TEX_COLOR),
                    s -> starGlow = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), id("galaxy"), DefaultVertexFormat.POSITION_TEX_COLOR),
                    s -> galaxy = s);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ShaderInstance starGlow() {
        return starGlow;
    }

    public static ShaderInstance galaxy() {
        return galaxy;
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, name);
    }
}
