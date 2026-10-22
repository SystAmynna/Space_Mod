package fr.systamynna.spacemod.client.render;

import fr.systamynna.spacemod.strategic.component.Render;
import net.minecraft.nbt.CompoundTag;

/**
 * Halo brillant d'une étoile, à empiler sur sa sphère. Paramètres : {@code color} ({@code #rrggbb}, blanc par défaut),
 * {@code brightness} (1 par défaut) et {@code glow_size} (demi-taille du halo en rad, 0.012 par défaut). La taille du halo
 * ne dépend pas du rayon réel : une étoile lointaine, plus petite qu'un pixel, reste visible.
 */
final class StarRenderObject extends RenderObject {
    private final float[] color;
    private final float brightness;
    private final float glowSize;

    StarRenderObject(Render.Layer layer) {
        CompoundTag p = layer.params();
        color = Billboard.color(p.getString("color"));
        brightness = p.contains("brightness") ? (float) p.getDouble("brightness") : 1f;
        glowSize = p.contains("glow_size") ? (float) p.getDouble("glow_size") : 0.012f;
    }

    @Override
    public void render(RenderContext ctx) {
        // une étoile proche (le Soleil) garde un halo proportionnel à son disque
        float half = Math.max(glowSize, 4 * ctx.angularRadius());
        Billboard.draw(ctx, half, 1f, 0f, color[0], color[1], color[2], brightness, ctx.angularRadius() / half,
                SkyShaders::starGlow);
    }
}
