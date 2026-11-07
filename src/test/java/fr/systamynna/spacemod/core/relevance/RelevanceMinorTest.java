package fr.systamynna.spacemod.core.relevance;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.StrategicLayer;
import fr.systamynna.spacemod.core.component.components.MajorObject;
import fr.systamynna.spacemod.core.component.components.MinorObject;
import fr.systamynna.spacemod.core.store.JdbcSpaceStore;

/** Un objet majeur à charger entraîne tous ses descendants MinorObject, sans test de distance ni de taille. */
class RelevanceMinorTest {
    static final double LY = 9.4607e12;

    StrategicLayer layer;

    private SpaceObject at(String name, SpaceObject parent, double scale, double x) {
        SpaceObject o = layer.add(name, parent);
        layer.setScale(o.getUuid(), scale);
        layer.editTransform(o.getUuid(), t -> t.x = x);
        return o;
    }

    private Set<UUID> need(SpaceObject anchor) {
        return Relevance.needed(layer, new PlayerSpacePos(anchor.getUuid(), 0, 0, 0), 1, Set.of(), Relevance.Params.DEFAULT);
    }

    @Test
    void minorsFollowTheirMajorParent() throws Exception {
        layer = new StrategicLayer();
        layer.start(new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:")));
        SpaceObject gal = layer.add("gal", layer.tree().resolve("universe"));
        SpaceObject sys_a = at("sys_a", gal, LY, 0), sys_b = at("sys_b", gal, LY, 20);
        layer.addComponent(sys_a.getUuid(), new MajorObject());
        layer.addComponent(sys_b.getUuid(), new MajorObject());
        SpaceObject starA = at("star", sys_a, 1, 0);
        // planète à 5e13 km : au-delà de soiMaxKm, donc sa lune n'est atteinte par aucune autre règle
        SpaceObject planetA = at("planet", sys_a, 1, 5e13), moonA = at("moon", planetA, 1, 1e6);
        SpaceObject planetB = at("planet", sys_b, 1, 1e8);
        for (SpaceObject o : new SpaceObject[] {planetA, moonA, planetB}) layer.addComponent(o.getUuid(), new MinorObject());
        layer.unload(layer.tree().resolve("universe").getUuid(), true);

        Set<UUID> n = need(starA);
        assertTrue(n.contains(sys_a.getUuid()));
        assertTrue(n.contains(planetA.getUuid()));
        assertTrue(n.contains(moonA.getUuid()), "la lune est mineure : chargée avec son système");
        assertFalse(n.contains(sys_b.getUuid()), "l'observateur est dans la sphère d'influence de la galaxie, mais un objet majeur ne s'étend pas");
        assertFalse(n.contains(planetB.getUuid()), "système lointain : ses mineurs ne sont pas chargés");

        // sans MinorObject, la lune n'est pas atteinte
        layer.removeComponent(moonA.getUuid(), MinorObject.class);
        assertFalse(need(starA).contains(moonA.getUuid()));
    }

    @Test
    void aMajorObjectLoadsOnlyWhenNear() throws Exception {
        layer = new StrategicLayer();
        layer.start(new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:")));
        SpaceObject gal = layer.add("gal", layer.tree().resolve("universe"));
        SpaceObject home = at("home", gal, LY, 0), near = at("near", gal, 1, 5e9), far = at("far", gal, LY, 20);
        for (SpaceObject o : new SpaceObject[] {home, near, far}) layer.addComponent(o.getUuid(), new MajorObject());
        SpaceObject star = at("star", home, 1, 0);
        layer.unload(layer.tree().resolve("universe").getUuid(), true);
        Set<UUID> n = need(star);
        assertTrue(n.contains(home.getUuid()));
        assertTrue(n.contains(near.getUuid()), "sous rNearKm");
        assertFalse(n.contains(far.getUuid()));
    }
}
