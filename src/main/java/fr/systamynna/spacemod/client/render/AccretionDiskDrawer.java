package fr.systamynna.spacemod.client.render;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.core.clock.SpaceClock;
import fr.systamynna.spacemod.core.render.AccretionDiskRender;
import fr.systamynna.spacemod.core.render.SpaceRenderObject;
import net.minecraft.client.renderer.ShaderInstance;

/** Disque d'accrétion procédural, unité du shader = rayon extérieur (voir {@code shaders/core/accretion_disk.fsh}). */
final class AccretionDiskDrawer extends EffectDrawer {
    @Override
    @Nullable
    ShaderInstance shader() { return ClientShaders.accretionDisk; }

    /** Comme l'anneau : après l'astre central et ses couches. */
    @Override
    public int priority() { return 3; }

    @Override
    void uniforms(ShaderInstance sh, SpaceRenderObject ro) {
        AccretionDiskRender d = (AccretionDiskRender) ro;
        color(sh, "ColorA", d.colorInner);
        color(sh, "ColorB", d.colorOuter);
        sh.safeGetUniform("Params").set((float) (d.inner / d.radius()), (float) d.opacity, (float) (d.thickness / d.radius()));
        // angle parcouru au bord (rad), en double sur l'horloge spatiale ; deux couches décalées d'une demi-période : phase [0, 1) + numéro de cycle (graine)
        double c = SpaceClock.CLIENT.now() / 1000 * d.speed, b = c + 0.5;
        sh.safeGetUniform("Spin").set((float) (c - Math.floor(c)), (float) Math.floorMod((long) Math.floor(c), 256), (float) (b - Math.floor(b)), (float) Math.floorMod((long) Math.floor(b), 256));
        // la passe (arrière / avant) arrive par l'uniforme Flare (voir SpaceRenderer)
    }
}
