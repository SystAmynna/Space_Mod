package fr.systamynna.spacemod.spaceprovider;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import fr.systamynna.spacemod.strategic.SpaceObject;
import fr.systamynna.spacemod.strategic.StrategicLayer;
import fr.systamynna.spacemod.strategic.component.Destroyable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** {@code /spaceprovider reload|remove|list}, réservé aux opérateurs. */
final class SpaceCommands {
    /** Classe utilitaire. */
    private SpaceCommands() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spaceprovider").requires(s -> s.hasPermission(2))
                .then(Commands.literal("reload")
                        .executes(c -> reload(c.getSource(), "all"))
                        .then(Commands.argument("source", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        new String[] {"all", "resources", "datapack", "web"}, b))
                                .executes(c -> reload(c.getSource(), StringArgumentType.getString(c, "source")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(c -> remove(c.getSource(), StringArgumentType.getString(c, "id")))))
                .then(Commands.literal("list").executes(c -> list(c.getSource()))));
    }

    /** Ré-importe de force : crée les absents et remet à jour les données générales des existants. */
    private static int reload(CommandSourceStack source, String which) {
        StrategicLayer layer = StrategicLayer.get();
        int total = 0;
        for (SpaceProvider provider : SpaceProviderEvents.providers()) {
            if (!which.equals("all") && !which.equals(provider.name())) continue;
            SpaceImporter.Report r = SpaceImporter.run(layer, provider, source.getServer().getResourceManager(), true);
            total += r.created() + r.updated();
            source.sendSuccess(() -> Component.literal(provider.name() + " : " + r.created() + " créé(s), " + r.updated()
                    + " mis à jour, " + r.skipped() + " ignoré(s)"), true);
        }
        return total;
    }

    /**
     * Suppression explicite d'un objet par son identifiant JSON. La couche ne sait pas encore effacer un objet : il est
     * marqué détruit ({@link Destroyable}) et ne revient pas à un reload.
     */
    private static int remove(CommandSourceStack source, String id) {
        SpaceObject object = StrategicLayer.get().find(ProviderEntry.uuid(id));
        if (object == null) {
            source.sendFailure(Component.literal("Objet inconnu : " + id));
            return 0;
        }
        object.put(new Destroyable(true));
        source.sendSuccess(() -> Component.literal(id + " marqué détruit"), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        StrategicLayer layer = StrategicLayer.get();
        for (SpaceProvider provider : SpaceProviderEvents.providers()) {
            String version = layer.meta(SpaceImporter.versionKey(provider));
            int entries = provider.load(source.getServer().getResourceManager()).entries().size();
            source.sendSuccess(() -> Component.literal(provider.name() + " : version importée " + version + ", "
                    + entries + " entrée(s)"), false);
        }
        return 1;
    }
}
