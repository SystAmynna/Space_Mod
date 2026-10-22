package fr.systamynna.spacemod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import fr.systamynna.dynamicdimensions.impl.DynamicDimensionsInit;
import fr.systamynna.spacemod.spaceprovider.SpaceProviderEvents;
import fr.systamynna.spacemod.strategic.SpaceConfig;
import fr.systamynna.spacemod.strategic.StrategicEvents;

/** Point d'entrée du mod : branche la couche stratégique sur les événements du serveur. */
@Mod(AmynnaSpaceMod.MODID)
public class AmynnaSpaceMod {
    /** Identifiant du mod, namespace de ses ressources, composants et contrôleurs. */
    public static final String MODID = "amynnaspacemod";
    /** Journal partagé par tout le mod. */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Constructeur appelé par NeoForge au chargement du mod. Inscrit les gestionnaires statiques de
     * {@link StrategicEvents} sur le bus d'événements du jeu (et non celui du mod, qui ne porte que l'initialisation), et
     * déclare la configuration commune ({@code config/amynnaspacemod-common.toml}).
     *
     * @param modEventBus bus d'événements du mod (paquets réseau de la bibliothèque de dimensions)
     * @param container conteneur du mod, auprès duquel la configuration est enregistrée
     */
    public AmynnaSpaceMod(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SpaceConfig.SPEC);
        NeoForge.EVENT_BUS.register(StrategicEvents.class);
        NeoForge.EVENT_BUS.register(SpaceProviderEvents.class);
        DynamicDimensionsInit.init(modEventBus, FMLEnvironment.dist);
        LOGGER.info("Amynna's Space Mod loaded :)");
    }
}
