package fr.systamynna.spacemod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import fr.systamynna.spacemod.commands.SpaceClockCommand;
import fr.systamynna.spacemod.commands.SpaceObjectCommand;
import fr.systamynna.spacemod.core.StrategicLayer;
import fr.systamynna.spacemod.core.clock.ClockData;
import fr.systamynna.spacemod.core.clock.ClockNet;
import fr.systamynna.spacemod.core.store.StoreConfig;
import fr.systamynna.dynamicdimensions.impl.DynamicDimensionsInit;


/** Point d'entrée du mod : branche la couche stratégique sur les événements du serveur. */
@Mod(AmynnaSpaceMod.MODID)
public class AmynnaSpaceMod {
    /** Identifiant du mod, namespace de ses ressources, composants et contrôleurs. */
    public static final String MODID = "amynnaspacemod";
    /** Journal partagé par tout le mod. */
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Couche stratégique du serveur courant. */
    public static final StrategicLayer STRATEGIC = new StrategicLayer();

    public AmynnaSpaceMod(IEventBus modEventBus, ModContainer container) {
        DynamicDimensionsInit.init(modEventBus, FMLEnvironment.dist);
        container.registerConfig(ModConfig.Type.COMMON, StoreConfig.SPEC, "AmynnaSpaceMod/server.toml");
        modEventBus.addListener(ClockNet::register);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> {
            SpaceClockCommand.register(e.getDispatcher());
            SpaceObjectCommand.register(e.getDispatcher());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) PacketDistributor.sendToPlayer(p, ClockNet.currentSync());
        });
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> {
            ClockData.load(e.getServer());
            STRATEGIC.start(e.getServer().getWorldPath(LevelResource.ROOT));
        });
        // LevelEvent.Save part une fois par dimension : on ne réagit qu'à l'overworld.
        NeoForge.EVENT_BUS.addListener((LevelEvent.Save e) -> {
            if (e.getLevel() instanceof ServerLevel l && l.dimension() == Level.OVERWORLD) {
                ClockData.markDirty();
                STRATEGIC.flush();
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> STRATEGIC.stop());
        LOGGER.info("Amynna's Space Mod loaded :)");
    }
}
