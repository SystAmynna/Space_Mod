package fr.systamynna.spacemod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import fr.systamynna.dynamicdimensions.impl.DynamicDimensionsInit;


/** Point d'entrée du mod : branche la couche stratégique sur les événements du serveur. */
@Mod(AmynnaSpaceMod.MODID)
public class AmynnaSpaceMod {
    /** Identifiant du mod, namespace de ses ressources, composants et contrôleurs. */
    public static final String MODID = "amynnaspacemod";
    /** Journal partagé par tout le mod. */
    public static final Logger LOGGER = LogUtils.getLogger();

    public AmynnaSpaceMod(IEventBus modEventBus, ModContainer container) {
        DynamicDimensionsInit.init(modEventBus, FMLEnvironment.dist);
        LOGGER.info("Amynna's Space Mod loaded :)");
    }
}
