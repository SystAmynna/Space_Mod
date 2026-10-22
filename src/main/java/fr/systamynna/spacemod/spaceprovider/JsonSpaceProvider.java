package fr.systamynna.spacemod.spaceprovider;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;

/**
 * Lit {@code data/<namespace>/space/**.json} dans le gestionnaire de ressources du serveur. Chaque fichier est
 * {@code {"version": N, "entries": [...]}}. Une instance lit les ressources du mod (son namespace), l'autre celles des
 * datapacks (tous les autres namespaces).
 */
public final class JsonSpaceProvider implements SpaceProvider {
    /** Dossier, sous {@code data/<namespace>/}, qui contient les fichiers. */
    public static final String FOLDER = "space";

    private final String name;
    private final Predicate<String> namespaces;

    /** @param namespaces namespaces lus par cette source */
    public JsonSpaceProvider(String name, Predicate<String> namespaces) {
        this.name = name;
        this.namespaces = namespaces;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Data load(ResourceManager resources) {
        int version = 0;
        List<ProviderEntry> entries = new ArrayList<>();
        for (var found : resources.listResources(FOLDER, id -> id.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation file = found.getKey();
            if (!namespaces.test(file.getNamespace())) continue;
            try (BufferedReader reader = found.getValue().openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                version = Math.max(version, GsonHelper.getAsInt(root, "version", 1));
                for (JsonElement element : GsonHelper.getAsJsonArray(root, "entries")) {
                    try {
                        entries.add(SpaceObjectJson.fromJson(element.getAsJsonObject()));
                    } catch (RuntimeException e) {
                        AmynnaSpaceMod.LOGGER.warn("Entrée ignorée dans {} : {}", file, e.getMessage());
                    }
                }
            } catch (Exception e) {
                AmynnaSpaceMod.LOGGER.warn("Fichier ignoré {} : {}", file, e.getMessage());
            }
        }
        return new Data(version, entries);
    }
}
