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
}
