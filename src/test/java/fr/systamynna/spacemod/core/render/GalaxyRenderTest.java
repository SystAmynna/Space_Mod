package fr.systamynna.spacemod.core.render;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

class GalaxyRenderTest {
    @Test
    void roundTripsThroughTheRegistry() {
        GalaxyRender g = new GalaxyRender(new RenderTransform(), 4.7e17, 4, 0.03);
        GalaxyRender back = (GalaxyRender) SpaceRenderObjects.load(g.save());
        assertNotNull(back);
        assertEquals(4.7e17, back.radius());
        assertEquals(4, back.arms);
        assertEquals(0.03, back.thickness);
        assertTrue(g.assets().isEmpty());
    }

    @Test
    void invalidDataIsRejected() {
        for (double[] bad : new double[][] {{0, 2, 0.04}, {-1, 2, 0.04}, {Double.NaN, 2, 0.04}, {1e17, 0, 0.04}, {1e17, 99, 0.04}, {1e17, 2, 0}, {1e17, 2, 2}}) {
            CompoundTag t = new GalaxyRender(new RenderTransform(), 1, 2, 0.04).save();
            t.putDouble("radius", bad[0]);
            t.putInt("arms", (int) bad[1]);
            t.putDouble("thickness", bad[2]);
            assertNull(GalaxyRender.load(t), java.util.Arrays.toString(bad));
        }
    }

    @Test
    void createFromCommandArgs() {
        GalaxyRender g = (GalaxyRender) SpaceRenderObjects.create("galaxy", List.of("1e17", "3"));
        assertEquals(3, g.arms);
        assertEquals(0.04, g.thickness);
        assertThrows(RuntimeException.class, () -> SpaceRenderObjects.create("galaxy", List.of()));
        assertThrows(RuntimeException.class, () -> SpaceRenderObjects.create("galaxy", List.of("abc")));
    }
}
