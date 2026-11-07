package fr.systamynna.spacemod.core.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.List;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.StrategicLayer;
import fr.systamynna.spacemod.core.component.components.Identity;
import fr.systamynna.spacemod.core.component.components.MajorObject;
import fr.systamynna.spacemod.core.controller.controllers.OrbitalController;
import fr.systamynna.spacemod.core.render.GalaxyRender;
import fr.systamynna.spacemod.core.render.PlanetRender;
import fr.systamynna.spacemod.core.render.SpaceRender;
import fr.systamynna.spacemod.core.store.JdbcSpaceStore;

class ProvidersTest {
    private static final String EARTH = "universe/milky_way/solar_system/sun/earth";

    private static StrategicLayer layer() throws Exception {
        StrategicLayer s = new StrategicLayer();
        s.start(new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:")));
        return s;
    }

    @Test
    void providerDefaultsToNoneAndPersists() throws Exception {
        StrategicLayer s = layer();
        assertEquals(ProviderType.NONE, s.getProvider());
        s.setProvider(ProviderType.DEFAULT);
        assertEquals(ProviderType.DEFAULT, s.getProvider());
    }

    @Test
    void defaultRefreshCreatesThenIsIdempotentThenRepairs() throws Exception {
        StrategicLayer s = layer();
        var first = Providers.refresh(s, ProviderType.DEFAULT);
        assertTrue(first.errors().isEmpty(), first.errors().toString());
        assertEquals(List.of("universe"), first.updated()); // le root existait déjà
        assertTrue(first.created().size() == 77);
        assertNotNull(s.tree().resolve(EARTH + "/moon").getController());
        assertNotNull(s.tree().resolve("universe/milky_way/trappist_1/trappist_1/trappist_1_h").getController());
        assertNotNull(s.tree().resolve("universe/andromeda"));

        var second = Providers.refresh(s, ProviderType.DEFAULT);
        assertEquals(0, second.created().size() + second.updated().size(), second.updated().toString());
        assertEquals(first.created().size() + 1, second.unchanged());

        s.tree().resolve(EARTH).getComponent(Identity.class).orElseThrow().properName = "Terra";
        var third = Providers.refresh(s, ProviderType.DEFAULT);
        assertEquals(List.of(EARTH), third.updated());
        assertEquals("Earth", s.tree().resolve(EARTH).getComponent(Identity.class).orElseThrow().properName);
    }

    @Test
    void everyOrbitIsPhysicallyValid() throws Exception {
        StrategicLayer s = layer();
        Providers.refresh(s, ProviderType.DEFAULT);
        int orbits = 0;
        for (SpaceObject o : s.tree().all()) {
            if (!(o.getController() instanceof OrbitalController c)) continue;
            orbits++;
            assertTrue(c.a >= 0 && c.e >= 0 && c.e < 1 && c.period > 0, s.tree().addressOf(o));
            assertTrue(o.getScale() > 0);
        }
        assertEquals(60, orbits);
    }

    @Test
    void scaleIsTheKilometreValueOfTheTransformUnit() throws Exception {
        StrategicLayer s = layer();
        Providers.refresh(s, ProviderType.DEFAULT);
        String system = "universe/milky_way/solar_system";
        assertEquals(9460730472580.8, s.tree().resolve(system).getScale(), 1e-3); // al
        assertEquals(149597870.7, s.tree().resolve(system + "/sun/earth").getScale(), 1e-6); // UA
        assertEquals(1.0, s.tree().resolve(EARTH + "/moon").getScale(), 0); // km
        var sun = (PlanetRender) s.tree().resolve(system + "/sun").getComponent(SpaceRender.class).orElseThrow().objects.get(0);
        assertEquals(696340, sun.radius(), 1); // rayons en km
    }

    @Test
    void defaultRenderDataIsValidAndTexturesExist() throws Exception {
        StrategicLayer s = layer();
        Providers.refresh(s, ProviderType.DEFAULT);
        assertEquals(6371, ((PlanetRender) s.tree().resolve(EARTH).getComponent(SpaceRender.class).orElseThrow().objects.get(0)).radius(), 0);
        int withRender = 0;
        for (SpaceObject o : s.tree().all()) {
            var r = o.getComponent(SpaceRender.class);
            if (r.isEmpty()) continue;
            withRender++;
            assertEquals(1, r.get().objects.size(), s.tree().addressOf(o)); // un rayon invalide fait ignorer l'objet au chargement
            if (r.get().objects.get(0) instanceof GalaxyRender g) { // les galaxies n'ont pas de texture
                assertTrue(g.radius() > 0, s.tree().addressOf(o));
                continue;
            }
            PlanetRender p = (PlanetRender) r.get().objects.get(0);
            assertTrue(p.radius() > 0, s.tree().addressOf(o));
            String path = "/assets/" + p.texture.replaceFirst(":", "/"); // namespace:chemin -> /assets/namespace/chemin
            assertNotNull(Providers.class.getResource(path), p.texture);
        }
        assertEquals(67, withRender); // 65 planètes/lunes/Soleil + 2 galaxies
    }

    @Test
    void everySystemIsAMajorObjectUnderTheGalaxy() throws Exception {
        StrategicLayer s = layer();
        Providers.refresh(s, ProviderType.DEFAULT);
        SpaceObject gal = s.tree().resolve("universe/milky_way");
        int n = 0;
        for (SpaceObject o : s.tree().children(gal)) { // les systèmes (niveau année-lumière)
            MajorObject k = o.getComponent(MajorObject.class).orElseThrow(() -> new AssertionError("pas de major_object : " + o.getName()));
            assertTrue(k.luminosity > 0 && k.color != 0, o.getName());
            n++;
        }
        assertEquals(10, n);
    }

    @Test
    void noneDoesNothingAndWebIsNotImplemented() throws Exception {
        StrategicLayer s = layer();
        assertTrue(Providers.refresh(s, ProviderType.NONE).errors().isEmpty());
        assertEquals(1, Providers.refresh(s, ProviderType.WEB).errors().size());
        assertEquals(0, s.tree().children(s.tree().resolve("universe")).size());
    }
}
