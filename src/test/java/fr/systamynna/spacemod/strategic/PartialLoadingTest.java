package fr.systamynna.spacemod.strategic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fr.systamynna.spacemod.strategic.component.ComponentTypes;
import fr.systamynna.spacemod.strategic.component.Pilotable;
import fr.systamynna.spacemod.strategic.component.RailFollower;
import fr.systamynna.spacemod.strategic.component.Render;
import fr.systamynna.spacemod.strategic.component.Transform;
import fr.systamynna.spacemod.strategic.store.JdbcStrategicStore;
import fr.systamynna.spacemod.strategic.store.StoredObject;
import net.minecraft.resources.ResourceLocation;

/** Seuls les cadres et le résident sont en mémoire au démarrage ; les corps d'un système le sont à la demande. */
class PartialLoadingTest {
    private static final ResourceLocation MODEL = ResourceLocation.parse("amynnaspacemod:proxy/rock");
    private static final int BODIES_PER_SYSTEM = 10;

    @TempDir
    Path dir;

    private StrategicLayer layer;
    private SpaceObject galaxy;
    private SpaceObject cruiserBetweenStars;
    private SpaceObject systemA;
    private SpaceObject systemB;
    private final List<UUID> bodiesA = new ArrayList<>();
    private final List<UUID> bodiesB = new ArrayList<>();
    private ResourceLocation dimA;
    private ResourceLocation dimB;

    private StrategicLayer reopen() {
        if (layer != null) layer.close();
        layer = new StrategicLayer(JdbcStrategicStore.sqlite(dir.resolve("strategic.db")));
        return layer;
    }

    private static ResourceLocation dim(String name) {
        return ResourceLocation.parse("amynnaspacemod:" + name);
    }

    private List<UUID> fill(StrategicLayer l, SpaceObject system, String prefix) {
        List<UUID> ids = new ArrayList<>();
        SpaceObject star = l.addObject(system.id(), Archetypes.star(prefix + "-etoile", Transform.ORIGIN, null, new Render(MODEL, 1), 10));
        ids.add(star.id());
        for (int i = 1; i < BODIES_PER_SYSTEM; i++) {
            SpaceObject planet = l.addObject(system.id(), Archetypes.planet(prefix + "-p" + i, RailFollower.orbit(1000 + i, 100.0 * i, i, 0),
                    new Render(MODEL, 1), dim(prefix + "_p" + i), 64, List.of()));
            ids.add(planet.id());
        }
        return ids;
    }

    @BeforeEach
    void populate() {
        reopen();
        galaxy = layer.addObject(null, Archetypes.galaxy("Voie lactée", Transform.ORIGIN, null));
        systemA = layer.addObject(galaxy.id(), Archetypes.system("A", Transform.at(100, 0, 0), null));
        systemB = layer.addObject(galaxy.id(), Archetypes.system("B", Transform.at(0, 200, 0), null));
        cruiserBetweenStars = layer.addObject(galaxy.id(), Archetypes.cruiser("Albatros", Transform.at(50, 50, 0), dim("albatros"), 32));
        bodiesA.addAll(fill(layer, systemA, "a"));
        bodiesB.addAll(fill(layer, systemB, "b"));
        dimA = dim("a_p1");
        dimB = dim("b_p1");
        assertEquals(4 + 2 * BODIES_PER_SYSTEM, layer.save(), "galaxie, 2 systèmes, vaisseau, 2 × 10 corps");
        reopen();
    }

    @AfterEach
    void close() {
        layer.close();
    }

    @Test
    void startupLoadsOnlyFramesAndTheResident() {
        // galaxie + 2 systèmes + vaisseau interstellaire
        assertEquals(4, layer.loadedObjectCount());
        assertEquals(0, layer.loadedUnitCount());
        assertTrue(layer.isLoaded(cruiserBetweenStars.id()));
        assertFalse(layer.isLoaded(bodiesA.get(0)));
        assertTrue(layer.hostedDimensions().containsAll(Set.of(dimA, dimB, dim("albatros"))), "l'index des dimensions est complet sans charger");
        assertEquals(0, layer.loadedUnitCount());
    }

    @Test
    void findLoadsTheWholeUnitButOnlyThatUnit() {
        SpaceObject body = layer.find(bodiesA.get(3));
        assertNotNull(body);
        assertEquals(1, layer.loadedUnitCount());
        assertEquals(4 + BODIES_PER_SYSTEM, layer.loadedObjectCount());
        assertTrue(layer.isLoaded(bodiesA.get(0)));
        assertFalse(layer.isLoaded(bodiesB.get(0)));
        assertSame(body, layer.find(bodiesA.get(3)), "pas de rechargement ni de doublon");
        assertNull(layer.find(UUID.randomUUID()));
    }

    @Test
    void aSystemListsAllItsChildrenEvenWhileTheyAreUnloaded() {
        SpaceObject system = layer.find(systemA.id());
        assertEquals(Set.copyOf(bodiesA), system.children());
        assertEquals(0, layer.loadedUnitCount(), "lire les enfants d'un système ne charge rien");
        assertFalse(layer.isLoaded(bodiesA.get(0)));
        layer.find(bodiesA.get(0));
        assertEquals(Set.copyOf(bodiesA), system.children());
        layer.tick(10_000);
        assertEquals(1, layer.unloadIdleUnits(1_000, Set.of()));
        assertEquals(Set.copyOf(bodiesA), system.children(), "la liste survit au déchargement des corps");
    }

    @Test
    void hostOfLoadsButUnitOfDimensionDoesNot() {
        assertEquals(systemB.id(), layer.unitOfDimension(dimB));
        assertNull(layer.unitOfDimension(dim("albatros")), "hôte résident : pas d'unité");
        assertNull(layer.unitOfDimension(dim("inconnue")));
        assertEquals(0, layer.loadedUnitCount());
        assertEquals(dimB, layer.hostOf(dimB).get(ComponentTypes.HOSTED_DIMENSION).dimension());
        assertEquals(1, layer.loadedUnitCount());
    }

    @Test
    void idleUnitsAreUnloadedButOccupiedOnesAreNot() {
        layer.tick(1_000);
        layer.find(bodiesA.get(0));
        layer.find(bodiesB.get(0));
        layer.tick(1_500);
        assertEquals(0, layer.unloadIdleUnits(1_000, Set.of()), "pas encore assez longtemps");
        layer.tick(2_100);
        assertEquals(1, layer.unloadIdleUnits(1_000, Set.of(systemA.id())), "A est occupé, B non");
        assertTrue(layer.isLoaded(bodiesA.get(0)));
        assertFalse(layer.isLoaded(bodiesB.get(0)));
        assertEquals(1, layer.loadedUnitCount());
        layer.tick(9_000);
        assertEquals(1, layer.unloadIdleUnits(1_000, Set.of()));
        assertEquals(4, layer.loadedObjectCount());
    }

    @Test
    void accessKeepsAUnitAlive() {
        layer.tick(0);
        layer.find(bodiesA.get(0));
        layer.tick(900);
        layer.find(bodiesA.get(1));
        layer.tick(1_500);
        assertEquals(0, layer.unloadIdleUnits(1_000, Set.of()), "dernier accès à 900 : 600 ticks seulement");
    }

    @Test
    void modificationsAreWrittenBeforeAUnitIsUnloaded() {
        layer.find(bodiesA.get(2)).put(Transform.at(7, 8, 9));
        layer.tick(5_000);
        assertEquals(1, layer.unloadIdleUnits(1_000, Set.of()));
        assertFalse(layer.isLoaded(bodiesA.get(2)));
        assertEquals(7.0, layer.find(bodiesA.get(2)).get(ComponentTypes.TRANSFORM).tx(), "relu depuis la base, modification comprise");
        assertEquals(0, layer.save(), "tout était déjà écrit au déchargement");
    }

    @Test
    void addingABodyToAnUnloadedUnitLoadsItFirst() {
        SpaceObject station = layer.addObject(systemA.id(), Archetypes.station("Relais", RailFollower.orbit(100, 1, 0, 0), dim("relais"), 16));
        assertEquals(1, layer.loadedUnitCount());
        assertEquals(4 + BODIES_PER_SYSTEM + 1, layer.loadedObjectCount(), "toute l'unité, pas seulement le nouveau corps");
        assertEquals(BODIES_PER_SYSTEM + 1, layer.find(systemA.id()).children().size());
        assertEquals(systemA.id(), layer.unitOfDimension(dim("relais")));
        layer.save();
        assertEquals(Set.copyOf(bodiesA).size() + 1, reopen().find(systemA.id()).children().size());
        assertEquals(station.id(), layer.hostOf(dim("relais")).id());
    }

    @Test
    void reparentingABodyBetweenSystemsMovesItsUnit() {
        UUID moving = bodiesA.get(1);
        layer.reparent(moving, systemB.id(), 0, Pilotable.AT_REST);
        assertEquals(systemB.id(), layer.unitOfDimension(dimA), "dimension de la planète déplacée");
        layer.save();

        reopen();
        assertEquals(systemB.id(), layer.unitOfDimension(dimA));
        assertEquals(BODIES_PER_SYSTEM - 1, layer.find(systemA.id()).children().size());
        assertEquals(BODIES_PER_SYSTEM + 1, layer.find(systemB.id()).children().size());
        layer.find(bodiesB.get(0));
        assertEquals(systemB.id(), layer.find(moving).parent());
    }

    @Test
    void aBodyThatLeavesAllSystemsBecomesResident() {
        UUID moving = bodiesA.get(2);
        layer.reparent(moving, galaxy.id(), 0, Pilotable.AT_REST);
        layer.save();
        reopen();
        assertTrue(layer.isLoaded(moving), "résident : chargé dès le démarrage");
        assertEquals(5, layer.loadedObjectCount());
    }

    @Test
    void framesStayResidentAndCannotLiveBelowASystemOrABody() {
        SpaceObject nested = Archetypes.system("Imbriqué", Transform.ORIGIN, null);
        assertThrows(IllegalArgumentException.class, () -> layer.addObject(systemA.id(), nested), "cadre sous un système");
        SpaceObject underBody = Archetypes.galaxy("Sous un corps", Transform.ORIGIN, null);
        assertThrows(IllegalArgumentException.class, () -> layer.addObject(bodiesA.get(0), underBody), "cadre sous un corps");
        assertThrows(IllegalArgumentException.class, () -> layer.reparent(systemB.id(), systemA.id(), 0), "système sous un système");
        assertThrows(IllegalArgumentException.class, () -> layer.reparent(systemB.id(), cruiserBetweenStars.id(), 0), "système sous un corps");
        layer.reparent(systemB.id(), null, 0);
        assertEquals(2, layer.galaxies().size(), "un système peut devenir racine");
    }

    @Test
    void railMathLoadsWhatItNeedsOnDemand() {
        double[] relative = RailMath.relativePosition(layer, layer.find(bodiesA.get(1)), layer.find(bodiesB.get(1)), 123.5);
        assertEquals(2, layer.loadedUnitCount());
        assertTrue(Double.isFinite(relative[0]) && Double.isFinite(relative[1]));
    }

    @Test
    void inconsistentLinksFailLoudlyAtStartup() {
        layer.close();
        Path db = dir.resolve("incoherent.db");
        SpaceObject g = new SpaceObject(UUID.randomUUID());
        SpaceObject s = new SpaceObject(UUID.randomUUID());
        s.setParent(g.id()); // le parent ne liste pas son enfant
        try (JdbcStrategicStore store = JdbcStrategicStore.sqlite(db)) {
            store.save(List.of(new StoredObject(g, null), new StoredObject(s, null)), null);
        }
        assertThrows(IllegalStateException.class, () -> new StrategicLayer(JdbcStrategicStore.sqlite(db)));

        // l'inverse : le parent liste un enfant qui a un autre parent
        Path db2 = dir.resolve("incoherent2.db");
        SpaceObject g2 = new SpaceObject(UUID.randomUUID());
        SpaceObject other = new SpaceObject(UUID.randomUUID());
        SpaceObject s2 = new SpaceObject(UUID.randomUUID());
        s2.setParent(other.id());
        other.addChild(s2.id());
        g2.addChild(s2.id());
        try (JdbcStrategicStore store = JdbcStrategicStore.sqlite(db2)) {
            store.save(List.of(new StoredObject(g2, null), new StoredObject(other, null), new StoredObject(s2, null)), null);
        }
        assertThrows(IllegalStateException.class, () -> new StrategicLayer(JdbcStrategicStore.sqlite(db2)));
    }

    @Test
    void inconsistentLinksFailLoudlyWhenAUnitIsLoaded() {
        layer.close();
        Path db = dir.resolve("incoherent3.db");
        SpaceObject g = new SpaceObject(UUID.randomUUID()).put(new fr.systamynna.spacemod.strategic.component.Frame());
        SpaceObject sys = new SpaceObject(UUID.randomUUID()).put(new fr.systamynna.spacemod.strategic.component.Frame())
                .put(new fr.systamynna.spacemod.strategic.component.StellarSystem(null));
        SpaceObject body = new SpaceObject(UUID.randomUUID());
        sys.setParent(g.id());
        g.addChild(sys.id());
        body.setParent(sys.id()); // le système ne liste pas ce corps
        try (JdbcStrategicStore store = JdbcStrategicStore.sqlite(db)) {
            store.save(List.of(new StoredObject(g, null), new StoredObject(sys, null), new StoredObject(body, sys.id())), null);
        }
        StrategicLayer l = new StrategicLayer(JdbcStrategicStore.sqlite(db));
        assertThrows(IllegalStateException.class, () -> l.find(body.id()));
        assertEquals(0, l.loadedUnitCount(), "rien n'est à moitié chargé");
        l.close();
    }

    /** Mesure, sans seuil imposé : 50 000 objets, démarrage résident et lecture d'une unité. */
    @Test
    void volumeStartupStaysSmallWhateverTheBodyCount() {
        layer.close();
        layer = new StrategicLayer(JdbcStrategicStore.sqlite(dir.resolve("volume.db")));
        SpaceObject bigGalaxy = layer.addObject(null, Archetypes.galaxy("Grande", Transform.ORIGIN, null));
        int systems = 1_000;
        int bodies = 50;
        long t0 = System.nanoTime();
        for (int s = 0; s < systems; s++) {
            SpaceObject system = layer.addObject(bigGalaxy.id(), Archetypes.system("S" + s, Transform.at(s * 10.0, 0, 0), null));
            for (int b = 0; b < bodies; b++) {
                layer.addObject(system.id(), Archetypes.star("c" + b, Transform.at(b, 0, 0), null, new Render(MODEL, 1), 1));
            }
            layer.save();
            layer.tick(s * 1000L);
            layer.unloadIdleUnits(0, Set.of());
        }
        long t1 = System.nanoTime();
        assertEquals(1 + systems, layer.loadedObjectCount(), "après déchargement, seuls les cadres restent");

        layer.close();
        long t2 = System.nanoTime();
        layer = new StrategicLayer(JdbcStrategicStore.sqlite(dir.resolve("volume.db")));
        long t3 = System.nanoTime();
        assertEquals(1 + systems, layer.loadedObjectCount());
        UUID anySystem = layer.galaxies().get(0).id();
        UUID oneSystem = layer.find(anySystem).children().iterator().next();
        UUID oneBody = layer.find(oneSystem).children().iterator().next();
        long t4 = System.nanoTime();
        assertNotNull(layer.find(oneBody));
        long t5 = System.nanoTime();
        assertEquals(1 + systems + bodies, layer.loadedObjectCount());
        System.out.printf("VOLUME %d objets : insertion %d ms, démarrage %d ms (%d résidents), lecture d'une unité de %d corps %d ms%n",
                systems * (bodies + 1) + 1, (t1 - t0) / 1_000_000, (t3 - t2) / 1_000_000, 1 + systems, bodies, (t5 - t4) / 1_000_000);
    }
}
