package fr.systamynna.spacemod.strategic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.strategic.component.Component;
import fr.systamynna.spacemod.strategic.component.ComponentType;
import fr.systamynna.spacemod.strategic.component.ComponentTypes;
import fr.systamynna.spacemod.strategic.component.Controller;
import fr.systamynna.spacemod.strategic.component.ControllerTypes;
import fr.systamynna.spacemod.strategic.component.Destroyable;
import fr.systamynna.spacemod.strategic.component.ExclusionVolume;
import fr.systamynna.spacemod.strategic.component.HostedDimension;
import fr.systamynna.spacemod.strategic.component.LogicalVolume;
import fr.systamynna.spacemod.strategic.component.Pilotable;
import fr.systamynna.spacemod.strategic.component.PortsPoints.Port;
import fr.systamynna.spacemod.strategic.component.RailFollower;
import fr.systamynna.spacemod.strategic.component.Render;
import fr.systamynna.spacemod.strategic.component.Transform;
import fr.systamynna.spacemod.strategic.component.UnknownComponent;
import fr.systamynna.spacemod.strategic.component.UnknownController;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

class SpaceObjectTest {
    private static final ResourceLocation MODEL = ResourceLocation.parse("amynnaspacemod:proxy/rock");
    private static final ResourceLocation DIM = ResourceLocation.parse("amynnaspacemod:terra");

    private static SpaceObject roundTrip(SpaceObject object) {
        return SpaceObject.load(object.save());
    }

    private static void assertSameContent(SpaceObject expected, SpaceObject actual) {
        assertEquals(expected.id(), actual.id());
        assertEquals(expected.parent(), actual.parent());
        assertEquals(expected.children(), actual.children());
        assertEquals(expected.controller(), actual.controller());
        assertEquals(expected.isDestroyed(), actual.isDestroyed());
        assertEquals(Set.copyOf(expected.components()), Set.copyOf(actual.components()));
    }

    @Test
    void everyArchetypeSurvivesARoundTrip() {
        RailFollower rail = RailFollower.orbit(72_000, 2_000, 5, 0.9);
        List<SpaceObject> objects = List.of(
                Archetypes.galaxy("Voie lactée", Transform.at(1e21, 0, 0).withScale(1e9, 1e9, 1e9), null),
                Archetypes.system("Sol", Transform.at(2.6e17, 0, 0), RailFollower.orbit(1L << 40, 2.6e17, 0, 0.1)),
                Archetypes.star("Sol", Transform.ORIGIN, RailFollower.spin(24_000, 3), new Render(MODEL, 6.9e8), 1.4e9),
                Archetypes.planet("Terra", rail, new Render(MODEL, 6.4e6), DIM, 512,
                        List.of(new Port("nord", 1.5, -2.5, 3.5, 90, 40), new Port("sud", 0, 0, 0, 360, 0))),
                Archetypes.megastructure("Anneau", rail, ResourceLocation.parse("amynnaspacemod:anneau"), 2048, List.of()),
                Archetypes.station("Relais", rail, ResourceLocation.parse("amynnaspacemod:relais"), 256),
                Archetypes.cruiser("Albatros", Transform.at(1e12, -3.25, 7), ResourceLocation.parse("amynnaspacemod:albatros"), 128));
        for (SpaceObject object : objects) {
            assertSameContent(object, roundTrip(object));
        }
    }

    @Test
    void archetypesFollowTheArchitectureTable() {
        SpaceObject planet = Archetypes.planet("Terra", null, new Render(MODEL, 1), DIM, 512, List.of());
        assertTrue(planet.has(ComponentTypes.HOSTED_DIMENSION) && planet.has(ComponentTypes.PORTS_POINTS)
                && planet.has(ComponentTypes.LOGICAL_VOLUME) && planet.has(ComponentTypes.RENDER));
        assertEquals(512, planet.get(ComponentTypes.LOGICAL_VOLUME).radius());
        assertEquals(1024, planet.get(ComponentTypes.EXCLUSION_VOLUME).radius());
        assertNull(planet.controller(), "pas de contrôleur : immobile");
        assertFalse(planet.has(ComponentTypes.DESTROYABLE), "une planète est indestructible");

        SpaceObject star = Archetypes.star("Sol", Transform.ORIGIN, null, new Render(MODEL, 1), 100);
        assertFalse(star.has(ComponentTypes.HOSTED_DIMENSION));
        assertFalse(star.has(ComponentTypes.LOGICAL_VOLUME), "une étoile exclut sans avoir de dimension");
        assertEquals(100, star.get(ComponentTypes.EXCLUSION_VOLUME).radius());

        SpaceObject cruiser = Archetypes.cruiser("Albatros", Transform.ORIGIN, DIM, 100);
        assertInstanceOf(Pilotable.class, cruiser.controller());
        assertTrue(cruiser.has(ComponentTypes.DESTROYABLE));
        assertEquals(360, cruiser.get(ComponentTypes.PORTS_POINTS).ports().get(0).openingDegrees());
    }

    @Test
    void doublesKeepTheirExactBits() {
        Transform tricky = new Transform(Math.nextUp(1e15), -Math.E, Double.MIN_VALUE, 0, 0, 0, 1, 1e-300, -1e300, Math.PI);
        SpaceObject object = new SpaceObject(UUID.randomUUID()).put(tricky);
        Transform back = roundTrip(object).get(ComponentTypes.TRANSFORM);
        assertEquals(Double.doubleToLongBits(tricky.tx()), Double.doubleToLongBits(back.tx()));
        assertEquals(Double.doubleToLongBits(tricky.ty()), Double.doubleToLongBits(back.ty()));
        assertEquals(tricky, back);
    }

    @Test
    void transformHasTranslationRotationAndScale() {
        Transform t = Transform.at(1, 2, 3);
        assertEquals(1, t.sx());
        assertEquals(List.of(1.0, 2.0, 3.0), List.of(t.translation()[0], t.translation()[1], t.translation()[2]));
        // rotation de 90° autour de Z, échelle 2, translation (1, 0, 0) : (1, 0, 0) -> (1, 2, 0)
        double h = Math.sqrt(0.5);
        Transform frame = new Transform(1, 0, 0, 0, 0, h, h, 2, 2, 2);
        double[] p = frame.apply(new double[] { 1, 0, 0 });
        assertEquals(1, p[0], 1e-12);
        assertEquals(2, p[1], 1e-12);
        assertEquals(0, p[2], 1e-12);
        double[] back = frame.invert(p);
        assertEquals(1, back[0], 1e-12);
        assertEquals(0, back[1], 1e-12);
        assertThrows(IllegalArgumentException.class, () -> new Transform(0, 0, 0, 0, 0, 0, 0, 1, 1, 1));
    }

    @Test
    void parentAndChildrenSurviveARoundTripInOrder() {
        SpaceObject object = new SpaceObject(UUID.randomUUID());
        UUID parent = UUID.randomUUID();
        object.setParent(parent);
        List<UUID> kids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        kids.forEach(object::addChild);
        object.addChild(kids.get(0));
        SpaceObject back = roundTrip(object);
        assertEquals(parent, back.parent());
        assertEquals(kids, List.copyOf(back.children()), "ordre d'ajout conservé, sans doublon");
        assertTrue(roundTrip(new SpaceObject(UUID.randomUUID())).children().isEmpty());
        assertFalse(new SpaceObject(UUID.randomUUID()).save().contains("children"), "absent quand il n'y en a pas");
        object.removeChild(kids.get(1));
        assertEquals(List.of(kids.get(0), kids.get(2)), List.copyOf(roundTrip(object).children()));
    }

    @Test
    void controllerIsAPropertyAndNullMeansStill() {
        SpaceObject still = new SpaceObject(UUID.randomUUID());
        assertNull(roundTrip(still).controller());
        SpaceObject moving = new SpaceObject(UUID.randomUUID()).controller(new RailFollower(
                new RailFollower.Orbit(100, 5, 1, 0.5), new RailFollower.Spin(40, 2)));
        assertEquals(moving.controller(), roundTrip(moving).controller());
        SpaceObject piloted = new SpaceObject(UUID.randomUUID()).controller(new Pilotable(0.5, 0, -0.25));
        assertEquals(piloted.controller(), roundTrip(piloted).controller());
    }

    @Test
    void unknownComponentIsKeptVerbatim() {
        CompoundTag data = new CompoundTag();
        data.putString("note", "venu d'un autre mod");
        data.putLong("n", 42);
        ResourceLocation foreign = ResourceLocation.parse("autremod:blindage");
        SpaceObject object = new SpaceObject(UUID.randomUUID()).put(new LogicalVolume(10));
        CompoundTag saved = object.save();
        saved.getCompound("components").put(foreign.toString(), data);

        SpaceObject loaded = SpaceObject.load(saved);
        Component kept = loaded.components().stream().filter(c -> c.typeId().equals(foreign)).findFirst().orElseThrow();
        assertInstanceOf(UnknownComponent.class, kept);
        assertEquals(data, kept.save());
        assertEquals(data, SpaceObject.load(loaded.save()).components().stream()
                .filter(c -> c.typeId().equals(foreign)).findFirst().orElseThrow().save());
    }

    @Test
    void unknownControllerIsKeptVerbatim() {
        CompoundTag data = new CompoundTag();
        data.putDouble("cap", 1.5);
        ResourceLocation foreign = ResourceLocation.parse("autremod:autopilote");
        CompoundTag saved = new SpaceObject(UUID.randomUUID()).save();
        CompoundTag controller = new CompoundTag();
        controller.putString("type", foreign.toString());
        controller.put("data", data);
        saved.put("controller", controller);

        SpaceObject loaded = SpaceObject.load(saved);
        assertInstanceOf(UnknownController.class, loaded.controller());
        assertEquals(data, loaded.controller().save());
        assertEquals(saved.getCompound("controller"), roundTrip(loaded).save().getCompound("controller"));
    }

    /** Un autre mod ajoute son composant et son contrôleur sans toucher à ce mod. */
    record Shield(int strength) implements Component {
        static final ResourceLocation ID = ResourceLocation.parse("autremod:bouclier");
        static final ComponentType<Shield> TYPE = ComponentTypes.register(ID, tag -> new Shield(tag.getInt("strength")));

        @Override
        public ResourceLocation typeId() {
            return ID;
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("strength", strength);
            return tag;
        }
    }

    record Hover(double height) implements Controller {
        static final ResourceLocation ID = ResourceLocation.parse("autremod:vol_stationnaire");
        static final AtomicInteger REGISTERED = new AtomicInteger();

        static {
            ControllerTypes.register(ID, tag -> new Hover(tag.getDouble("height")));
            REGISTERED.incrementAndGet();
        }

        @Override
        public ResourceLocation typeId() {
            return ID;
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("height", height);
            return tag;
        }

        @Override
        public Transform resolve(Transform stored, double spaceTime) {
            return stored.withTranslation(stored.tx(), height, stored.tz());
        }
    }

    @Test
    void anotherModCanExtendComponentsAndControllers() {
        assertEquals(Shield.ID, Shield.TYPE.id());
        assertEquals(1, Hover.REGISTERED.get());
        SpaceObject object = new SpaceObject(UUID.randomUUID()).put(new Shield(7)).controller(new Hover(12.5));
        SpaceObject back = roundTrip(object);
        assertEquals(new Shield(7), back.get(Shield.TYPE));
        assertEquals(new Hover(12.5), back.controller());
        assertEquals(12.5, RailMath.local(back, 0).ty());
    }

    @Test
    void putReplacesAndRemoveDeletes() {
        SpaceObject object = new SpaceObject(UUID.randomUUID()).put(new LogicalVolume(10)).put(new LogicalVolume(20));
        assertEquals(20, object.get(ComponentTypes.LOGICAL_VOLUME).radius());
        object.remove(ComponentTypes.LOGICAL_VOLUME);
        assertNull(object.get(ComponentTypes.LOGICAL_VOLUME));
        assertFalse(object.has(ComponentTypes.LOGICAL_VOLUME));
    }

    @Test
    void destroyableMakesAnObjectDestructible() {
        SpaceObject indestructible = new SpaceObject(UUID.randomUUID());
        assertFalse(indestructible.isDestroyed());
        SpaceObject destructible = new SpaceObject(UUID.randomUUID()).put(new Destroyable(true));
        assertTrue(roundTrip(destructible).isDestroyed());
    }

    @Test
    void changesAreReportedOnceAttached() {
        AtomicInteger changes = new AtomicInteger();
        SpaceObject object = new SpaceObject(UUID.randomUUID()).put(new HostedDimension(DIM));
        object.attach(o -> changes.incrementAndGet());
        object.put(Transform.at(1, 2, 3));
        object.controller(Pilotable.AT_REST);
        object.remove(ComponentTypes.TRANSFORM);
        assertEquals(3, changes.get());
        object.put(new ExclusionVolume(5));
        assertEquals(4, changes.get());
        UUID child = UUID.randomUUID();
        object.addChild(child);
        object.addChild(child);
        assertEquals(5, changes.get(), "ajouter deux fois le même enfant ne change rien la seconde fois");
        object.removeChild(child);
        assertEquals(6, changes.get());
    }

    @Test
    void duplicateTypeRegistrationIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> ComponentTypes.register(ComponentTypes.TRANSFORM.id(), t -> Transform.ORIGIN));
        assertThrows(IllegalStateException.class,
                () -> ControllerTypes.register(ControllerTypes.PILOTABLE, t -> Pilotable.AT_REST));
    }
}
