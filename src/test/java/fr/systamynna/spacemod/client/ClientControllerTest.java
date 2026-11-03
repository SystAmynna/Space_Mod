package fr.systamynna.spacemod.client;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.StrategicLayer;
import fr.systamynna.spacemod.core.controller.controllers.OrbitalController;
import fr.systamynna.spacemod.core.store.JdbcSpaceStore;

class ClientControllerTest {
    @Test
    void clientReplicaMatchesServer() throws Exception {
        StrategicLayer s = new StrategicLayer();
        s.start(new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:")));
        SpaceObject o = s.add("a", s.tree().resolve("universe"));
        OrbitalController oc = new OrbitalController();
        oc.edit("a=5 period=100 spin=40");
        s.setController(o.getUuid(), oc);
        s.tickControllers(33);

        ClientLayer c = new ClientLayer();
        c.apply(o.getUuid(), o.getParent(), o.save());
        c.step(33);
        SpaceObject r = c.get(o.getUuid());
        assertEquals(o.transform.x, r.transform.x, 1e-12);
        assertEquals(o.transform.z, r.transform.z, 1e-12);
        assertEquals(o.transform.yaw, r.transform.yaw, 1e-12);
    }
}
