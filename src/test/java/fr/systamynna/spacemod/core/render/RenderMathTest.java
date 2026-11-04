package fr.systamynna.spacemod.core.render;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.core.SpaceObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

class RenderMathTest {
    private final Map<UUID, SpaceObject> world = new HashMap<>();

    /** Objet à la pose donnée, d'échelle {@code scale} (le constructeur ne l'expose pas : on passe par le NBT). */
    private SpaceObject obj(SpaceObject parent, String name, double x, double y, double z, double yaw, double scale) {
        SpaceObject o = new SpaceObject(UUID.randomUUID(), parent == null ? null : parent.getUuid(), name);
        o.transform.x = x; o.transform.y = y; o.transform.z = z; o.transform.yaw = yaw;
        CompoundTag t = o.save();
        t.putDouble("scale", scale);
        o = SpaceObject.load(o.getUuid(), o.getParent(), t);
        world.put(o.getUuid(), o);
        return o;
    }

    private RenderConfig cfg(SpaceObject host) { return new RenderConfig(ResourceLocation.parse("a:b"), host.getUuid()); }

    @Test
    void siblingPlacedRelativeToHost() {
        SpaceObject sys = obj(null, "sys", 0, 0, 0, 0, 1);
        SpaceObject host = obj(sys, "host", 10, 0, 0, 0, 1);
        SpaceObject other = obj(sys, "other", 10, 0, 100, 0, 1);
        var ob = RenderMath.observer(world::get, host, cfg(host));
        var p = RenderMath.place(world::get, ob, other, new RenderTransform(), 5);
        assertEquals(0, p.pos().x, 1e-9);
        assertEquals(100, p.pos().z, 1e-9);
        assertEquals(Math.asin(5.0 / 100), p.angularRadius(), 1e-12);
    }

    @Test
    void hostYawRotatesTheSky() {
        SpaceObject sys = obj(null, "sys", 0, 0, 0, 0, 1);
        SpaceObject host = obj(sys, "host", 0, 0, 0, 90, 1); // lacet de 90° autour de Y
        SpaceObject other = obj(sys, "other", 100, 0, 0, 0, 1);
        var p = RenderMath.place(world::get, RenderMath.observer(world::get, host, cfg(host)), other, new RenderTransform(), 1);
        assertEquals(100, p.distance(), 1e-9);
        assertEquals(0, p.pos().x, 1e-9); // l'objet n'est plus sur l'axe X de l'observateur tourné
        assertEquals(1, Math.abs(p.pos().z) / 100, 1e-9);
    }

    @Test
    void transformUnitsAreScaleKilometres() {
        SpaceObject sys = obj(null, "sys", 0, 0, 0, 0, 1);
        SpaceObject planet = obj(sys, "planet", 1, 0, 0, 0, 1000);    // transform en unités de 1000 km
        SpaceObject moon = obj(planet, "moon", 0, 0, 1000, 0, 1);     // transform en km : 1000 km devant la planète
        var p = RenderMath.place(world::get, RenderMath.observer(world::get, planet, cfg(planet)), moon, new RenderTransform(), 100);
        assertEquals(1000, p.pos().z, 1e-6);  // la lune est posée dans le référentiel de la planète (km)
        assertEquals(100, p.radius(), 1e-12); // le rayon est déjà en km
        var q = RenderMath.place(world::get, RenderMath.observer(world::get, moon, cfg(moon)), planet, new RenderTransform(), 1);
        assertEquals(1000, q.distance(), 1e-6); // planète vue depuis la lune : 1000 km
    }

    @Test
    void observerOffsetMovesTheView() {
        SpaceObject sys = obj(null, "sys", 0, 0, 0, 0, 1);
        SpaceObject host = obj(sys, "host", 0, 0, 0, 0, 1);
        SpaceObject other = obj(sys, "other", 0, 0, 50, 0, 1);
        RenderConfig c = cfg(host);
        c.editAll("pos 0 0 20");
        var p = RenderMath.place(world::get, RenderMath.observer(world::get, host, c), other, new RenderTransform(), 1);
        assertEquals(30, p.pos().z, 1e-9);
    }

    @Test
    void noCommonAncestorOrSamePositionIsIgnored() {
        SpaceObject a = obj(null, "a", 0, 0, 0, 0, 1);
        SpaceObject b = obj(null, "b", 0, 0, 0, 0, 1);
        assertNull(RenderMath.place(world::get, RenderMath.observer(world::get, a, cfg(a)), b, new RenderTransform(), 1));
        assertNull(RenderMath.place(world::get, RenderMath.observer(world::get, a, cfg(a)), a, new RenderTransform(), 1)); // confondu avec l'observateur
    }

    @Test
    void insideObjectHasNoAngularRadius() {
        SpaceObject sys = obj(null, "sys", 0, 0, 0, 0, 1);
        SpaceObject host = obj(sys, "host", 0, 0, 0, 0, 1);
        SpaceObject other = obj(sys, "other", 0, 0, 1, 0, 1);
        var p = RenderMath.place(world::get, RenderMath.observer(world::get, host, cfg(host)), other, new RenderTransform(), 5);
        assertTrue(Double.isNaN(p.angularRadius()));
    }

    @Test
    void hugeDistancesKeepPrecision() {
        SpaceObject sys = obj(null, "sys", 0, 0, 0, 0, 1);
        SpaceObject host = obj(sys, "host", 1e15, 0, 0, 0, 1);
        SpaceObject other = obj(sys, "other", 1e15 + 1000, 0, 0, 0, 1);
        var p = RenderMath.place(world::get, RenderMath.observer(world::get, host, cfg(host)), other, new RenderTransform(), 1);
        assertEquals(1000, p.distance(), 1e-3);
    }

    @Test
    void lookAnglesFollowMinecraftConvention() {
        assertArrayEquals(new float[] {0, 0}, RenderMath.lookAngles(new org.joml.Vector3d(0, 0, 5)), 1e-4f);
        assertArrayEquals(new float[] {-90, 0}, RenderMath.lookAngles(new org.joml.Vector3d(5, 0, 0)), 1e-4f);
        assertArrayEquals(new float[] {90, 0}, RenderMath.lookAngles(new org.joml.Vector3d(-5, 0, 0)), 1e-4f);
        assertEquals(-90, RenderMath.lookAngles(new org.joml.Vector3d(0, 5, 0))[1], 1e-4f);
        assertNull(RenderMath.lookAngles(new org.joml.Vector3d()));
    }

    @Test
    void lookingAtThePlacedObjectPointsAtIt() {
        SpaceObject sys = obj(null, "sys", 0, 0, 0, 0, 1);
        SpaceObject host = obj(sys, "host", 3, 1, 0, 25, 1);
        SpaceObject other = obj(sys, "other", -40, 70, 90, 0, 1);
        RenderConfig c = cfg(host);
        c.editAll("pos 1 2 3 rot 10 20 30");
        var p = RenderMath.place(world::get, RenderMath.observer(world::get, host, c), other, new RenderTransform(), 0);
        float[] a = RenderMath.lookAngles(p.pos());
        double yaw = Math.toRadians(a[0]), pitch = Math.toRadians(a[1]);
        org.joml.Vector3d look = new org.joml.Vector3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)); // regard Minecraft
        org.joml.Vector3d want = new org.joml.Vector3d(p.pos()).normalize();
        assertEquals(0, look.sub(want).length(), 1e-6); // limite : yaw/pitch en float
    }
}
