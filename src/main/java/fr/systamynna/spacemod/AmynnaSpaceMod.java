package fr.systamynna.spacemod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(AmynnaSpaceMod.MODID)
public class AmynnaSpaceMod {
    public static final String MODID = "amynnaspacemod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AmynnaSpaceMod(IEventBus modEventBus) {
        LOGGER.info("Amynna's Space Mod loaded");
    }
}
