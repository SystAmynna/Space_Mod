#version 150

// Passe de brillance d'une face de la cubemap Bright (au quart de la résolution du ciel capturé) : somme de ce qui dépasse THRESHOLD sur les 4×4 texels
// du ciel couverts (lectures alignées sur leurs centres : une étoile d'un texel n'est pas diluée avant le seuil). Le fond discret, sous le seuil,
// ne compte pas : seuls les objets lumineux font un halo en fin de plongée (voir distorsion.fsh).
uniform samplerCube Sky; // ciel capturé (unité 3)
uniform vec3 FaceN;      // axe de la face
uniform vec3 FaceU;      // axe s de la face (gl_FragCoord.x)
uniform vec3 FaceV;      // axe t de la face (gl_FragCoord.y)
uniform float Texel;     // côté d'un texel du ciel dans le plan de la face : 2 / taille

out vec4 fragColor;

const float THRESHOLD = 0.35;

void main() {
    vec2 c = gl_FragCoord.xy * (4.0 * Texel) - 1.0; // centre du texel Bright dans le plan de la face, [-1, 1]
    vec3 sum = vec3(0.0);
    for (int a = 0; a < 4; a++)
        for (int b = 0; b < 4; b++) {
            vec2 uv = c + (vec2(a, b) - 1.5) * Texel;
            sum += max(textureLod(Sky, FaceN + uv.x * FaceU + uv.y * FaceV, 0.0).rgb - THRESHOLD, 0.0);
        }
    fragColor = vec4(sum, 1.0);
}
