package fr.systamynna.spacemod.core.render;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

class GalaxyRenderTest {
    @Test
    void cloudRoundTripsThroughTheRegistry() {
        GalaxyCloudRender g = new GalaxyCloudRender(new RenderTransform(), 4.7e17, 4, 0.03);
        GalaxyCloudRender back = (GalaxyCloudRender) SpaceRenderObjects.load(g.save());
        assertNotNull(back);
        assertEquals(4.7e17, back.radius());
        assertEquals(4, back.arms);
        assertEquals(0.03, back.thickness);
        assertTrue(g.assets().isEmpty());
    }

    @Test
    void starsRoundTripThroughTheRegistry() {
        GalaxyStarsRender g = new GalaxyStarsRender(new RenderTransform(), 4.7e17, 4, 0.03, 2.5);
        GalaxyStarsRender back = (GalaxyStarsRender) SpaceRenderObjects.load(g.save());
        assertNotNull(back);
        assertEquals(4.7e17, back.radius());
        assertEquals(4, back.arms);
        assertEquals(0.03, back.thickness);
        assertEquals(2.5, back.density);
        assertTrue(g.assets().isEmpty());
        // une donnée sans densité (ancien format) retombe sur 1
        CompoundTag t = g.save();
        t.remove("density");
        assertEquals(1, ((GalaxyStarsRender) SpaceRenderObjects.load(t)).density);
    }

    @Test
    void invalidDataIsRejected() {
        for (double[] bad : new double[][] {{0, 2, 0.04, 1}, {-1, 2, 0.04, 1}, {Double.NaN, 2, 0.04, 1}, {1e17, 0, 0.04, 1}, {1e17, 99, 0.04, 1},
                {1e17, 2, 0, 1}, {1e17, 2, 2, 1}, {1e17, 2, 0.04, 0}, {1e17, 2, 0.04, 21}, {1e17, 2, 0.04, Double.NaN}}) {
            CompoundTag t = new GalaxyStarsRender(new RenderTransform(), 1, 2, 0.04, 1).save();
            t.putDouble("radius", bad[0]);
            t.putInt("arms", (int) bad[1]);
            t.putDouble("thickness", bad[2]);
            t.putDouble("density", bad[3]);
            assertNull(GalaxyStarsRender.load(t), java.util.Arrays.toString(bad));
            if (bad[3] == 1) assertNull(GalaxyCloudRender.load(t), java.util.Arrays.toString(bad));
        }
    }

    @Test
    void oldGalaxyTypeIsIgnored() {
        CompoundTag t = new GalaxyCloudRender(new RenderTransform(), 1e17, 2, 0.04).save();
        t.putString("type", "galaxy");
        assertNull(SpaceRenderObjects.load(t));
    }

    @Test
    void createFromCommandArgs() {
        GalaxyCloudRender c = (GalaxyCloudRender) SpaceRenderObjects.create("galaxy_cloud", List.of("1e17", "3"));
        assertEquals(3, c.arms);
        assertEquals(0.04, c.thickness);
        GalaxyStarsRender s = (GalaxyStarsRender) SpaceRenderObjects.create("galaxy_stars", List.of("1e17", "3", "0.05", "4"));
        assertEquals(4, s.density);
        assertEquals(0.05, s.thickness);
        assertEquals(1, ((GalaxyStarsRender) SpaceRenderObjects.create("galaxy_stars", List.of("1e17"))).density);
        for (String type : List.of("galaxy_cloud", "galaxy_stars")) {
            assertThrows(RuntimeException.class, () -> SpaceRenderObjects.create(type, List.of()));
            assertThrows(RuntimeException.class, () -> SpaceRenderObjects.create(type, List.of("abc")));
            assertThrows(RuntimeException.class, () -> SpaceRenderObjects.create(type, List.of("1e17", "2.5"))); // bras non entier
        }
    }

    @Test
    void cloudColoursRoundTripAndDefault() {
        Rgba core = new Rgba(0.1, 0.2, 0.3, 2.5), edge = new Rgba(1, 0, 0.5, 0);
        GalaxyCloudRender back = (GalaxyCloudRender) SpaceRenderObjects.load(new GalaxyCloudRender(new RenderTransform(), 1e17, 2, 0.04, core, edge).save());
        assertEquals(core, back.core);
        assertEquals(edge, back.edge);
        // ancien format, sans couleurs : l'aspect d'avant
        CompoundTag t = new GalaxyCloudRender(new RenderTransform(), 1e17, 2, 0.04).save();
        t.remove("core");
        t.remove("edge");
        GalaxyCloudRender old = (GalaxyCloudRender) SpaceRenderObjects.load(t);
        assertEquals(GalaxyCloudRender.DEFAULT_CORE, old.core);
        assertEquals(GalaxyCloudRender.DEFAULT_EDGE, old.edge);
        // champ manquant ou non fini : valeur par défaut de ce champ ; hors bornes : borné
        CompoundTag c = new CompoundTag();
        c.putDouble("r", 7);
        c.putDouble("g", Double.NaN);
        c.putDouble("a", 99);
        t.put("core", c);
        GalaxyCloudRender fixed = (GalaxyCloudRender) SpaceRenderObjects.load(t);
        assertEquals(new Rgba(1, GalaxyCloudRender.DEFAULT_CORE.g(), GalaxyCloudRender.DEFAULT_CORE.b(), Rgba.MAX_A), fixed.core);
    }

    @Test
    void rgbaParsesHex() {
        assertEquals(new Rgba(1, 170 / 255.0, 0, 1), Rgba.parse("FFAA00"));
        assertEquals(new Rgba(0x33 / 255.0, 0x44 / 255.0, 1, 0x80 / 255.0), Rgba.parse("#3344FF80"));
        for (String bad : new String[] {"", "FFF", "GGGGGG", "FFAA000", "FFAA00AA1"}) assertNull(Rgba.parse(bad), bad);
    }

    @Test
    void createCloudWithColours() {
        GalaxyCloudRender c = (GalaxyCloudRender) SpaceRenderObjects.create("galaxy_cloud", List.of("1e17", "3", "0.05", "FFAA00", "3344FF80"));
        assertEquals(Rgba.parse("FFAA00"), c.core);
        assertEquals(Rgba.parse("3344FF80"), c.edge);
        assertEquals(GalaxyCloudRender.DEFAULT_EDGE, ((GalaxyCloudRender) SpaceRenderObjects.create("galaxy_cloud", List.of("1e17", "3", "0.05", "FFAA00"))).edge);
        assertThrows(RuntimeException.class, () -> SpaceRenderObjects.create("galaxy_cloud", List.of("1e17", "3", "0.05", "ZZZZZZ")));
        assertThrows(RuntimeException.class, () -> SpaceRenderObjects.create("galaxy_cloud", List.of("1e17", "3", "0.05", "FFAA00", "3344FF", "extra")));
    }
}
