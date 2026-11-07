package fr.systamynna.spacemod.core.major;

/**
 * Constantes des étoiles majeures lointaines. shortcut: constantes, pas dans le fichier de config ; à brancher sur un ModConfigSpec quand il faudra les calibrer.
 */
public final class MajorParams {
    private MajorParams() {}

    /** Kilomètres dans une année-lumière (unité de distance du flux). */
    public static final double LY_KM = 9460730472580.8;
    /** Côté d'une cellule d'envoi, en années-lumière : unité de diff entre le serveur et chaque client. */
    public static final int CELL_UNITS = 64;
    public static final double CELL_KM = CELL_UNITS * LY_KM;
    /** Flux minimal {@code lum / d²} (Soleil, al) à l'affichage : le Soleil est visible à l'œil nu (magnitude ~6,5) jusqu'à ~70 al. */
    public static final double FLUX_LIMIT = 2e-4;
    /** Étoiles maximum répliquées par conteneur (les plus lumineuses). shortcut: coupure sèche, à remplacer par un niveau de détail si une galaxie dépasse. */
    public static final int MAX_STARS = 100_000;
    /** Octets visés par paquet MajorChunk (le plafond vanilla serveur → client est ~1 Mio). */
    public static final int MAX_CHUNK_BYTES = 900_000;
    /** UUID (16) + x, y, z (3 x 4) + couleur (4) + luminosité (4). */
    public static final int STAR_BYTES = 36;
    public static final int MAX_CHUNK_STARS = MAX_CHUNK_BYTES / STAR_BYTES;

    public static int cell(double km) { return (int) Math.floor(km / CELL_KM); }
}
