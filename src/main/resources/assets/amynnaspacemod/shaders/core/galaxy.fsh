#version 150

uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// Galaxie procédurale vue de dessus (l'inclinaison est faite par le quad) : noyau, disque, bras spiraux.
// vertexColor.rgb = couleur des bras (déjà multipliée par la luminosité) ; vertexColor.a = force des bras (0 : elliptique).
void main() {
    vec2 p = texCoord0 * 2.0 - 1.0;
    float r = length(p);
    if (r >= 1.0) discard;
    float theta = atan(p.y, p.x);
    float arms = 0.5 + 0.5 * cos(2.0 * (theta - 3.2 * log(r + 0.08)));
    float disk = exp(-r * 3.5) * mix(1.0, 0.25 + 0.75 * arms * arms, vertexColor.a);
    float bulge = exp(-r * r * 40.0) * 1.4;
    vec3 color = mix(vertexColor.rgb, vec3(1.0, 0.9, 0.7), clamp(bulge, 0.0, 1.0)) ;
    float a = (disk * 0.8 + bulge) * smoothstep(1.0, 0.7, r);
    fragColor = vec4(color * a, a) * ColorModulator;
}
