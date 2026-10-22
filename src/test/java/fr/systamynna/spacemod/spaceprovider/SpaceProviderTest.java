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
import fr.systamynna.spacemod.strategic.component.Identity;
import fr.systamynna.spacemod.strategic.component.hostedPropriety.LogicalVolume;
import fr.systamynna.spacemod.strategic.component.hostedPropriety.PortPoint;
import fr.systamynna.spacemod.strategic.component.hostedPropriety.TriggerVolume;
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
            SpaceObject parent = e.parent() == null ? null : layer.find(ProviderEntry.uuid(e.parent()));
            Identity parentIdentity = parent == null ? null : parent.get(ComponentTypes.IDENTITY);
            if (existing != null) SpaceObjectJson.update(existing, e, parentIdentity);
            else layer.addObject(parent == null ? null : parent.id(), SpaceObjectJson.toObject(e, parentIdentity));
        }
    }

    @Test
    void importedHostsGetDefaultSkyAndKeepEditedOne() {
        StrategicLayer layer = new StrategicLayer(JdbcStrategicStore.inMemory());
        List<ProviderEntry> entries = bundled("sol.json");
        apply(layer, entries);
        SpaceObject earth = layer.find(ProviderEntry.uuid("sol/earth"));
        assertEquals(HostedDimension.DEFAULT_RENDER, earth.get(ComponentTypes.HOSTED_DIMENSION).render());
        ResourceLocation custom = ResourceLocation.fromNamespaceAndPath("amynnaspacemod", "custom");
        earth.put(earth.get(ComponentTypes.HOSTED_DIMENSION).withRender(custom));
        apply(layer, entries);
        HostedDimension hosted = earth.get(ComponentTypes.HOSTED_DIMENSION);
        assertEquals(custom, hosted.render());
        assertEquals(custom, HostedDimension.load(hosted.save()).render());
    }

    @Test
    void importedObjectsGetTheirPathAsId() {
        StrategicLayer layer = new StrategicLayer(JdbcStrategicStore.inMemory());
        apply(layer, bundled("sol.json"));
        apply(layer, bundled("neighbors.json"));
        SpaceObject earth = layer.find(ProviderEntry.uuid("sol/earth"));
        assertEquals("milky_way/sol/earth", earth.get(ComponentTypes.IDENTITY).id());
        assertEquals("milky_way/sol/earth", Identity.load(earth.get(ComponentTypes.IDENTITY).save()).id());
        assertEquals("milky_way/sol/earth/moon", layer.find(ProviderEntry.uuid("sol/moon")).get(ComponentTypes.IDENTITY).id());
    }

    @Test
    void onlyPlanetsDeclareAnUncreatedDimension() {
        StrategicLayer layer = new StrategicLayer(JdbcStrategicStore.inMemory());
        apply(layer, bundled("sol.json"));
        HostedDimension earth = layer.find(ProviderEntry.uuid("sol/earth")).get(ComponentTypes.HOSTED_DIMENSION);
        assertNotNull(earth);
        assertNull(earth.dimension());
        assertNull(layer.find(ProviderEntry.uuid("sol/sun")).get(ComponentTypes.HOSTED_DIMENSION));
        assertNull(layer.find(ProviderEntry.uuid("sol")).get(ComponentTypes.HOSTED_DIMENSION));
        // deux objets sans clé ne sont pas en conflit d'index
        assertNull(layer.find(ProviderEntry.uuid("sol/mars")).get(ComponentTypes.HOSTED_DIMENSION).dimension());
    }

    @Test
    void hostedDimensionKeepsPropertiesAndNullKeyThroughNbt() {
        HostedDimension.Properties p = new HostedDimension.Properties(0, -9.8f, 0, 1.0, 0.1f, 1, 0, 0);
        HostedDimension uncreated = new HostedDimension(null, p);
        assertEquals(uncreated, HostedDimension.load(uncreated.save()));
        HostedDimension created = new HostedDimension(ResourceLocation.parse("amynnaspacemod:x"), p, Transform.at(1, 2, 3),
                ResourceLocation.parse("amynnaspacemod:sky"), List.of(new PortPoint("a", 1, 2, 3, 4, 5, 6, 0, 1, 0, 0, 90, 5,
                        new LogicalVolume(10), new TriggerVolume(20))));
        assertEquals(created, HostedDimension.load(created.save()));
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
                old.rail(), old.render(), null, null, null);
        apply(layer, List.of(moved));
        assertEquals(2, earth.transform().ty());
        assertNotNull(earth.get(ComponentTypes.HOSTED_DIMENSION));
        assertNull(layer.find(ProviderEntry.uuid("inconnu")));
    }

    @Test
    void hostedStateTracksLastExit() {
        HostedDimension.State state = new HostedDimension(ResourceLocation.parse("amynnaspacemod:x")).state();
        java.util.UUID a = java.util.UUID.randomUUID(), b = java.util.UUID.randomUUID();
        state.enter(a);
        state.enter(b);
        state.leave(a, 100);
        assertEquals(1, state.playerCount());
        state.leave(b, 250);
        assertEquals(0, state.playerCount());
        assertEquals(250, state.lastExitTick());
    }
}
