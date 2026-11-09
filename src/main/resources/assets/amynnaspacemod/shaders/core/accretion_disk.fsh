#version 150

in vec3 Dir;

// Repère du disque (dalle autour du plan y = 0), unité = rayon extérieur.
uniform mat3 GalRot;
uniform vec3 CamPos;
uniform vec2 Seed;     // graine tirée de l'UUID du porteur, [0, 1)
uniform vec4 ColorA;   // couleur intérieure, rgb + intensité
uniform vec4 ColorB;   // couleur extérieure
uniform vec3 Params;   // x = rayon intérieur, y = opacité, z = demi-épaisseur
uniform vec4 Spin;     // deux couches décalées d'une demi-période : x/z = phase [0, 1) (1 = 1 rad parcouru au bord), y/w = numéro de cycle (graine)
uniform float Flare;   // passe : 0 = entier, 1 = moitié arrière, 2 = moitié avant (par rapport au centre du disque)

out vec4 fragColor;

const float TAU = 6.2831853;
// shortcut: 40 pas x 2 couches x 5 bruits ; baisser STEPS / OCTAVES si le disque plein écran coûte trop.
const int STEPS = 40;
const int OCTAVES = 4;
const float NTH = 5.0;      // périodes de bruit sur un tour (1re octave) : filaments longs le long de l'orbite
const float FR = 16.0;      // fréquence radiale, par unité de ln r : filaments fins
const float FY = 1.2;       // fréquence verticale, par demi-épaisseur
// shortcut: rotation képlérienne saturée sous ce rayon (sinon le centre tourne à l'infini et le bruit n'est plus qu'un grain).
const float R_SPIN = 0.05;
const float PUFF = 1.6;     // la dalle traversée englobe les bords gonflés (h local jusqu'à 1,5 h)

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

// Bruit de valeur 3D, périodique de période entière en y (l'angle) : pas de couture à ±π.
float vnoise(vec3 x, float period) {
    vec3 i = floor(x), f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float y0 = mod(i.y, period), y1 = mod(i.y + 1.0, period);
    float a = mix(hash13(vec3(i.x, y0, i.z)), hash13(vec3(i.x + 1.0, y0, i.z)), f.x);
    float b = mix(hash13(vec3(i.x, y1, i.z)), hash13(vec3(i.x + 1.0, y1, i.z)), f.x);
    float c = mix(hash13(vec3(i.x, y0, i.z + 1.0)), hash13(vec3(i.x + 1.0, y0, i.z + 1.0)), f.x);
    float d = mix(hash13(vec3(i.x, y1, i.z + 1.0)), hash13(vec3(i.x + 1.0, y1, i.z + 1.0)), f.x);
    return mix(mix(a, b, f.y), mix(c, d, f.y), f.z);
}

float fbm(vec3 q, float period) {
    float s = 0.0, amp = 0.5, norm = 0.0;
    for (int k = 0; k < OCTAVES; k++) {
        s += amp * vnoise(q, period);
        norm += amp;
        q *= 2.0;
        period *= 2.0;
        amp *= 0.5;
    }
    return s / norm;
}

// Densité de gaz [0, 1] d'une couche : coordonnées polaires tournées (képlérien), filaments étirés le long de l'orbite, déformés par un bruit lent (turbulence).
float layer(float lr, float r, float th, float yh, float phase, float cycle) {
    float rot = phase * pow(max(r, R_SPIN), -1.5);
    vec3 q = vec3(lr * FR, (th - rot) / TAU * NTH, yh * FY) + vec3(Seed.x * 97.0 + cycle * 13.0, 0.0, Seed.y * 89.0 + cycle * 7.0);
    q.x += (vnoise(q * vec3(0.2, 1.0, 0.5), NTH) - 0.5) * 6.0;
    return fbm(q, NTH);
}

void main() {
    vec3 rd = normalize(GalRot * Dir);
    vec3 ro = CamPos;
    float h = Params.z, H = PUFF * h;
    // dalle |y| <= H
    float t0 = 0.0, t1 = 1e9;
    if (abs(rd.y) > 1e-6) {
        float ta = (-H - ro.y) / rd.y, tb = (H - ro.y) / rd.y;
        t0 = max(t0, min(ta, tb));
        t1 = min(t1, max(ta, tb));
    } else if (abs(ro.y) > H) {
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
    // moitié arrière / avant (trou noir : l'arrière passe par la lentille)
    float tc = -dot(ro, rd);
    if (Flare == 1.0) t0 = max(t0, tc);
    else if (Flare == 2.0) t1 = min(t1, tc);
    if (t1 <= t0) discard;

    // poids des deux couches (flowmap) : une couche est invisible quand sa phase revient à 0
    float wA = 1.0 - abs(2.0 * Spin.x - 1.0), wB = 1.0 - wA;
    float inner = Params.x;
    float tau = -log(1.0 - min(Params.y, 0.999)) * 2.0; // x2 : la densité moyenne du gaz est ~0,5
    float dt = (t1 - t0) / float(STEPS);
    float t = t0 + dt * hash13(vec3(gl_FragCoord.xy, 1.0));
    vec3 acc = vec3(0.0);
    float T = 1.0;
    for (int i = 0; i < STEPS; i++, t += dt) {
        vec3 p = ro + rd * t;
        float r = length(p.xz);
        if (r > 1.0 || r < inner) continue;
        float lr = log(r), th = atan(p.z, p.x), yh = p.y / h;
        float n = 0.0;
        if (wA > 0.01) n += wA * layer(lr, r, th, yh, Spin.x, Spin.y);
        if (wB > 0.01) n += wB * layer(lr, r, th, yh, Spin.z, Spin.w);
        float u = (r - inner) / (1.0 - inner);
        float hl = h * (0.5 + n); // bords gonflés là où le gaz est dense
        float vert = exp(-2.0 * p.y * p.y / (hl * hl));
        // fondu intérieur borné par le rayon intérieur (pas de trou : le ciel apparaîtrait par la lentille) ; bord extérieur effiloché par le bruit
        float edge = smoothstep(inner, inner + 0.02 * min(1.0 - inner, inner), r) * smoothstep(1.0, 0.7, r + 0.3 * (n - 0.5));
        float dens = smoothstep(0.3, 0.75, n) * vert * edge; // couloirs vides entre les filaments
        if (dens <= 0.0) continue;
        vec4 cc = mix(ColorA, ColorB, sqrt(u));
        vec3 col = cc.rgb * cc.a * (0.4 + 1.2 * n);
        float aStep = 1.0 - exp(-tau * dens * dt / (1.2533 * h));
        acc += T * aStep * col;
        T *= 1.0 - aStep;
        if (T < 0.01) break;
    }
    fragColor = vec4(acc, 1.0 - T); // prémultiplié
}
