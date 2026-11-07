#version 150

in vec3 Dir;

// Repère de la galaxie, unité = rayon de la galaxie.
uniform mat3 GalRot;   // repère de l'observateur -> repère de la galaxie
uniform vec3 CamPos;   // l'observateur dans le repère de la galaxie
uniform vec2 Seed;     // deux valeurs de [0, 1[ dérivées de l'UUID de la galaxie (phase des bras, bruit)
uniform vec2 Params;   // x = demi-épaisseur du disque (fraction du rayon), y = nombre de bras
uniform vec4 ColorCore; // couleur du centre (rgb) et intensité (a)
uniform vec4 ColorEdge; // couleur du bord (rgb) et intensité (a)

out vec4 fragColor;

const int STEPS = 48;
const float SLAB = 0.45;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float vnoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash13(i), hash13(i + vec3(1, 0, 0)), f.x),
            mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y),
        mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x),
            mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y),
        f.z);
}

// Densité de matière en p : disque spiral mince (profil vertical exponentiel) et bulbe central.
float density(vec3 p) {
    float r = length(p.xz);
    float bulge = exp(-length(vec3(p.x, p.y * 2.5, p.z)) * 9.0) * 4.0;
    float disc = exp(-abs(p.y) / Params.x) * exp(-r * 3.0) * smoothstep(1.0, 0.85, r);
    if (disc < 0.004) return bulge; // hors du disque : pas de bruit à évaluer
    float theta = atan(p.z, p.x);
    float spiral = 0.5 + 0.5 * cos(Params.y * (theta - 2.2 * log(r + 0.04)) + Seed.x * 6.2831853);
    float arm = spiral * spiral * spiral;
    float n = 0.65 * vnoise(p * 7.0 + vec3(Seed * 37.0, Seed.x + Seed.y)) + 0.35 * vnoise(p * 19.0 + Seed.yxy * 53.0);
    return bulge + disc * (0.25 + 1.6 * arm * (0.35 + n));
}

void main() {
    vec3 rd = normalize(GalRot * Dir);
    vec3 ro = CamPos;
    // intersection avec la sphère englobante (rayon 1)
    float b = dot(ro, rd);
    float h = b * b - (dot(ro, ro) - 1.0);
    if (h < 0.0) discard;
    float sq = sqrt(h);
    float t0 = max(-b - sq, 0.0);
    float t1 = -b + sq;
    if (t1 <= 0.0) discard;
    // tranche utile autour du plan galactique (disque + bulbe) : pas plus fin que l'épaisseur du disque, donc une colonne de matière peu bruitée
    // shortcut: tranche fixe à SLAB ; à relier à Params.x si les disques deviennent beaucoup plus épais ou minces
    if (abs(rd.y) > 1e-5) {
        float ta = (-SLAB - ro.y) / rd.y, tb = (SLAB - ro.y) / rd.y;
        t0 = max(t0, min(ta, tb));
        t1 = min(t1, max(ta, tb));
    } else if (abs(ro.y) > SLAB) {
        discard;
    }
    if (t1 <= t0) discard;

    float dt = (t1 - t0) / float(STEPS);
    float t = t0 + dt * hash13(vec3(gl_FragCoord.xy, 1.7));
    vec3 col = vec3(0.0);
    float T = 1.0;
    for (int i = 0; i < STEPS; i++) {
        vec3 p = ro + rd * t;
        float d = density(p);
        if (d > 0.0) {
            float r = length(p.xz);
            vec3 tint = mix(ColorCore.rgb * ColorCore.a, ColorEdge.rgb * ColorEdge.a, smoothstep(0.0, 0.6, r));
            col += T * tint * d * dt * 5.0;
            T *= exp(-d * dt * 2.0);
        }
        t += dt;
    }

    col = 1.0 - exp(-col * 0.95); // fond du ciel volontairement discret (était 1.6)
    fragColor = vec4(col, clamp(1.0 - T, 0.0, 1.0)); // prémultiplié : mélange ONE, ONE_MINUS_SRC_ALPHA
}
