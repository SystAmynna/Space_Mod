package fr.systamynna.spacemod.client.render;

import fr.systamynna.spacemod.client.ClientLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Branche le rendu spatial côté client uniquement. */
public final class ClientRenderInit {
    private ClientRenderInit() {}

    public static void init() {
        ClientLayer.INSTANCE.addListener(SpaceRenderer.INSTANCE);
        // Chaque dimension crée un nouveau ClientLevel : on y réinstalle l'enrobage des effets (ClientLevel.effects, rendu public par l'AT).
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            ClientLevel l = Minecraft.getInstance().level;
            if (l != null && !(l.effects instanceof SpaceSkyEffects)) l.effects = new SpaceSkyEffects(l.effects);
        });
        // Brouillard (et fond) noirs quand le ciel spatial est actif.
        NeoForge.EVENT_BUS.addListener((ViewportEvent.ComputeFogColor e) -> {
            ClientLevel l = Minecraft.getInstance().level;
            if (l != null && SpaceRenderer.config(l) != null) {
                e.setRed(0);
                e.setGreen(0);
                e.setBlue(0);
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            ClientRenderState.set(null);
            AssetCache.clear();
        });
    }
}
