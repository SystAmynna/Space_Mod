#version 150

in vec3 Dir;

// Repère de l'anneau (plan y = 0), unité = rayon extérieur.
uniform mat3 GalRot;
uniform vec3 CamPos;
uniform vec4 ColorA;   // couleur intérieure, rgb + intensité
uniform vec4 ColorB;   // couleur extérieure
uniform vec4 Params;   // x = rayon intérieur, y = opacité, z = émissif (0/1), w = rayon de l'astre central (0 = aucun)

out vec4 fragColor;

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    return fract(p * (p + p));
}

float noise1(float x) {
    float i = floor(x);
    float f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(hash11(i), hash11(i + 1.0), f);
}

void main() {
    vec3 rd = normalize(GalRot * Dir);
    vec3 ro = CamPos;
    if (abs(rd.y) < 1e-6) discard;
    float t = -ro.y / rd.y;
    if (t <= 0.0) discard;
    vec3 p = ro + rd * t;
    float r = length(p.xz);
    if (r > 1.0 || r < Params.x) discard;
    // la planète cache la partie de l'anneau qui passe derrière elle
    if (Params.w > 0.0) {
        float b = dot(ro, rd);
        float h = b * b - (dot(ro, ro) - Params.w * Params.w);
        if (h > 0.0) {
            float ts = -b - sqrt(h);
            if (ts > 0.0 && ts < t) discard;
        }
    }
    float u = (r - Params.x) / (1.0 - Params.x);
    vec4 cc = mix(ColorA, ColorB, u);
    float band = 0.6 + 0.4 * noise1(u * 60.0) * (0.5 + 0.5 * noise1(u * 9.0 + 3.0)); // stries axisymétriques
    float edge = smoothstep(0.0, 0.02, u) * smoothstep(1.0, 0.98, u);
    float a = Params.y * band * edge;
    vec3 col = Params.z > 0.5 ? cc.rgb * cc.a : cc.rgb * 0.85;
    fragColor = vec4(col * a, a);
}
