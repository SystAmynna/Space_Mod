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
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import fr.systamynna.spacemod.commands.SpaceClockCommand;
import fr.systamynna.spacemod.client.ClientLayer;
import fr.systamynna.spacemod.commands.SpaceComponentCommand;
import fr.systamynna.spacemod.commands.SpaceControllerCommand;
import fr.systamynna.spacemod.commands.SpaceLoadCommand;
import fr.systamynna.spacemod.commands.SpaceLookAtCommand;
import fr.systamynna.spacemod.commands.SpaceObjectCommand;
import fr.systamynna.spacemod.commands.SpacePlayerCommand;
import fr.systamynna.spacemod.commands.SpaceProviderCommand;
import fr.systamynna.spacemod.commands.SpaceRenderCommand;
import fr.systamynna.spacemod.core.render.RenderConfigs;
import fr.systamynna.spacemod.core.render.RenderNet;
import fr.systamynna.spacemod.core.SpaceNet;
import fr.systamynna.spacemod.core.Subscriptions;
import fr.systamynna.spacemod.core.StrategicLayer;
import fr.systamynna.spacemod.core.clock.ClockData;
import fr.systamynna.spacemod.core.clock.ClockNet;
import fr.systamynna.spacemod.core.clock.SpaceClock;
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
    /** Abonnements des joueurs à la couche stratégique. */
    public static final Subscriptions SUBS = new Subscriptions(STRATEGIC);
    /** Configs de ciel spatial par dimension (/spacerender) et assets de rendu. */
    public static final RenderConfigs RENDER = new RenderConfigs(STRATEGIC, SUBS);

    public AmynnaSpaceMod(IEventBus modEventBus, ModContainer container) {
        DynamicDimensionsInit.init(modEventBus, FMLEnvironment.dist);
        container.registerConfig(ModConfig.Type.COMMON, StoreConfig.SPEC, "AmynnaSpaceMod/server.toml");
        modEventBus.addListener(ClockNet::register);
        modEventBus.addListener(SpaceNet::register);
        modEventBus.addListener(RenderNet::register);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> {
            SpaceClockCommand.register(e.getDispatcher());
            SpaceObjectCommand.register(e.getDispatcher());
            SpaceLoadCommand.register(e.getDispatcher());
            SpaceComponentCommand.register(e.getDispatcher());
            SpaceControllerCommand.register(e.getDispatcher());
            SpacePlayerCommand.register(e.getDispatcher());
            SpaceProviderCommand.register(e.getDispatcher());
            SpaceRenderCommand.register(e.getDispatcher());
            SpaceLookAtCommand.register(e.getDispatcher());
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> STRATEGIC.tickControllers(SpaceClock.SERVER.now() / 50));
        // Le rendu spatial (SpaceRenderer) rappelle step() à chaque image ; ce tick garde la couche à jour quand il est inactif.
        if (FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ClientLayer.INSTANCE.step(SpaceClock.CLIENT.now() / 50));
            fr.systamynna.spacemod.client.render.ClientRenderInit.init();
        }
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                PacketDistributor.sendToPlayer(p, ClockNet.currentSync());
                SUBS.login(p);
                RENDER.send(p);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) RENDER.send(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) SUBS.logout(p);
        });
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> {
            ClockData.load(e.getServer());
            STRATEGIC.start(e.getServer().getWorldPath(LevelResource.ROOT));
            SUBS.start(e.getServer());
            RENDER.start(e.getServer());
        });
        // LevelEvent.Save part une fois par dimension : on ne réagit qu'à l'overworld.
        NeoForge.EVENT_BUS.addListener((LevelEvent.Save e) -> {
            if (e.getLevel() instanceof ServerLevel l && l.dimension() == Level.OVERWORLD) {
                ClockData.markDirty();
                STRATEGIC.flush();
                RENDER.flush();
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            RENDER.stop();
            SUBS.stop();
            STRATEGIC.stop();
        });
        LOGGER.info("Amynna's Space Mod loaded :)");
    }
}
