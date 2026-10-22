#version 150

uniform vec4 ColorModulator;
uniform float DiscRadius;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// Halo d'étoile : cœur blanc surexposé, halo coloré, rayons en croix. vertexColor.a = intensité, intensité constante.
// DiscRadius : fraction du quad occupée par le disque de l'étoile (0 pour une étoile ponctuelle) ; le disque est saturé.
void main() {
    vec2 p = texCoord0 * 2.0 - 1.0;
    float r = length(p);
    if (r >= 1.0) discard;
    float outside = max(r - DiscRadius, 0.0) / max(1.0 - DiscRadius, 0.05);
    float disc = 1.0 - smoothstep(DiscRadius * 0.8, DiscRadius, r);
    float core = exp(-outside * outside * 220.0) * 3.0;
    float halo = pow(1.0 - outside, 3.0) * 0.55 + exp(-outside * 9.0) * 0.5;
    float rays = (exp(-abs(p.x) * 70.0) * exp(-abs(p.y) * 3.5) + exp(-abs(p.y) * 70.0) * exp(-abs(p.x) * 3.5)) * 0.8;
    float a = (core + halo + rays) * vertexColor.a * (1.0 - r * r);
    a = max(a, disc * 3.0 * vertexColor.a);
    vec3 color = mix(vertexColor.rgb, vec3(1.0), clamp(max(core, disc * 3.0) * 0.5, 0.0, 1.0));
    fragColor = vec4(color * a, a) * ColorModulator;
}
