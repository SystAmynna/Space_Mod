package fr.systamynna.spacemod.strategic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.strategic.component.HostedDimension;
import fr.systamynna.spacemod.strategic.component.Transform;
import fr.systamynna.spacemod.strategic.component.hostedPropriety.LogicalVolume;
import fr.systamynna.spacemod.strategic.component.hostedPropriety.PortPoint;
import fr.systamynna.spacemod.strategic.component.hostedPropriety.TriggerVolume;
import net.minecraft.resources.ResourceLocation;

class DimensionArrivalTest {
    private static final double S = Math.sqrt(0.5);
    private static final ResourceLocation SKY = ResourceLocation.parse("amynnaspacemod:black");

    private static HostedDimension hosted(Transform placement, PortPoint port) {
        return new HostedDimension(null, HostedDimension.Properties.SPACE, placement, SKY, List.of(port));
    }

    private static PortPoint port(double qy, double qw) {
        return new PortPoint("p", 0, 0, 0, 10, 64, 20, 0, qy, 0, qw, 360, 0, new LogicalVolume(100), new TriggerVolume(200));
    }

    @Test
    void noOffsetArrivesOnThePortOrigin() {
        PortPoint p = port(0, 1);
        assertArrayEquals(new double[] {10, 64, 20}, DimensionManager.arrival(hosted(Transform.ORIGIN, p), p, null), 1e-9);
    }

    @Test
    void offsetWithoutRotationIsAddedAsIs() {
        PortPoint p = port(0, 1);
        assertArrayEquals(new double[] {10, 164, 20},
                DimensionManager.arrival(hosted(Transform.ORIGIN, p), p, new double[] {0, 100, 0}), 1e-9);
    }

    @Test
    void offsetIsBroughtBackIntoTheSkyFrame() {
        // ciel tourné de 90° autour de Y : le décalage (1, 0, 0) du parent est (0, 0, 1) dans la dimension
        PortPoint p = port(S, S);
        double[] at = DimensionManager.arrival(hosted(Transform.ORIGIN, p), p, new double[] {1, 0, 0});
        assertArrayEquals(new double[] {10, 64, 21}, at, 1e-9);
    }
}
