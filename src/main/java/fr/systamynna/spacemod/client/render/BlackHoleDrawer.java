package fr.systamynna.spacemod.client.render;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.core.render.BlackHoleRender;
import fr.systamynna.spacemod.core.render.SpaceRenderObject;
import net.minecraft.client.renderer.ShaderInstance;

/** Sphère monochrome d'un trou noir, au-dessus de sa lentille ({@link DistorsionDrawer}) à position égale. */
final class BlackHoleDrawer extends EffectDrawer {
    @Override
    @Nullable
    ShaderInstance shader() { return ClientShaders.blackHole; }

    @Override
    public int priority() { return 2; }

    @Override
    void uniforms(ShaderInstance sh, SpaceRenderObject ro) { color(sh, "ColorA", ((BlackHoleRender) ro).color); }
}
