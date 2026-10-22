package fr.systamynna.spacemod.client.render;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Ce qu'un {@link RenderObject} sait de l'objet qu'il dessine, dans le repère de la dimension (celui du ciel).
 *
 * @param view matrice de vue (rotation de la caméra, sans translation)
 * @param direction direction unitaire de l'objet
 * @param angularRadius rayon angulaire de l'objet, en radians
 * @param screenSize taille à l'écran : 1 = demi-hauteur de l'écran ; sert au LOD
 * @param bodyRotation orientation propre du corps (rotation sur lui-même)
 * @param light direction unitaire vers l'astre principal, ou {@code null} s'il n'y en a pas
 * @param partialTick fraction de tick
 */
public record RenderContext(Matrix4f view, Vector3f direction, float angularRadius, float screenSize,
        Quaternionf bodyRotation, @Nullable Vector3f light, float partialTick) {
    /** Distance fictive à laquelle les astres sont dessinés : seuls la direction et l'angle comptent. */
    public static final float SKY_DISTANCE = 50f;
}
