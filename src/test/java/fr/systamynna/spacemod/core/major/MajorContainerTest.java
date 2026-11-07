package fr.systamynna.spacemod.core.major;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.StrategicLayer;
import fr.systamynna.spacemod.core.component.components.MajorContainer;
import fr.systamynna.spacemod.core.component.components.MajorContainer.Entry;
import fr.systamynna.spacemod.core.component.components.MajorObject;
import fr.systamynna.spacemod.core.component.components.MinorObject;
import fr.systamynna.spacemod.core.store.JdbcSpaceStore;
import net.minecraft.nbt.CompoundTag;

class MajorContainerTest {
    static final double LY = MajorParams.LY_KM;

    private StrategicLayer layer() throws Exception {
        StrategicLayer l = new StrategicLayer();
        l.start(new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:")));
        return l;
    }

    @Test
    void collectsEveryMajorObjectInTheContainerFrameWithoutDescendingUnderThem() throws Exception {
        StrategicLayer l = layer();
        SpaceObject gal = l.add("gal", l.tree().resolve("universe"));
        l.setScale(gal.getUuid(), LY);
        l.addComponent(gal.getUuid(), new MajorContainer());
        // a : directement dans la galaxie ; arm/b : dans un sous-objet décalé (sans MajorObject) ; planet : sous une étoile (ignorée)
        SpaceObject a = l.add("a", gal), arm = l.add("arm", gal), b = l.add("b", arm), planet = l.add("planet", a);
        for (SpaceObject o : List.of(a, arm, b)) l.setScale(o.getUuid(), LY);
        l.editTransform(a.getUuid(), t -> { t.x = 1; t.y = 2; t.z = 3; });
        l.editTransform(arm.getUuid(), t -> t.x = 10);
        l.editTransform(b.getUuid(), t -> { t.x = 1; t.y = 1; });
        MajorObject ma = new MajorObject(), mb = new MajorObject(), mp = new MajorObject();
        ma.luminosity = 2;
        mb.color = 0x112233;
        l.addComponent(a.getUuid(), ma);
        l.addComponent(b.getUuid(), mb);
        l.addComponent(planet.getUuid(), mp);
        l.addComponent(planet.getUuid(), new MinorObject());

        Map<java.util.UUID, Entry> got = MajorContainer.collect(l.tree(), gal).stream().collect(Collectors.toMap(Entry::uuid, Function.identity()));
        assertEquals(2, got.size());
        Entry ea = got.get(a.getUuid()), eb = got.get(b.getUuid());
        assertEquals(1 * LY, ea.x(), LY * 1e-9);
        assertEquals(2 * LY, ea.y(), LY * 1e-9);
        assertEquals(3 * LY, ea.z(), LY * 1e-9);
        assertEquals(2f, ea.lum());
        assertEquals(11 * LY, eb.x(), LY * 1e-9); // b (x = 1) décalé par arm (x = 10)
        assertEquals(1 * LY, eb.y(), LY * 1e-9);
        assertEquals(0x112233, eb.color());
        assertFalse(got.containsKey(planet.getUuid()));
    }

    @Test
    void markersAndMajorObjectLoadDefensively() {
        assertTrue(new MajorContainer().save().isEmpty());
        assertTrue(new MinorObject().save().isEmpty());
        assertThrows(RuntimeException.class, () -> new MinorObject().edit("x"));
        CompoundTag bad = new CompoundTag();
        bad.putDouble("lum", Double.NaN);
        assertEquals(1, MajorObject.load(bad).luminosity);
        MajorObject m = new MajorObject();
        m.color = 0xABCDEF;
        m.luminosity = 3.5;
        MajorObject back = MajorObject.load(m.save());
        assertEquals(0xABCDEF, back.color);
        assertEquals(3.5, back.luminosity);
    }
}
