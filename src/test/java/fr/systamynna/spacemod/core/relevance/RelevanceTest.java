package fr.systamynna.spacemod.core.relevance;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.StrategicLayer;
import fr.systamynna.spacemod.core.relevance.Relevance.Params;
import fr.systamynna.spacemod.core.render.PlanetRender;
import fr.systamynna.spacemod.core.render.RenderTransform;
import fr.systamynna.spacemod.core.render.SpaceRender;
import fr.systamynna.spacemod.core.store.JdbcSpaceStore;

/** Arbre synthétique : univers → galaxie → 1000 systèmes → étoile → planètes (planètes seulement pour sys0 et sys1). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RelevanceTest {
    static final double LY = 9.4607e12;
    static final Params P = Params.DEFAULT;

    StrategicLayer layer;
    SpaceObject uni, gal, star0, star1, p1, p2, p3, q1, q2, q3, giant, dwarf, probe;

    private SpaceObject at(String name, SpaceObject parent, double scale, double x, double y, double z) {
        SpaceObject o = layer.add(name, parent);
        layer.setScale(o.getUuid(), scale);
        layer.editTransform(o.getUuid(), t -> { t.x = x; t.y = y; t.z = z; });
        return o;
    }

    private void radius(SpaceObject o, double km) {
        layer.addComponent(o.getUuid(), new SpaceRender());
        layer.editComponent(o.getUuid(), SpaceRender.class, c -> ((SpaceRender) c).objects.add(new PlanetRender(new RenderTransform(), "t", km)));
    }

    @BeforeAll
    void build() throws Exception {
        layer = new StrategicLayer();
        layer.start(new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:")));
        uni = layer.tree().resolve("universe");
        gal = layer.add("milky_way", uni);
        Random r = new Random(42);
        for (int i = 0; i < 1000; i++) {
            // sys0 à l'origine, sys1 à 0,5 al ; les autres à plus de 1,7 al (tirés dans [1, 50] al par axe)
            double x = i == 0 ? 0 : i == 1 ? 0.5 : 1 + 49 * r.nextDouble();
            double y = i < 2 ? 0 : 1 + 49 * r.nextDouble(), z = i < 2 ? 0 : 1 + 49 * r.nextDouble();
            SpaceObject sys = at("sys" + i, gal, LY, x, y, z);
            SpaceObject star = at("star", sys, 1, 0, 0, 0);
            if (i == 0) star0 = star;
            if (i == 1) star1 = star;
            if (i < 2) {
                SpaceObject a = at("p1", star, 1, 1.5e8, 0, 0), b = at("p2", star, 1, 7.8e8, 0, 0), c = at("p3", star, 1, 4.5e9, 0, 0);
                if (i == 0) { p1 = a; p2 = b; p3 = c; } else { q1 = a; q2 = b; q3 = c; }
            }
        }
        giant = at("giant", gal, 1, 0, 1e12, 0); // rayon apparent 1e-3 rad
        radius(giant, 1e9);
        dwarf = at("dwarf", gal, 1, 0, 0, 1e12); // 1e-7 rad
        radius(dwarf, 1e5);
        probe = at("probe", gal, 1, 1e11, 0, 0);
        layer.unload(uni.getUuid(), true); // on part d'un serveur où rien n'est loadé
        assertEquals(0, layer.loadedObjects().size());
    }

    private Set<UUID> need(SpaceObject anchor, double x, double y, double z, Set<UUID> prev) {
        return Relevance.needed(layer, new PlayerSpacePos(anchor.getUuid(), x, y, z), 12345, prev, P);
    }

    private static Set<UUID> ids(SpaceObject... o) {
        Set<UUID> s = new HashSet<>();
        for (SpaceObject x : o) s.add(x.getUuid());
        return s;
    }

    @Test
    void boundedWithAncestorsAndOwnSystem() {
        int loaded = layer.loadedObjects().size();
        Set<UUID> n = need(p1, 0, 0, 0, Set.of());
        assertTrue(n.containsAll(ids(p1, star0, layer.peek(star0.getParent()), gal, uni)), "ancre + ancêtres");
        assertTrue(n.containsAll(ids(p2, p3)), "planètes du système de l'observateur");
        assertTrue(n.contains(giant.getUuid()), "gros dans le ciel");
        assertFalse(n.contains(dwarf.getUuid()), "petit et loin");
        assertFalse(n.containsAll(ids(q1, q2, q3)), "autre système : ni planètes ni étoile");
        assertFalse(n.contains(star1.getUuid()));
        assertTrue(n.size() < 20, "borné malgré 1000 systèmes : " + n.size());
        assertEquals(loaded, layer.loadedObjects().size(), "needed() ne charge rien");
    }

    @Test
    void expandsOnlyInsideSphereOfInfluence() {
        // dans l'étoile de sys1 : ses planètes oui, celles de sys0 non
        Set<UUID> n = need(star1, 0, 0, 0, Set.of());
        assertTrue(n.containsAll(ids(q1, q2, q3)));
        assertFalse(n.contains(p1.getUuid()));
        // hors de la sphère (1,5 x 4,5e9 km) mais dans le même système : plus de planètes, toujours les ancêtres
        n = need(star0, 1e11, 0, 0, Set.of());
        assertTrue(n.containsAll(ids(star0, gal, uni)));
        assertFalse(n.containsAll(ids(p2, p3)));
        assertTrue(n.size() < 20);
    }

    @Test
    void hysteresisAvoidsFlipFlopAtBoundary() {
        // probe est à x (km) de l'observateur (étoile de sys0, à l'origine de la galaxie) ; rNear = 1e10
        double[] x = {0.9 * P.rNearKm(), 1.2 * P.rNearKm(), 1.6 * P.rNearKm(), 1.2 * P.rNearKm()};
        boolean[] expected = {true, true, false, false};
        assertFalse(need(star0, 0, 0, 0, Set.of()).contains(probe.getUuid()), "probe de départ trop loin");
        Set<UUID> prev = Set.of();
        for (int i = 0; i < x.length; i++) {
            double d = x[i];
            layer.editTransform(probe.getUuid(), t -> t.x = d);
            prev = need(star0, 0, 0, 0, prev);
            assertEquals(expected[i], prev.contains(probe.getUuid()), "étape " + i);
            assertEquals(prev, need(star0, 0, 0, 0, prev), "stable sans mouvement, étape " + i);
        }
        // et le même 1,2 x rNear depuis un ensemble vide : pas chargé (c'est bien l'hystérésis qui le retenait à l'étape 1)
        assertFalse(need(star0, 0, 0, 0, Set.of()).contains(probe.getUuid()));
    }

    @Test
    void parentIsTheAnchorOverDimensionConfig() {
        var cfg = new fr.systamynna.spacemod.core.render.RenderConfig(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), star1.getUuid());
        assertEquals(star1.getUuid(), PlayerSpacePos.of(null, cfg).anchor(), "sans parent : repli sur la config");
        PlayerSpacePos pos = PlayerSpacePos.of(star0.getUuid(), cfg);
        assertEquals(new PlayerSpacePos(star0.getUuid(), 0, 0, 0), pos, "parent : ancre, décalage nul");
        Set<UUID> n = Relevance.needed(layer, pos, 12345, Set.of(), P);
        assertTrue(n.containsAll(ids(star0, p1, p2, p3)));
        assertFalse(n.containsAll(ids(star1)), "voisin de l'ancien hôte absent");
        assertFalse(n.containsAll(ids(q1, q2, q3)));
    }

    @Test
    void sweepEvictsIdleLeavesFirstAndRespectsPins() throws Exception {
        StrategicLayer s = new StrategicLayer();
        s.start(new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:")));
        SpaceObject a = s.add("a", s.tree().resolve("universe")), b = s.add("b", a), c = s.add("c", b);
        long idle = System.currentTimeMillis() + StrategicLayer.TTL_MS + 1;
        assertEquals(0, s.sweep(System.currentTimeMillis(), u -> false), "encore actifs");
        assertEquals(1, s.sweep(idle, u -> u.equals(b.getUuid())), "b retenu (abonné) : seul la feuille c part");
        assertFalse(s.isLoaded(c.getUuid()));
        s.pin(b.getUuid());
        assertEquals(0, s.sweep(idle, u -> false), "b épinglé retient a et la racine");
        s.unpin(b.getUuid());
        assertEquals(3, s.sweep(idle, u -> false), "feuilles d'abord : b, a puis la racine en un seul balayage");
        assertEquals(0, s.loadedObjects().size());
        // l'index suit un setParent
        s.load(a.getUuid());
        s.load(b.getUuid());
        s.load(c.getUuid());
        s.setParent(s.getOrThrow(c.getUuid()), s.getOrThrow(a.getUuid())); // instances rechargées
        assertTrue(s.unload(b.getUuid(), false));
        assertFalse(s.unload(a.getUuid(), false), "c est maintenant enfant loadé de a");
        s.stop();
    }
}
