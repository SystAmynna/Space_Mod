package fr.systamynna.spacemod.spaceprovider;

import java.util.ArrayList;
import java.util.List;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.strategic.SpaceConfig;
import fr.systamynna.spacemod.strategic.StrategicLayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Import des sources au démarrage du monde et enregistrement des commandes. */
public final class SpaceProviderEvents {
    /** Source des ressources du mod. */
    static final SpaceProvider RESOURCES = new JsonSpaceProvider("resources", AmynnaSpaceMod.MODID::equals);
    /** Source des datapacks (vide par défaut). */
    static final SpaceProvider DATAPACK = new JsonSpaceProvider("datapack", ns -> !AmynnaSpaceMod.MODID.equals(ns));

    /** Classe utilitaire : gestionnaires statiques. */
    private SpaceProviderEvents() {}

    /** Sources actives selon la configuration. */
    static List<SpaceProvider> providers() {
        List<SpaceProvider> list = new ArrayList<>(List.of(RESOURCES, DATAPACK));
        if (SpaceConfig.WEB_PROVIDER.get()) list.add(new WebSpaceProvider());
        return list;
    }

    /** Importe chaque source dont la version dépasse celle déjà en base (toutes, au premier démarrage du monde). */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        StrategicLayer layer = StrategicLayer.get();
        for (SpaceProvider provider : providers()) {
            SpaceImporter.run(layer, provider, event.getServer().getResourceManager(), false);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        SpaceCommands.register(event.getDispatcher());
    }
}
