package fr.systamynna.spacemod.core.component;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.component.components.ArtificialStructure;
import fr.systamynna.spacemod.core.component.components.StellarSystem;
import net.minecraft.nbt.CompoundTag;

class StellarSystemTest {
    @Test
    void isARegisteredTagWithoutData() {
        assertTrue(SpaceComponents.ids().contains(StellarSystem.ID));
        assertEquals(StellarSystem.class, SpaceComponents.type(StellarSystem.ID));
        SpaceComponent c = SpaceComponents.create(StellarSystem.ID);
        assertInstanceOf(StellarSystem.class, c);
        assertTrue(c.save().isEmpty());
        assertInstanceOf(StellarSystem.class, SpaceComponents.load(StellarSystem.ID, new CompoundTag()));
    }

    @Test
    void artificialStructureIsAnArgumentlessTag() {
        assertTrue(SpaceComponents.ids().contains(ArtificialStructure.ID));
        SpaceComponent c = SpaceComponents.create(ArtificialStructure.ID);
        assertInstanceOf(ArtificialStructure.class, c);
        assertTrue(c.save().isEmpty());
        assertInstanceOf(ArtificialStructure.class, SpaceComponents.load(ArtificialStructure.ID, new CompoundTag()));
        c.edit("");
        assertThrows(SpaceObjectException.class, () -> c.edit("x"));
    }

    @Test
    void refusesArguments() {
        SpaceComponent c = new StellarSystem();
        c.edit("");
        assertThrows(SpaceObjectException.class, () -> c.edit("x"));
        assertTrue(c.suggest("").isEmpty());
    }
}
