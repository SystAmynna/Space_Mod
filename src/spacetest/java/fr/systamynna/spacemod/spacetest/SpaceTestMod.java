package fr.systamynna.spacemod.spacetest;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(SpaceTestMod.MODID)
public class SpaceTestMod {
    public static final String MODID = "spacemod_test";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SpaceTestMod() {
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) { A3Client.register(); A8Client.register(); A10Client.register(); }
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> SpaceTestCommands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> { A1Runner.tick(e.getServer()); A2Runner.tick(e.getServer()); A4Runner.tickEnd(e.getServer()); A6Runner.tick(); A5Runner.tickEnd(); A3Server.tick(); A7Server.tick(e.getServer()); A9Runner.tick(); A8Server.tick(); CRunner.tick(); C5Runner.tick(); DimLifecycle.tick(e.getServer()); });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre e) -> { A4Runner.tickStart(); A5Runner.tickStart(); });
        A7Server.register(NeoForge.EVENT_BUS);
        DimLifecycle.register();
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent e) -> { A2Runner.registerPhysics(); A4Runner.registerPhysics(); A6Runner.registerPhysics(); A5Runner.registerPhysics(); A3Server.registerPhysics(); A7Server.registerPhysics(); A9Runner.registerPhysics(); A8Server.registerPhysics(); CRunner.registerPhysics(); C5Runner.registerPhysics(); });
        // Lancement sans console : ./gradlew runServerDevMods -Pa1=<cycles> démarre A1 puis arrête le serveur.
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> {
            DimLifecycle.onServerStarted(e.getServer());
            String n = System.getProperty("spacetest.a1", "");
            if (!n.isEmpty()) {
                String err = A1Runner.start(e.getServer(), Integer.parseInt(n));
                if (err != null) LOGGER.error("A1 : {}", err);
                A1Runner.haltWhenDone = true;
            }
            String a2 = System.getProperty("spacetest.a2", "");
            if (!System.getProperty("spacetest.c", "").isEmpty()) CRunner.start(e.getServer(), System.getProperty("spacetest.c"));
            if (!System.getProperty("spacetest.c5", "").isEmpty()) C5Runner.start(e.getServer(), System.getProperty("spacetest.c5"), System.getProperty("spacetest.c5.action", ""));
            if (!System.getProperty("spacetest.a6", "").isEmpty()) A6Runner.start(e.getServer());
            if (!System.getProperty("spacetest.a9", "").isEmpty()) A9Runner.start(e.getServer());
            if (!System.getProperty("spacetest.a5", "").isEmpty()) A5Runner.start(e.getServer());
            if (!System.getProperty("spacetest.a4", "").isEmpty()) A4Runner.start(e.getServer());
            if (!a2.isEmpty()) A2Runner.start(e.getServer(), a2);
        });
        LOGGER.info("spacemod_test loaded (commands: /spacetest)");
    }
}
