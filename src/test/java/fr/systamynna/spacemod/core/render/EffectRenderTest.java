package fr.systamynna.spacemod.core.render;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

/** Star, black_hole, distorsion, gamma_eruption, ring : aller-retour par le registre, données invalides, création en commande. */
class EffectRenderTest {
    private static final List<SpaceRenderObject> ALL = List.of(
            new StarRender(new RenderTransform(), 6.9e5, StarRender.DEFAULT_HOT, StarRender.DEFAULT_COOL, 2, 3, 2.5),
            new BlackHoleRender(new RenderTransform(), 162.5, new Rgba(0.1, 0, 0, 1)),
            new DistorsionRender(new RenderTransform(), 62.5, 2.8, 25),
            new GammaEruptionRender(new RenderTransform(), 1e9, 0.04, GammaEruptionRender.DEFAULT_A, GammaEruptionRender.DEFAULT_B, 3, 33, 0.8, 35, 0.3),
            new RingRender(new RenderTransform(), 136775, 122170, RingRender.DEFAULT_INNER, RingRender.DEFAULT_OUTER, 0.65, true, 58232, 683));

    @Test
    void roundTripsThroughTheRegistry() {
        for (SpaceRenderObject o : ALL) {
            SpaceRenderObject back = SpaceRenderObjects.load(o.save());
            assertNotNull(back, o.type());
            assertEquals(o.type(), back.type());
            assertEquals(o.radius(), back.radius());
            assertEquals(o.describe(), back.describe());
            assertTrue(o.assets().isEmpty());
        }
    }

    @Test
    void invalidDataIsRejected() {
        for (SpaceRenderObject o : ALL) {
            for (double bad : new double[] {0, -1, Double.NaN}) {
                CompoundTag t = o.save();
                t.putDouble("radius", bad);
                assertNull(SpaceRenderObjects.load(t), o.type() + " radius " + bad);
            }
        }
        CompoundTag t = ALL.get(4).save();
        t.putDouble("inner", 2e5); // intérieur >= extérieur
        assertNull(SpaceRenderObjects.load(t));
        t = ALL.get(4).save();
        t.putDouble("opacity", 1.5);
        assertNull(SpaceRenderObjects.load(t));
        t = ALL.get(3).save();
        t.putDouble("width", 0);
        assertNull(SpaceRenderObjects.load(t));
        for (double bad : new double[] {-1, 11, Double.NaN}) {
            t = ALL.get(0).save();
            t.putDouble("emissive", bad);
            assertNull(SpaceRenderObjects.load(t), "emissive " + bad);
        }
        t = ALL.get(2).save();
        t.putDouble("extent", 1);
        assertNull(SpaceRenderObjects.load(t));
        for (double bad : new double[] {0, 11, -11, Double.NaN}) { // strength signé (trou blanc < 0), jamais nul
            t = ALL.get(2).save();
            t.putDouble("strength", bad);
            assertNull(SpaceRenderObjects.load(t), "strength " + bad);
        }
        t = ALL.get(2).save();
        t.putDouble("strength", -2.8);
        assertEquals(-2.8, ((DistorsionRender) SpaceRenderObjects.load(t)).strength);
        t = ALL.get(3).save();
        t.putDouble("spin", 187.6); // vraie rotation du pulsar du Crabe
        assertEquals(187.6, ((GammaEruptionRender) SpaceRenderObjects.load(t)).spin);
    }

    @Test
    void createFromCommandArgs() {
        StarRender s = (StarRender) SpaceRenderObjects.create("star", List.of("6.9e5", "FFEEAA", "FF5500", "2", "0"));
        assertEquals(Rgba.parse("FFEEAA"), s.hot);
        assertEquals(0, s.granulation);
        assertEquals(StarRender.DEFAULT_EMISSIVE, s.emissive);
        assertEquals(4, ((StarRender) SpaceRenderObjects.create("star", List.of("1", "FFEEAA", "FF5500", "1", "1", "4"))).emissive);
        CompoundTag old = s.save(); // ancien format, sans emissive : défaut
        old.remove("emissive");
        assertEquals(StarRender.DEFAULT_EMISSIVE, ((StarRender) SpaceRenderObjects.load(old)).emissive);
        assertEquals(StarRender.DEFAULT_COOL, ((StarRender) SpaceRenderObjects.create("star", List.of("1"))).cool);
        assertEquals(Rgba.parse("000000"), ((BlackHoleRender) SpaceRenderObjects.create("black_hole", List.of("100"))).color);
        assertEquals(DistorsionRender.DEFAULT_STRENGTH, ((DistorsionRender) SpaceRenderObjects.create("distorsion", List.of("100"))).strength);
        assertEquals(7, ((GammaEruptionRender) SpaceRenderObjects.create("gamma_eruption", List.of("1e9", "0.1", "FFFFFF", "0000FF", "2", "7"))).seed);
        RingRender r = (RingRender) SpaceRenderObjects.create("ring", List.of("100", "200", "FF0000", "00FF00", "0.5", "true", "50"));
        assertEquals(200, r.radius());
        assertTrue(r.emissive);
        assertEquals(50, r.occluder);
        for (String type : List.of("star", "black_hole", "distorsion", "gamma_eruption", "ring")) {
            assertThrows(RuntimeException.class, () -> SpaceRenderObjects.create(type, List.of()));
            assertThrows(RuntimeException.class, () -> SpaceRenderObjects.create(type, List.of("abc", "abc")));
        }
        assertThrows(RuntimeException.class, () -> SpaceRenderObjects.create("ring", List.of("200", "100"))); // intérieur > extérieur
        assertThrows(RuntimeException.class, () -> SpaceRenderObjects.create("ring", List.of("100", "200", "FF0000", "00FF00", "0.5", "maybe")));
        assertThrows(RuntimeException.class, () -> SpaceRenderObjects.create("gamma_eruption", List.of("1e9", "0.1", "FFFFFF", "0000FF", "2", "1.5"))); // graine non entière
    }

    @Test
    void shadowAndAtmosphere() {
        ShadowRender s = (ShadowRender) SpaceRenderObjects.create("shadow", List.of("6371"));
        assertEquals(ShadowRender.DEFAULT_AMBIENT, s.ambient);
        assertEquals(s.describe(), SpaceRenderObjects.load(s.save()).describe());
        AtmosphereRender a = (AtmosphereRender) SpaceRenderObjects.create("atmosphere", List.of("6560", "6371", "4D8CFF", "FF6A2A", "2"));
        assertEquals(6371, a.inner);
        assertEquals(Rgba.parse("4D8CFF"), a.day);
        assertEquals(a.describe(), SpaceRenderObjects.load(a.save()).describe());
        assertEquals(AtmosphereRender.DEFAULT_SUNSET, ((AtmosphereRender) SpaceRenderObjects.create("atmosphere", List.of("2", "1"))).sunset);
        for (List<String> bad : List.of(List.of("6371", "1.5"), List.of("6371", "0.1", "0"), List.of("0"), List.of("6371", "0.1", "0.1", "x")))
            assertThrows(RuntimeException.class, () -> SpaceRenderObjects.create("shadow", bad), bad.toString());
        for (List<String> bad : List.of(List.of("100"), List.of("100", "100"), List.of("100", "200"), List.of("200", "100", "4D8CFF", "FF6A2A", "0"), List.of("200", "100", "zz")))
            assertThrows(RuntimeException.class, () -> SpaceRenderObjects.create("atmosphere", bad), bad.toString());
        CompoundTag t = a.save();
        t.putDouble("inner", 0);
        assertNull(SpaceRenderObjects.load(t));
        t = s.save();
        t.putDouble("radius", Double.NaN);
        assertNull(SpaceRenderObjects.load(t));
    }
}
