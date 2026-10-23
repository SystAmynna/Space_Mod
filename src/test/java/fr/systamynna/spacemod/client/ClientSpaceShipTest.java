package fr.systamynna.spacemod.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.network.ShipPosePacket;
import fr.systamynna.spacemod.network.SystemSnapshotPacket;
import fr.systamynna.spacemod.ship.ShipManager;
import fr.systamynna.spacemod.strategic.Archetypes;
import fr.systamynna.spacemod.strategic.SpaceObject;
import fr.systamynna.spacemod.strategic.StrategicLayer;
import fr.systamynna.spacemod.strategic.component.Render;
import fr.systamynna.spacemod.strategic.component.Transform;
import fr.systamynna.spacemod.strategic.store.JdbcStrategicStore;
import net.minecraft.resources.ResourceLocation;

/** Application des poses de vaisseau sur la réplique client : mise à jour, changement de parent, extrapolation. */
class ClientSpaceShipTest {
    @Test
    void poseUpdatesReparentsAndExtrapolates() {
        ResourceLocation model = ResourceLocation.parse("amynnaspacemod:sphere");
        StrategicLayer layer = new StrategicLayer(JdbcStrategicStore.inMemory());
        try {
            SpaceObject galaxy = layer.addObject(null, Archetypes.galaxy("g", Transform.ORIGIN, null));
            SpaceObject system = layer.addObject(galaxy.id(), Archetypes.system("s", Transform.ORIGIN, null));
            SpaceObject a = layer.addObject(system.id(), Archetypes.star("A", Transform.ORIGIN, null, new Render(model, 5), 10));
            SpaceObject b = layer.addObject(system.id(), Archetypes.star("B", Transform.at(1000, 0, 0), null, new Render(model, 5), 10));
            SpaceObject ship = ShipManager.spawn(layer, "Alpha", a, true, 0);
            // le vaisseau est l'hôte : il n'est donc pas dans les objets à dessiner
            ClientSpace.set(new SystemSnapshotPacket(ship.id(), 0, 0, 1, List.of(galaxy.save(), system.save(), a.save(), b.save(), ship.save())));

            ClientSpace.applyPose(new ShipPosePacket(ship.id(), b.id(), 5, 6, 7, 0, 0, 0, 1, 1, 0, 0, 100));
            SpaceObject onClient = ClientSpace.get().find(ship.id());
            assertEquals(b.id(), onClient.parent());
            assertEquals(5, onClient.transform().tx());

            ClientSpace.get().extrapolate(105);
            assertEquals(10, ClientSpace.get().find(ship.id()).transform().tx(), 1e-9);
            // au-delà de 20 ticks, la position n'est plus extrapolée
            ClientSpace.get().extrapolate(500);
            assertEquals(25, ClientSpace.get().find(ship.id()).transform().tx(), 1e-9);

            // pose d'un vaisseau inconnu : ignorée
            ClientSpace.applyPose(new ShipPosePacket(UUID.randomUUID(), null, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0));
            assertSame(onClient.parent(), ClientSpace.get().find(ship.id()).parent());
            assertTrue(ClientSpace.get().renderables().stream().noneMatch(o -> o.id().equals(ship.id())));
        } finally {
            layer.close();
        }
    }
}
