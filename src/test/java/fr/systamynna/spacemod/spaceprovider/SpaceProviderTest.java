package fr.systamynna.spacemod.spaceprovider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStreamReader;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import fr.systamynna.spacemod.strategic.SpaceObject;
import fr.systamynna.spacemod.strategic.StrategicLayer;
import fr.systamynna.spacemod.strategic.component.ComponentTypes;
import fr.systamynna.spacemod.strategic.component.HostedDimension;
import fr.systamynna.spacemod.strategic.component.Transform;
import fr.systamynna.spacemod.strategic.store.JdbcStrategicStore;
import net.minecraft.resources.ResourceLocation;

/** JSON ⇄ objets, import initial, mise à jour sans toucher à la dimension, aucune suppression implicite. */
class SpaceProviderTest {
    private static List<ProviderEntry> bundled(String file) {
        JsonObject root = JsonParser.parseReader(new InputStreamReader(
                SpaceProviderTest.class.getResourceAsStream("/data/amynnaspacemod/space/" + file))).getAsJsonObject();
        return root.getAsJsonArray("entries").asList().stream().map(JsonElement::getAsJsonObject).map(SpaceObjectJson::fromJson).toList();
    }

    /** Importe directement des entrées, comme {@link SpaceImporter} le fait pour une source. */
    private static void apply(StrategicLayer layer, List<ProviderEntry> entries) {
        for (ProviderEntry e : entries) {
            SpaceObject existing = layer.find(e.uuid());
            if (existing != null) SpaceObjectJson.update(existing, e);
            else layer.addObject(e.parent() == null ? null : ProviderEntry.uuid(e.parent()), SpaceObjectJson.toObject(e));
        }
    }

    @Test
    void jsonRoundTrip() {
        for (ProviderEntry e : bundled("sol.json")) assertEquals(e, SpaceObjectJson.fromJson(SpaceObjectJson.toJson(e)));
    }

    @Test
    void importBuildsTreeWithStableIds() {
        StrategicLayer layer = new StrategicLayer(JdbcStrategicStore.inMemory());
        apply(layer, bundled("sol.json"));
        apply(layer, bundled("neighbors.json"));
        SpaceObject moon = layer.find(ProviderEntry.uuid("sol/moon"));
        assertNotNull(moon);
        assertEquals(ProviderEntry.uuid("sol/earth"), moon.parent());
    }

    @Test
    void reimportUpdatesGeneralDataButKeepsDimension() {
        StrategicLayer layer = new StrategicLayer(JdbcStrategicStore.inMemory());
        List<ProviderEntry> entries = bundled("sol.json");
        apply(layer, entries);
        SpaceObject earth = layer.find(ProviderEntry.uuid("sol/earth"));
        earth.put(new HostedDimension(ResourceLocation.parse("amynnaspacemod:terra")));
        ProviderEntry old = entries.stream().filter(e -> e.id().equals("sol/earth")).findFirst().orElseThrow();
        ProviderEntry moved = new ProviderEntry(old.id(), old.parent(), old.archetype(), old.name(), Transform.at(1, 2, 3),
                old.rail(), old.render(), null, null);
        apply(layer, List.of(moved));
        assertEquals(2, earth.transform().ty());
        assertNotNull(earth.get(ComponentTypes.HOSTED_DIMENSION));
        assertNull(layer.find(ProviderEntry.uuid("inconnu")));
    }
}
