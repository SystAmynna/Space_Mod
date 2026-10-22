package fr.systamynna.spacemod.client.render;

/**
 * Une couche de rendu d'un objet du ciel. Les couches d'un composant {@code Render} sont instanciées une fois par objet
 * (voir {@link RenderObjects}) et dessinées dans l'ordre à chaque image ; l'instance garde son état de LOD. Les
 * sous-classes futures (anneaux, halos, trous noirs, mégastructures) ne redéfinissent que {@link #render}.
 */
public abstract class RenderObject {
    private int lodLevel;

    /** Dessine la couche. L'état GL (projection, profondeur, shader de base) est préparé par {@code SkyRenderer}. */
    public abstract void render(RenderContext ctx);

    /** Niveau de détail pour cette taille à l'écran, avec hystérésis ; voir {@link Lod#step}. */
    protected final int lod(float screenSize, float[] up) {
        lodLevel = Lod.step(lodLevel, screenSize, up);
        return lodLevel;
    }
}
