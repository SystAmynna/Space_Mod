package fr.systamynna.spacemod.core.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.component.SpaceComponent;
import fr.systamynna.spacemod.core.component.SpaceComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

class SpaceRenderTest {
    @Test
    void planetRoundTripThroughRegistry() {
        SpaceRender r = new SpaceRender();
        r.edit("add planet earth.png 6371");
        r.edit("transform 0 pos 1 2 3 scale ~ 2 ~");
        SpaceComponent back = SpaceComponents.load(SpaceRender.ID, r.save());
        PlanetRender p = (PlanetRender) ((SpaceRender) back).objects.get(0);
        assertEquals("earth.png", p.texture);
        assertEquals(6371, p.radius(), 0);
        assertEquals(2, p.transform().pos.y, 0);
        assertEquals(2, p.transform().scale.y, 0);
        assertEquals(1, p.transform().scale.x, 0);
    }

    @Test
    void invalidEditsAreRejectedWithoutChange() {
        SpaceRender r = new SpaceRender();
        assertThrows(SpaceObjectException.class, () -> r.edit("add planet x.png -1"));
        assertThrows(SpaceObjectException.class, () -> r.edit("add nothing"));
        r.edit("add planet x.png 1");
        assertThrows(SpaceObjectException.class, () -> r.edit("transform 0 scale 0 1 1"));
        assertThrows(SpaceObjectException.class, () -> r.edit("transform 5 pos 1 1 1"));
        assertEquals(1, r.objects.get(0).transform().scale.x, 0);
    }

    @Test
    void loadSkipsInvalidAndUnknownObjects() {
        CompoundTag t = new SpaceRender().save();
        var l = new net.minecraft.nbt.ListTag();
        CompoundTag bad = new CompoundTag();
        bad.putString("type", "planet"); // rayon absent
        CompoundTag unknown = new CompoundTag();
        unknown.putString("type", "ufo");
        l.add(bad);
        l.add(unknown);
        t.put("objects", l);
        assertTrue(SpaceRender.load(t).objects.isEmpty());
    }

    @Test
    void renderTransformRejectsInvalidData() {
        CompoundTag t = new CompoundTag();
        t.putDouble("x", Double.NaN);
        t.putDouble("qw", 2); // non unitaire
        t.putDouble("sx", -1);
        RenderTransform r = RenderTransform.load(t);
        assertEquals(0, r.pos.x, 0);
        assertEquals(1, r.rot.w, 0);
        assertEquals(1, r.scale.x, 0);
    }

    @Test
    void renderTransformEulerQuaternionRoundTrip() {
        RenderTransform a = new RenderTransform();
        a.editAll("rot 30 20 10");
        RenderTransform b = RenderTransform.load(a.save());
        assertEquals(0, a.rot.difference(b.rot).angle(), 1e-12);
    }

    @Test
    void configEditKeepsTildeAndRoundTrips() {
        RenderConfig c = new RenderConfig(ResourceLocation.parse("a:b"), java.util.UUID.randomUUID());
        c.editAll("pos 1 2 3 rot 4 ~ 6 scale ~ ~ 7");
        c.editAll("pos ~ 9 ~");
        assertArrayEquals(new double[] {1, 9, 3}, c.pos);
        assertArrayEquals(new double[] {4, 0, 6}, c.rot);
        assertArrayEquals(new double[] {1, 1, 7}, c.scale);
        RenderConfig back = RenderConfig.load(c.save());
        assertEquals(c.object, back.object);
        assertArrayEquals(c.pos, back.pos);
        assertThrows(SpaceObjectException.class, () -> c.editAll("pos 1 2"));
        assertThrows(SpaceObjectException.class, () -> c.editAll("scale 0 1 1"));
        assertArrayEquals(new double[] {1, 9, 3}, c.pos); // inchangé après erreur
    }

    @Test
    void assetNamesCannotEscapeTheFolder() {
        assertTrue(RenderAssets.NAME.matcher("planets/earth.png").matches());
        assertFalse(RenderAssets.NAME.matcher("../secret.png").matches());
        assertFalse(RenderAssets.NAME.matcher("/etc/passwd.png").matches());
        assertFalse(RenderAssets.NAME.matcher("a.png/../b.png").matches());
    }
}
