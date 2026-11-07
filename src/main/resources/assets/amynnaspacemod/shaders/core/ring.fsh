#version 150

in vec3 Dir;

// Repère de l'anneau (dalle |y| <= épaisseur autour du plan y = 0), unité = rayon extérieur.
uniform mat3 GalRot;
uniform vec3 CamPos;
uniform vec4 ColorA;   // couleur intérieure, rgb + intensité
uniform vec4 ColorB;   // couleur extérieure
uniform vec4 Params;   // x = rayon intérieur, y = opacité, z = émissif (0/1), w = rayon de l'astre central (0 = aucun)
uniform float Thickness; // demi-épaisseur (unité = rayon extérieur)
uniform float Flare;     // passe (champ commun à tous les dessins) : 0 = entier, 1 = moitié arrière, 2 = moitié avant (par rapport au centre de l'anneau)

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

// shortcut: pas fixes dans la dalle ; de profil (vue rasante) la dalle est longue et le jitter donne un grain, à augmenter si ça se voit.
const int STEPS = 40;

void main() {
    vec3 rd = normalize(GalRot * Dir);
    vec3 ro = CamPos;
    float h = Thickness;
    // dalle |y| <= h
    float t0 = 0.0, t1 = 1e9;
    if (abs(rd.y) > 1e-6) {
        float ta = (-h - ro.y) / rd.y, tb = (h - ro.y) / rd.y;
        t0 = max(t0, min(ta, tb));
        t1 = min(t1, max(ta, tb));
    } else if (abs(ro.y) > h) {
        discard;
    }
    // cylindre r <= 1
    float a = dot(rd.xz, rd.xz), b = dot(ro.xz, rd.xz), c = dot(ro.xz, ro.xz) - 1.0;
    if (a < 1e-12) {
        if (c > 0.0) discard;
    } else {
        float d = b * b - a * c;
        if (d < 0.0) discard;
        float sq = sqrt(d);
        t0 = max(t0, (-b - sq) / a);
        t1 = min(t1, (-b + sq) / a);
    }
    // moitié arrière / avant : t est la distance le long du rayon, tc celle du point le plus proche du centre de l'anneau
    float tc = -dot(ro, rd);
    if (Flare == 1.0) t0 = max(t0, tc);
    else if (Flare == 2.0) t1 = min(t1, tc);
    // la planète cache la partie de l'anneau qui passe derrière elle
    if (Params.w > 0.0) {
        float bs = dot(ro, rd);
        float hs = bs * bs - (dot(ro, ro) - Params.w * Params.w);
        if (hs > 0.0) {
            float ts = -bs - sqrt(hs);
            if (ts > 0.0) t1 = min(t1, ts);
        }
    }
    if (t1 <= t0) discard;

    // profondeur optique d'une traversée verticale = celle de l'ancien disque 2D (-ln(1 - opacité)) ; profil vertical exp(-2 (y/h)²), d'intégrale 1,2533 h
    float tau = -log(1.0 - min(Params.y, 0.999));
    float dt = (t1 - t0) / float(STEPS);
    float t = t0 + dt * hash11(dot(gl_FragCoord.xy, vec2(12.9898, 78.233)));
    vec3 acc = vec3(0.0);
    float T = 1.0;
    for (int i = 0; i < STEPS; i++, t += dt) {
        vec3 p = ro + rd * t;
        float r = length(p.xz);
        if (r > 1.0 || r < Params.x) continue;
        float u = (r - Params.x) / (1.0 - Params.x);
        vec4 cc = mix(ColorA, ColorB, u);
        float band = 0.6 + 0.4 * noise1(u * 60.0) * (0.5 + 0.5 * noise1(u * 9.0 + 3.0)); // stries axisymétriques
        float edge = smoothstep(0.0, 0.02, u) * smoothstep(1.0, 0.98, u);
        float vert = exp(-2.0 * p.y * p.y / (h * h));
        float aStep = 1.0 - exp(-tau * band * edge * vert * dt / (1.2533 * h));
        vec3 col = Params.z > 0.5 ? cc.rgb * cc.a : cc.rgb * 0.85;
        acc += T * aStep * col;
        T *= 1.0 - aStep;
    }
    fragColor = vec4(acc, 1.0 - T); // prémultiplié
}
