package fr.systamynna.spacemod.core.relevance;

import java.util.UUID;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.core.render.RenderConfig;

/** Position d'un joueur dans l'arbre : dans le référentiel de {@code anchor}, décalé de (x, y, z) km. */
public record PlayerSpacePos(UUID anchor, double x, double y, double z) {
    /** Position donnée par la config de ciel de la dimension du joueur ; {@code null} sans config. */
    @Nullable
    public static PlayerSpacePos of(@Nullable RenderConfig c) {
        return c == null ? null : new PlayerSpacePos(c.object, c.pos[0], c.pos[1], c.pos[2]);
    }

    /** Le parent du joueur, s'il en a un, est sa position (décalage nul) ; sinon repli sur {@link #of(RenderConfig)}. */
    @Nullable
    public static PlayerSpacePos of(@Nullable UUID parent, @Nullable RenderConfig c) {
        return parent == null ? of(c) : new PlayerSpacePos(parent, 0, 0, 0);
    }
}
