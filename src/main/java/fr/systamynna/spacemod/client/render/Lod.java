package fr.systamynna.spacemod.client.render;

/** Choix d'un niveau de détail par la taille à l'écran, avec hystérésis (montée et descente à des seuils différents). */
public final class Lod {
    /** Une descente exige une taille sous ce facteur du seuil de montée, pour éviter le va-et-vient. */
    public static final float DOWN = 0.8f;

    /** Classe utilitaire. */
    private Lod() {}

    /**
     * Niveau suivant : au plus un pas par appel.
     *
     * @param level niveau courant
     * @param size taille à l'écran (1 = demi-hauteur de l'écran)
     * @param up {@code up[n]} = taille au-dessus de laquelle on passe du niveau n au niveau n+1
     */
    public static int step(int level, float size, float[] up) {
        if (level < up.length && size > up[level]) return level + 1;
        if (level > 0 && size < up[level - 1] * DOWN) return level - 1;
        return level;
    }
}
