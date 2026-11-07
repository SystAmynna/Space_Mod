package fr.systamynna.spacemod.client.render;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.core.render.RenderAssets;
import fr.systamynna.spacemod.core.render.RenderNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Textures du serveur reçues une fois (reliable) et gardées en mémoire pour la session.
 * Un nom contenant ':' est une texture de resource pack ({@code namespace:chemin}), sans réseau.
 */
// shortcut: pas de cache disque entre sessions ; le hash évite seulement le renvoi quand la config est rediffusée. Ajouter un cache par hash si les textures pèsent.
final class AssetCache {
    private AssetCache() {}

    private record Loaded(String hash, ResourceLocation location) {}

    private static final Map<String, Loaded> LOADED = new HashMap<>();
    private static final Map<String, ResourceLocation> PACK = new HashMap<>();
    private static final Set<String> REQUESTED = new HashSet<>();

    /** Texture de {@code name}, ou {@code null} tant qu'elle n'est pas arrivée (la demande part une seule fois). */
    @Nullable
    static ResourceLocation texture(String name) {
        if (name.indexOf(':') >= 0) return PACK.computeIfAbsent(name, ResourceLocation::tryParse);
        Loaded l = LOADED.get(name);
        if (l != null) return l.location;
        if (RenderAssets.NAME.matcher(name).matches() && REQUESTED.add(name)) PacketDistributor.sendToServer(new RenderNet.AssetRequest(name, ""));
        return null;
    }

    /** La config a changé : redemande ce qu'on a, le serveur ne renvoie que si le hash diffère. */
    static void refresh() { LOADED.forEach((n, l) -> PacketDistributor.sendToServer(new RenderNet.AssetRequest(n, l.hash))); }

    static void onData(RenderNet.AssetData d) {
        if (!RenderAssets.NAME.matcher(d.name()).matches() || !RenderAssets.hash(d.bytes()).equals(d.hash())) return; // paquet invalide : ignoré
        try (NativeImage img = NativeImage.read(new ByteArrayInputStream(d.bytes()))) {
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, "render/" + d.name().substring(0, d.name().length() - 4));
            DynamicTexture tex = new DynamicTexture(new NativeImage(img.format(), img.getWidth(), img.getHeight(), false));
            assert tex.getPixels() != null;
            tex.getPixels().copyFrom(img);
            tex.upload();
            Minecraft.getInstance().getTextureManager().register(loc, tex);
            tex.setFilter(true, false);
            LOADED.put(d.name(), new Loaded(d.hash(), loc));
        } catch (IOException | RuntimeException e) {
            AmynnaSpaceMod.LOGGER.warn("Texture de rendu {} illisible", d.name(), e);
        }
    }

    static void clear() {
        LOADED.clear();
        REQUESTED.clear();
    }
}
