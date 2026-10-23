package fr.systamynna.spacemod.strategic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.strategic.component.ComponentTypes;
import fr.systamynna.spacemod.strategic.component.ExclusionShape;
import fr.systamynna.spacemod.strategic.component.ExclusionVolume;
import net.minecraft.nbt.CompoundTag;

/** Forme et portée d'un volume d'exclusion, et lecture de l'ancien format {@code {radius}}. */
class ExclusionVolumeTest {
    @Test
    void oldFormatReadsAsLargeOnlySphere() {
        CompoundTag old = new CompoundTag();
        old.putDouble("radius", 10);
        ExclusionVolume volume = ComponentTypes.EXCLUSION_VOLUME.read(old);
        assertEquals(new ExclusionVolume(10), volume);
        assertEquals(ExclusionVolume.Scope.LARGE_ONLY, volume.scope());
    }

    @Test
    void roundTripKeepsShapeAndScope() {
        ExclusionVolume volume = new ExclusionVolume(new ExclusionShape.Sphere(42), ExclusionVolume.Scope.ALL);
        SpaceObject object = new SpaceObject(UUID.randomUUID()).put(volume);
        assertEquals(volume, SpaceObject.load(object.save()).get(ComponentTypes.EXCLUSION_VOLUME));
        assertEquals(42, volume.radius());
    }
}
