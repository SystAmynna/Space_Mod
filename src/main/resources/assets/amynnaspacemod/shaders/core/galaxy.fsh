#version 150

in vec3 Dir;

// Repère de la galaxie, unité = rayon de la galaxie.
uniform mat3 GalRot;   // repère de l'observateur -> repère de la galaxie
uniform vec3 CamPos;   // l'observateur dans le repère de la galaxie
uniform vec2 Seed;     // deux valeurs de [0, 1[ dérivées de l'UUID de la galaxie
uniform vec2 Params;   // x = demi-épaisseur du disque (fraction du rayon), y = nombre de bras

out vec4 fragColor;

const int STEPS = 48;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
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

    float dt = (t1 - t0) / float(STEPS);
    float t = t0 + dt * hash13(vec3(gl_FragCoord.xy, 1.7));
    vec3 col = vec3(0.0);
    float T = 1.0;
    float wsum = 0.0;
    vec3 pm = vec3(0.0);
    for (int i = 0; i < STEPS; i++) {
        vec3 p = ro + rd * t;
        float d = density(p);
        if (d > 0.0) {
            float r = length(p.xz);
            vec3 tint = mix(vec3(1.0, 0.72, 0.42), vec3(0.50, 0.62, 1.0), smoothstep(0.0, 0.6, r));
            col += T * tint * d * dt * 5.0;
            T *= exp(-d * dt * 2.0);
            wsum += d;
            pm += d * p;
        }
        t += dt;
    }

    // Étoiles aléatoires (graine = UUID) : plus nombreuses là où la colonne de matière est dense.
    // Vue de l'intérieur, la grille est sur la direction (points fixes à l'écran) ; vue de l'extérieur, sur le point moyen traversé (suit la galaxie).
    float column = wsum * dt;
    vec3 q = dot(ro, ro) < 1.0 ? rd * 230.0 : (wsum > 0.0 ? pm / wsum : rd) * 70.0;
    vec3 cell = floor(q) + vec3(Seed, Seed.x) * 97.0;
    vec3 fp = fract(q) - 0.5;
    if (hash13(cell) < clamp(column * 0.9, 0.0, 0.5)) {
        vec3 jit = hash33(cell) - 0.5;
        float dd = length(fp - jit * 0.6);
        float s = exp(-dd * dd * 12.0) * (0.4 + hash13(cell + 7.0));
        col += mix(vec3(1.0, 0.82, 0.62), vec3(0.72, 0.82, 1.0), hash13(cell + 3.0)) * s * 1.5;
    }

    col = 1.0 - exp(-col * 1.6);
    fragColor = vec4(col, clamp(1.0 - T, 0.0, 1.0)); // prémultiplié : mélange ONE, ONE_MINUS_SRC_ALPHA
}
