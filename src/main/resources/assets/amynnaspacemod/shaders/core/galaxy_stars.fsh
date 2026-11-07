#version 150

in vec3 Dir;

// Repère de la galaxie, unité = rayon de la galaxie.
uniform mat3 GalRot;   // repère de l'observateur -> repère de la galaxie
uniform vec3 CamPos;   // l'observateur dans le repère de la galaxie
uniform vec2 Seed;     // deux valeurs de [0, 1[ dérivées de l'UUID de la galaxie
uniform vec2 Params;   // x = demi-épaisseur du disque (fraction du rayon), y = nombre de bras
uniform float Stars;   // multiplicateur du nombre d'étoiles

out vec4 fragColor;

const float GRID_IN = 230.0;  // vue de l'intérieur : cellules par radian de direction
const float GRID_OUT = 70.0;  // vue de l'extérieur : cellules par rayon de galaxie
const float PMAX = 0.5;       // probabilité maximale qu'une cellule porte une étoile
const float K = 0.9;          // étoiles par unité de colonne de matière

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

// Facteur radial et bras du disque en (x, z), sans bruit : même forme que le nuage (galaxy_cloud.fsh), la moyenne du bruit y valant ~0,85.
float radArm(vec2 xz) {
    float r = length(xz);
    float theta = atan(xz.y, xz.x);
    float spiral = 0.5 + 0.5 * cos(Params.y * (theta - 2.2 * log(r + 0.04)) + Seed.x * 6.2831853);
    float arm = spiral * spiral * spiral;
    return exp(-r * 3.0) * smoothstep(1.0, 0.85, r) * (0.25 + 1.6 * arm * 0.85);
}

float bulge(vec3 p) {
    return exp(-length(vec3(p.x, p.y * 2.5, p.z)) * 9.0) * 4.0;
}

// Primitive de exp(-|y| / h) : sert à intégrer le profil vertical le long d'un rayon incliné, sans échantillonnage.
float vprim(float y, float h) {
    return h * sign(y) * (1.0 - exp(-abs(y) / h));
}

// Colonne de matière, estimée sans bruit, le long de la direction dc depuis CamPos (observateur dans la galaxie).
float columnIn(vec3 dc) {
    float h = Params.x;
    float b = dot(CamPos, dc);
    float L = -b + sqrt(max(b * b - (dot(CamPos, CamPos) - 1.0), 0.0)); // distance jusqu'au bord de la galaxie
    float dy = dc.y;
    float vert, ts;
    if (abs(dy) > 1e-4) {
        vert = abs(vprim(CamPos.y + dy * L, h) - vprim(CamPos.y, h)) / abs(dy);
        ts = clamp(-CamPos.y / dy, 0.0, L);
    } else {
        vert = L * exp(-abs(CamPos.y) / h);
        ts = 0.5 * L;
    }
    vec3 ps = CamPos + dc * ts; // point du rayon le plus proche du plan galactique
    vec3 pb = CamPos + dc * clamp(-b, 0.0, L); // point le plus proche du centre
    return vert * radArm(ps.xz) + 0.15 * bulge(pb);
}

// Même chose pour une étoile placée en p (repère de la galaxie) vue de l'extérieur : densité du disque à sa hauteur, plus le bulbe.
float columnOut(vec3 p) {
    return 2.0 * Params.x * radArm(p.xz) * exp(-abs(p.y) / Params.x) + 0.15 * bulge(p);
}

void main() {
    vec3 rd = normalize(GalRot * Dir);
    vec3 ro = CamPos;
    float b = dot(ro, rd);
    float h = b * b - (dot(ro, ro) - 1.0);
    if (h < 0.0) discard;
    float sq = sqrt(h);
    float t0 = max(-b - sq, 0.0);
    float t1 = -b + sq;
    if (t1 <= 0.0) discard;

    // Grille de cellules. Vue de l'intérieur : sur la direction (points fixes à l'écran). Vue de l'extérieur : en 3D, autour du point du rayon le plus
    // proche du plan galactique (suit la galaxie).
    bool inside = dot(ro, ro) < 1.0;
    vec3 q = inside ? rd * GRID_IN
                    : (ro + rd * (abs(rd.y) > 1e-5 ? clamp(-ro.y / rd.y, t0, t1) : 0.5 * (t0 + t1))) * GRID_OUT;
    vec3 base = floor(q - 0.5);
    vec3 col = vec3(0.0);
    // 8 cellules les plus proches du point : une étoile qui déborde de sa cellule se prolonge dans la voisine (pas de coupure en carré)
    for (int i = 0; i < 8; i++) {
        vec3 ic = base + vec3(float(i & 1), float((i >> 1) & 1), float((i >> 2) & 1)); // cellule entière
        vec3 cell = ic + vec3(Seed, Seed.x) * 97.0;                                    // clé de hachage (graine de la galaxie)
        float h0 = hash13(cell);
        if (h0 >= PMAX) continue;
        vec3 c = ic + 0.5 + (hash33(cell) - 0.5) * 0.6; // centre de l'étoile
        // L'existence de l'étoile ne dépend que de sa cellule (jamais du pixel) : tous les pixels qu'elle couvre décident pareil.
        float prob = clamp(Stars * K * (inside ? columnIn(normalize(c)) : columnOut(c / GRID_OUT)), 0.0, PMAX);
        if (h0 >= prob) continue;
        vec3 d = q - c;
        float s = exp(-dot(d, d) * 12.0) * (0.4 + hash13(cell + 7.0));
        col += mix(vec3(1.0, 0.82, 0.62), vec3(0.72, 0.82, 1.0), hash13(cell + 3.0)) * s * 1.5;
    }
    if (col == vec3(0.0)) discard;
    col = 1.0 - exp(-col * 1.6);
    fragColor = vec4(col, 0.0); // prémultiplié, alpha nul : purement additif (mélange ONE, ONE_MINUS_SRC_ALPHA)
}
