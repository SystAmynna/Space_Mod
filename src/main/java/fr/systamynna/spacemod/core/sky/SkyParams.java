package fr.systamynna.spacemod.core.sky;

/**
 * Constantes du ciel lointain. shortcut: constantes, pas dans le fichier de config ; à brancher sur un ModConfigSpec quand il faudra les calibrer.
 */
public final class SkyParams {
    private SkyParams() {}

    /** Kilomètres dans une année-lumière (unité de distance du flux). */
    public static final double LY_KM = 9460730472580.8;
    /** Côté d'une cellule de l'index spatial, en années-lumière. */
    public static final int SKY_CELL_UNITS = 64;
    public static final double CELL_KM = SKY_CELL_UNITS * LY_KM;
    /** Flux minimal {@code lum / d²} (Soleil, al) : le Soleil est visible à l'œil nu (magnitude ~6,5) jusqu'à ~70 al. */
    public static final double FLUX_LIMIT = 2e-4;
    /** Plus forte luminosité prévue (supergéantes) ; borne le rayon de recherche à {@code sqrt(LUM_MAX / FLUX_LIMIT)} al. */
    public static final double LUM_MAX = 1e5;
    /** Octets visés par paquet SkyChunk (le plafond vanilla serveur → client est ~1 Mio). */
    public static final int MAX_CHUNK_BYTES = 900_000;
    /** UUID (16) + x, y, z (3 x 4) + couleur (4) + luminosité (4). */
    public static final int STAR_BYTES = 36;
    public static final int MAX_CHUNK_STARS = MAX_CHUNK_BYTES / STAR_BYTES;

    public static int cell(double km) { return (int) Math.floor(km / CELL_KM); }

    /** Vrai si une luminosité {@code lum} vue à {@code d2} km² atteint {@link #FLUX_LIMIT} (confondu avec l'observateur : vrai). */
    public static boolean visible(double lum, double d2) { return d2 <= 0 || lum * LY_KM * LY_KM / d2 >= FLUX_LIMIT; }
}
