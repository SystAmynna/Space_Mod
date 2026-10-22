package fr.systamynna.spacemod.strategic.command;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import fr.systamynna.spacemod.spaceprovider.ProviderEntry;
import fr.systamynna.spacemod.strategic.DimensionManager;
import fr.systamynna.spacemod.strategic.SpaceObject;
import fr.systamynna.spacemod.strategic.StrategicLayer;
import fr.systamynna.spacemod.strategic.component.Identity;
import fr.systamynna.spacemod.strategic.component.ComponentTypes;
import fr.systamynna.spacemod.strategic.component.HostedDimension;
import fr.systamynna.spacemod.strategic.component.Transform;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /spaceobject <id> create|render|dimension|enter}, {@code /spaceobject list}, réservé aux opérateurs. L'id est l'identifiant texte de l'objet ({@code Identity.id}, par exemple {@code milky_way/sol/earth}). */
public final class SpaceObjectCommands {
    /** Archétype des objets créés à la main. */
    private static final String ARCHETYPE = "object";

    /** Classe utilitaire. */
    private SpaceObjectCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spaceobject").requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(c -> list(c.getSource())))
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(ids(), b))
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(c -> create(c.getSource(), StringArgumentType.getString(c, "id"),
                                                StringArgumentType.getString(c, "name")))))
                        .then(Commands.literal("render")
                                .then(Commands.argument("sky", ResourceLocationArgument.id())
                                        .executes(c -> render(c.getSource(), StringArgumentType.getString(c, "id"),
                                                ResourceLocationArgument.getId(c, "sky")))))
                        .then(Commands.literal("dimension")
                                .executes(c -> dimension(c.getSource(), StringArgumentType.getString(c, "id"))))
                        .then(Commands.literal("enter")
                                .executes(c -> enter(c.getSource(), StringArgumentType.getString(c, "id"), null))
                                .then(Commands.argument("port", StringArgumentType.string())
                                        .executes(c -> enter(c.getSource(), StringArgumentType.getString(c, "id"),
                                                StringArgumentType.getString(c, "port")))))));
    }

    /** Objets de l'arbre, parents avant enfants, avec leur profondeur. Charge les systèmes non chargés. */
    private static void walk(SpaceObject object, int depth, List<Object[]> out) {
        out.add(new Object[] {object, depth});
        StrategicLayer layer = StrategicLayer.get();
        for (UUID child : object.children()) {
            SpaceObject c = layer.find(child);
            if (c != null) walk(c, depth + 1, out);
        }
    }

    private static List<Object[]> tree() {
        List<Object[]> out = new ArrayList<>();
        for (SpaceObject galaxy : StrategicLayer.get().galaxies()) walk(galaxy, 0, out);
        return out;
    }

    /** Ids texte connus, pour la complétion : entre guillemets, Brigadier refusant le « / » d'une chaîne nue. */
    private static List<String> ids() {
        return tree().stream().map(e -> ((SpaceObject) e[0]).get(ComponentTypes.IDENTITY).id()).filter(id -> !id.isEmpty())
                .map(id -> '"' + id + '"').toList();
    }

    private static int list(CommandSourceStack source) {
        List<Object[]> entries = tree();
        for (Object[] e : entries) {
            SpaceObject o = (SpaceObject) e[0];
            Identity identity = o.get(ComponentTypes.IDENTITY);
            HostedDimension dim = o.get(ComponentTypes.HOSTED_DIMENSION);
            // id vide : objet antérieur à l'id texte, un /spaceprovider reload le remplit (sauf objet créé à la main)
            String line = "  ".repeat((Integer) e[1]) + (identity.id().isEmpty() ? "?" : identity.id()) + " — " + identity.name() + " ["
                    + identity.archetype() + "]" + (dim == null ? "" : dim.dimension() == null ? " → dimension non créée" : " → " + dim.dimension());
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return entries.size();
    }

    /** Objet d'identifiant texte donné, ou {@code null}. */
    private static SpaceObject byId(String id) {
        return tree().stream().map(e -> (SpaceObject) e[0]).filter(o -> o.get(ComponentTypes.IDENTITY).id().equals(id))
                .findFirst().orElse(null);
    }

    /** Crée l'objet sous le parent désigné par le début de son id ({@code a/b/c} → parent {@code a/b}). */
    private static int create(CommandSourceStack source, String id, String name) {
        StrategicLayer layer = StrategicLayer.get();
        int cut = id.lastIndexOf('/');
        SpaceObject parent = cut < 0 ? null : byId(id.substring(0, cut));
        if (parent == null) return fail(source, "Parent inconnu : " + (cut < 0 ? "(id sans parent)" : id.substring(0, cut)));
        if (byId(id) != null) return fail(source, "Objet déjà présent : " + id);
        Identity identity = Identity.child(parent.get(ComponentTypes.IDENTITY), id.substring(cut + 1), name, ARCHETYPE);
        layer.addObject(parent.id(), new SpaceObject(ProviderEntry.uuid(id)).put(identity).put(Transform.ORIGIN));
        source.sendSuccess(() -> Component.literal(id + " créé"), true);
        return 1;
    }

    /** Déclare que l'objet héberge une dimension (créée au premier {@code enter}), aux paramètres de l'espace. */
    private static int dimension(CommandSourceStack source, String id) {
        SpaceObject object = byId(id);
        if (object == null) return fail(source, "Objet inconnu : " + id);
        if (object.has(ComponentTypes.HOSTED_DIMENSION)) return fail(source, id + " héberge déjà une dimension");
        object.put(new HostedDimension(null, HostedDimension.Properties.SPACE));
        source.sendSuccess(() -> Component.literal(id + " héberge désormais une dimension (créée au premier enter)"), true);
        return 1;
    }

    private static int render(CommandSourceStack source, String id, ResourceLocation sky) {
        SpaceObject object = byId(id);
        if (object == null) return fail(source, "Objet inconnu : " + id);
        HostedDimension hosted = object.get(ComponentTypes.HOSTED_DIMENSION);
        if (hosted == null) return fail(source, id + " n'héberge pas de dimension");
        object.put(hosted.withRender(sky));
        source.sendSuccess(() -> Component.literal(id + " : ciel " + sky), true);
        return 1;
    }

    /** Téléporte le joueur dans la dimension de l'objet, créée au premier accès. */
    private static int enter(CommandSourceStack source, String id, String port) {
        SpaceObject object = byId(id);
        if (object == null) return fail(source, "Objet inconnu : " + id);
        try {
            DimensionManager.enter(source.getPlayerOrException(), object, port);
        } catch (CommandSyntaxException | IllegalArgumentException | IllegalStateException e) {
            return fail(source, e.getMessage());
        }
        source.sendSuccess(() -> Component.literal("Entrée dans " + id), true);
        return 1;
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }
}
