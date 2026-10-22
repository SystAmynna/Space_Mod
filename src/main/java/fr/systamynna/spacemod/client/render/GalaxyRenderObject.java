package fr.systamynna.spacemod.client.render;

import fr.systamynna.spacemod.strategic.component.Render;
import net.minecraft.nbt.CompoundTag;

/**
 * Galaxie lointaine (shader procédural), à la taille angulaire de son rayon. Paramètres : {@code color} ({@code #rrggbb}),
 * {@code brightness} (1), {@code arms} (force des bras spiraux, 0 à 1 ; 0 = elliptique), {@code inclination} (rad, 0 =
 * vue de face) et {@code rotation} (rad, orientation sur le ciel).
 */
final class GalaxyRenderObject extends RenderObject {
    private final float[] color;
    private final float arms;
    private final float squash;
    private final float rotation;

    GalaxyRenderObject(Render.Layer layer) {
        CompoundTag p = layer.params();
        float brightness = p.contains("brightness") ? (float) p.getDouble("brightness") : 1f;
        float[] c = Billboard.color(p.getString("color"));
        color = new float[] { c[0] * brightness, c[1] * brightness, c[2] * brightness };
        arms = (float) p.getDouble("arms");
        squash = (float) Math.max(0.05, Math.cos(p.getDouble("inclination")));
        rotation = (float) p.getDouble("rotation");
    }

    @Override
    public void render(RenderContext ctx) {
        Billboard.draw(ctx, ctx.angularRadius(), squash, rotation, color[0], color[1], color[2], arms, SkyShaders::galaxy);
    }
}
