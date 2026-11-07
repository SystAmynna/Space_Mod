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

    /** Comme {@link #obj}, avec le tag {@code stellar_system}. */
    private SpaceObject system(SpaceObject parent, String name) {
        SpaceObject o = obj(parent, name, 0, 0, 0, 0, 1);
        CompoundTag t = o.save();
        t.getCompound("components").put("stellar_system", new CompoundTag());
        o = SpaceObject.load(o.getUuid(), o.getParent(), t);
        world.put(o.getUuid(), o);
        return o;
    }

    private java.util.List<SpaceObject> childrenOf(UUID parent) { return world.values().stream().filter(o -> parent.equals(o.getParent())).toList(); }

    @Test
    void stellarSystemOfFindsTheTaggedAncestor() {
        SpaceObject gal = obj(null, "gal", 0, 0, 0, 0, 1);
        SpaceObject sys = system(gal, "sys");
        SpaceObject star = obj(sys, "star", 0, 0, 0, 0, 1);
        SpaceObject sister = obj(sys, "sister", 5, 0, 0, 0, 1);
        SpaceObject planet = obj(star, "planet", 1, 0, 0, 0, 1);
        SpaceObject far = system(gal, "far");
        SpaceObject farPlanet = obj(far, "farPlanet", 1, 0, 0, 0, 1);
        SpaceObject untagged = obj(gal, "untagged", 7, 0, 0, 0, 1);
        for (SpaceObject host : new SpaceObject[] {planet, star, sister, sys}) // dans le système (le nœud lui-même compris)
            assertSame(sys, RenderMath.stellarSystemOf(world::get, RenderMath.observer(world::get, host, cfg(host)), star), host.getName());
        for (SpaceObject host : new SpaceObject[] {gal, far, farPlanet, untagged}) // niveau galaxie, autre système, objet sans tag
            assertNull(RenderMath.stellarSystemOf(world::get, RenderMath.observer(world::get, host, cfg(host)), star), host.getName());
        // sans tag sur le parent, l'étoile n'est dans aucun système
        SpaceObject bare = obj(gal, "bare", 0, 0, 0, 0, 1), bareStar = obj(bare, "bareStar", 0, 0, 0, 0, 1);
        assertNull(RenderMath.stellarSystemOf(world::get, RenderMath.observer(world::get, bareStar, cfg(bareStar)), bareStar));
    }

    @Test
    void farthestFromIsTheLargestDistanceToTheStar() {
        SpaceObject sys = system(null, "sys");
        SpaceObject star = obj(sys, "star", 10, 0, 0, 0, 1);
        obj(star, "near", 0, 0, 100, 0, 1);                  // 100 km de l'étoile (repère de l'étoile)
        SpaceObject planet = obj(star, "far", 300, 0, 0, 0, 1); // 300 km de l'étoile
        obj(planet, "moon", 0, 0, 50, 0, 1);                  // à ~304 km de l'étoile
        obj(sys, "sister", -90, 0, 0, 0, 1);                  // à 100 km de l'étoile
        double d = RenderMath.farthestFrom(this::childrenOf, sys, star);
        assertEquals(Math.sqrt(300 * 300 + 50 * 50), d, 1e-6);
        assertEquals(0, RenderMath.farthestFrom(this::childrenOf, sys, obj(null, "stranger", 0, 0, 0, 0, 1)), 0); // étoile hors du système
        SpaceObject lonely = system(null, "lonely");
        assertEquals(0, RenderMath.farthestFrom(this::childrenOf, lonely, obj(lonely, "alone", 0, 0, 0, 0, 1)), 0);
    }

    @Test
    void farthestFromIgnoresArtificialStructuresAndTheirChildren() {
        SpaceObject sys = system(null, "sys");
        SpaceObject star = obj(sys, "star", 0, 0, 0, 0, 1);
        obj(star, "planet", 100, 0, 0, 0, 1);
        SpaceObject ship = obj(sys, "ship", 9000, 0, 0, 0, 1);
        obj(ship, "module", 500, 0, 0, 0, 1);
        double before = RenderMath.farthestFrom(this::childrenOf, sys, star);
        assertEquals(9500, before, 1e-6); // sans tag, le vaisseau compterait
        CompoundTag t = ship.save();
        t.getCompound("components").put("artificial_structure", new CompoundTag());
        SpaceObject tagged = SpaceObject.load(ship.getUuid(), ship.getParent(), t);
        world.put(tagged.getUuid(), tagged);
        assertEquals(100, RenderMath.farthestFrom(this::childrenOf, sys, star), 1e-6); // ni lui ni son module ne comptent
    }

    @Test
    void flareFollowsTheDocumentedTable() { // étoile en 0, objet notable le plus loin en 10
        double dmax = 10;
        assertEquals(RenderMath.flare(2 / dmax), RenderMath.flare(18 / dmax), 1e-12);
        assertEquals(RenderMath.flare(7 / dmax), RenderMath.flare(13 / dmax), 1e-12);
        assertEquals(1, RenderMath.flare(10 / dmax), 1e-12);
        assertTrue(RenderMath.flare(2 / dmax) < RenderMath.flare(7 / dmax) && RenderMath.flare(7 / dmax) < RenderMath.flare(10 / dmax));
        assertEquals(0, RenderMath.flare(20 / dmax));
        assertEquals(0, RenderMath.flare(21 / dmax));
        for (double x = 0.05; x < 1; x += 0.05) assertTrue(RenderMath.flare(x + 0.05) > RenderMath.flare(x)); // croît jusqu'au pic
        for (double x = 1.05; x < 1.95; x += 0.05) assertTrue(RenderMath.flare(x + 0.05) < RenderMath.flare(x)); // puis décroît
    }

    @Test
    void flareIsASymmetricParabola() {
        assertEquals(0, RenderMath.flare(0));
        assertEquals(0.75, RenderMath.flare(0.5), 1e-12);
        assertEquals(1, RenderMath.flare(1), 1e-12);
        assertEquals(0.75, RenderMath.flare(1.5), 1e-12);
        assertEquals(0, RenderMath.flare(2));
        assertEquals(0, RenderMath.flare(-1));
        assertEquals(0, RenderMath.flare(3));
        for (double x : new double[] {0.1, 0.3, 0.7}) assertEquals(RenderMath.flare(x), RenderMath.flare(2 - x), 1e-12);
    }

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
