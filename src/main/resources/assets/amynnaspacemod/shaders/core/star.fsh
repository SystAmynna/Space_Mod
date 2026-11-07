#version 150

in vec3 Dir;

// Repère de l'étoile, unité = rayon de l'étoile.
// De loin (rayon angulaire < NEAR_START) : disque plat éblouissant, bloom doux et fines stries statiques ; de près (> NEAR_END) : plasma émissif ; fondu entre les deux.
uniform mat3 GalRot;
uniform vec3 CamPos;
uniform vec2 Seed;     // dérivé de l'UUID de l'étoile (phase du scintillement)
uniform float Time;
uniform float Flare;   // intensité du flare dans [0, 1] (0 hors système stellaire, au centre et au-delà du double de la distance de l'objet le plus éloigné) : cœur, bloom, portée, halo, stries, taille
uniform vec4 ColorA;   // zones chaudes (granules), rgb + intensité
uniform vec4 ColorB;   // zones froides
uniform vec3 Params;   // x = vitesse du plasma, y = granulation (0 = surface lisse), z = intensité émissive du plasma

out vec4 fragColor;

// shortcut: seuils (rayon angulaire, rad) communs à toutes les étoiles ; à passer en paramètre de `star` pour un réglage par étoile.
const float NEAR_START = 0.03;
const float NEAR_END = 0.10;
// Renforcement de l'étoile du système de l'observateur, entièrement piloté par Flare (0..1).
const float LOCAL_REACH = 1.7;
const float LOCAL_GLOW = 1.2;
const float LOCAL_HALO = 0.35;
const float LOCAL_CORE = 3.0;
const float LOCAL_RAYS = 0.9;
// Taille apparente du rayonnement de l'étoile locale : ANGLE_PEAK (rad) au maximum du flare (Flare = 1), proportionnelle à Flare (parabole calculée côté Java
// selon la distance à l'étoile et à l'objet le plus éloigné de son système). Sans flare, le rayonnement garde sa taille en rayons stellaires.
const float ANGLE_PEAK = 0.04;
// Base « étoile lointaine » toujours présente, en taille angulaire (indépendante de la distance), même profil que le sprite des étoiles majeures : à Flare = 0
// (hors système, au centre, au-delà du double de la distance de l'objet le plus éloigné) l'étoile ressemble aux autres.
const float STAR_BASE_ANGLE = 0.012;
const float STAR_BASE_GAIN = 1.2;
// Portée angulaire maximale du rayonnement depuis la direction de l'étoile : π/3 = 1/3 des 180° qu'il faudrait pour atteindre le point opposé du ciel.
const float MAX_ANGLE = 1.0471976;

// Tonalité qui garde la teinte : une forte intensité éclaircit la couleur sans la blanchir (le canal par canal saturait tout vers le blanc).
vec3 tonemap(vec3 c) {
    float m = max(c.r, max(c.g, c.b));
    return m < 1e-4 ? vec3(0.0) : c / m * (1.0 - exp(-m * 1.4));
}

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

// Distance au centre de bulle le plus proche (cellules de Worley dont les centres oscillent avec t) : les granules du plasma.
float bubbles(vec3 p, float t) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    float d = 8.0;
    for (int x = -1; x <= 1; x++)
        for (int y = -1; y <= 1; y++)
            for (int z = -1; z <= 1; z++) {
                vec3 g = vec3(x, y, z);
                vec3 o = 0.5 + 0.5 * sin(t + 6.2831853 * hash33(i + g));
                d = min(d, length(g + o - f));
            }
    return d;
}

void main() {
    vec3 rd = normalize(GalRot * Dir);
    vec3 ro = CamPos;
    vec3 hot = ColorA.rgb * ColorA.a;
    vec3 cool = ColorB.rgb * ColorB.a;
    float dist = length(ro);
    float b = dot(ro, rd);
    float c = dist * dist - 1.0;
    if (c < 0.0) { // observateur dans l'étoile
        fragColor = vec4(tonemap(hot), 1.0);
        return;
    }
    // 0 = lueur (loin), 1 = plasma (près), selon la taille apparente de l'étoile
    float near = smoothstep(NEAR_START, NEAR_END, asin(1.0 / dist));
    float far = 1.0 - near;
    // distance de passage du rayon au centre (en rayons) : < 1 = le rayon traverse le disque
    float dmin = length(ro + rd * max(-b, 0.0));
    float d1 = max(dmin - 1.0, 0.0);
    float sl = max(1.0, ANGLE_PEAK * Flare * dist); // échelle du rayonnement en rayons stellaires (1 sans flare)
    float u = d1 / sl;
    float reach = mix(30.0, 14.0, near) * (1.0 + LOCAL_REACH * Flare) * sl;
    // angle entre le rayon et la direction de l'étoile (0 = vers l'étoile, π = opposé) et portée angulaire bornée (sans borne, une lueur uniforme couvrait l'hémisphère opposé)
    vec3 cdir = -ro / dist;
    float ang = atan(length(cross(rd, cdir)), dot(rd, cdir));
    float reachAng = min(asin(min(reach / dist, 1.0)), MAX_ANGLE);
    // la sphère n'existe que de près (de loin elle est transparente : seule la lueur reste, sans écart de couleur à raccorder) ; bord anti-aliasé
    float dw = (1.0 - smoothstep(0.97, 1.0, dmin)) * near;
    // bloom : décroît avec la distance au centre (étiré par sl) ; plus large de loin
    float g = (0.5 * exp(-u * 3.0) + 0.2 * exp(-u * 0.7) + 0.10 / (max(dmin, 1.0) * max(dmin, 1.0))) * mix(1.0, 0.6, near) * (1.0 + LOCAL_GLOW * Flare);
    g += Flare * LOCAL_HALO * exp(-u * 0.12) * inversesqrt(sl);
    g += (1.5 + LOCAL_CORE * Flare) * exp(-d1 * 4.0) * mix(1.0, 0.5, near); // cœur de lueur, à l'échelle du vrai rayon de l'étoile (remplace le disque)
    float streaks = max(far, 0.4 * Flare); // les stries de l'étoile locale subsistent de près
    if (streaks > 0.0) { // fines stries radiales irrégulières, fixes dans le ciel (bruit sur un cercle : pas de couture)
        vec3 d = normalize(Dir);
        vec3 ctr = transpose(GalRot) * (-ro / dist);
        vec3 up = abs(ctr.y) < 0.99 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
        vec3 e1 = normalize(cross(up, ctr));
        vec3 e2 = cross(ctr, e1);
        vec3 perp = d - ctr * dot(d, ctr);
        float pl = length(perp);
        if (pl > 1e-6) {
            float rays = pow(vnoise(vec3(dot(perp, e1), dot(perp, e2), 0.0) / pl * 7.0 + vec3(Seed * 40.0, 0.0)), 4.0);
            g += streaks * rays * (0.5 + LOCAL_RAYS * Flare) * exp(-u * mix(0.28, 0.10, Flare)) * (1.0 - smoothstep(min(asin(min(2.0 * sl / dist, 1.0)), 0.9 * reachAng), reachAng, ang));
        }
    }
    g *= (1.0 - smoothstep(0.35 * reachAng, reachAng, ang)) * (1.0 - dw); // pas de lueur sous la sphère opaque
    float bd = ang / STAR_BASE_ANGLE;
    g += STAR_BASE_GAIN * (bd < 1.0 ? (1.0 - bd * bd) * (1.0 - bd * bd) : 0.0) * far * (1.0 - dw); // base d'étoile lointaine, hors fondu du flare
    vec3 rgb = tonemap(mix(hot, vec3(1.0), 0.1) * g); // lueur teintée par l'étoile
    float a = dw; // pré-multiplié : additif de loin, opaque de près
    float h = b * b - c;
    if (near > 0.0 && h > 0.0 && -b - sqrt(h) > 0.0) { // plasma : évalué seulement de près, sur le disque
        vec3 n = ro + rd * (-b - sqrt(h));
        float mu = clamp(dot(n, -rd), 0.0, 1.0);
        float heat = 1.0;
        if (Params.y > 0.001) {
            float t = Time * Params.x * 0.35;
            vec3 q = n * 6.0 * Params.y;
            float cell = 1.0 - smoothstep(0.0, 0.8, bubbles(q, t));
            float fine = 1.0 - smoothstep(0.0, 0.8, bubbles(q * 2.3 + 11.0, t * 1.7));
            heat = clamp(0.25 + 0.55 * cell + 0.25 * fine + 0.2 * vnoise(q * 0.6 + vec3(0.0, t * 0.3, 0.0)), 0.0, 1.0);
        }
        // émissif : le plasma rayonne, les cellules les plus chaudes surexposent
        vec3 plasma = (mix(cool, hot, heat) * (0.35 + 0.65 * pow(mu, 0.6)) + hot * pow(heat, 3.0) * 0.5) * Params.z;
        rgb = mix(rgb, tonemap(plasma), near);
        a = near; // la sphère apparaît par fondu : transparente de loin
    }
    fragColor = vec4(rgb, a);
}
