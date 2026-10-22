package fr.systamynna.spacemod.client;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.client.render.SkyShaders;
import fr.systamynna.spacemod.strategic.DimensionManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** Enregistrement des effets de dimension, côté client uniquement. */
@EventBusSubscriber(modid = AmynnaSpaceMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    /** Classe utilitaire. */
    private ClientEvents() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        SkyShaders.register(event);
    }

    @SubscribeEvent
    public static void onRegisterEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(DimensionManager.SPACE_EFFECTS, new SpaceEffects());
    }
}
