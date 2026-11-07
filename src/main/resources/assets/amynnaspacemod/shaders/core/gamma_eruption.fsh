#version 150

in vec3 Dir;

// Repère de l'astre, unité = longueur d'un jet ; les jets suivent l'axe Y (haut et bas).
uniform mat3 GalRot;
uniform vec3 CamPos;
uniform vec2 Seed;     // dérivé de l'UUID du porteur
uniform float Time;
uniform vec4 ColorA;   // base des jets
uniform vec4 ColorB;   // pointe des jets
uniform vec3 Params;   // x = demi-largeur à la pointe (fraction de la longueur), y = vitesse, z = graine de l'animation
uniform vec3 Pulsar;   // x = phase de rotation (rad, calculée côté Java), y = inclinaison des jets (rad), z = courbure en spirale [0, 1]

out vec4 fragColor;

const int STEPS = 40;
const float TWIST = 3.0; // retard angulaire (rad) de la pointe du jet sur sa base : la spirale

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

// Repère de l'astre -> repère des jets : les jets sont inclinés de Pulsar.y sur l'axe de rotation Y et tournent de la phase Pulsar.x (phare).
vec3 toJet(vec3 v) {
    float a = -Pulsar.x, ca = cos(a), sa = sin(a);
    v = vec3(v.x * ca + v.z * sa, v.y, -v.x * sa + v.z * ca);
    float t = -Pulsar.y, ct = cos(t), st = sin(t);
    return vec3(v.x * ct - v.y * st, v.x * st + v.y * ct, v.z);
}

void main() {
    vec3 rd = toJet(normalize(GalRot * Dir));
    vec3 ro = toJet(CamPos);
    float w = Params.x;
    float bw = w + Pulsar.z; // la courbure décale la ligne centrale jusqu'à Pulsar.z en bout de jet
    // boîte englobante des deux jets
    vec3 inv = 1.0 / (abs(rd) + 1e-6) * sign(rd + 1e-9);
    vec3 bmin = vec3(-bw, -1.0, -bw);
    vec3 bmax = vec3(bw, 1.0, bw);
    vec3 ta = (bmin - ro) * inv;
    vec3 tb = (bmax - ro) * inv;
    vec3 tn = min(ta, tb);
    vec3 tf = max(ta, tb);
    float t0 = max(max(tn.x, tn.y), max(tn.z, 0.0));
    float t1 = min(min(tf.x, tf.y), tf.z);
    if (t1 <= t0) discard;
    float dt = (t1 - t0) / float(STEPS);
    vec3 off = vec3(Seed * 40.0, Seed.x + Seed.y) + Params.z * 0.37;
    vec3 acc = vec3(0.0);
    for (int i = 0; i < STEPS; i++) {
        vec3 p = ro + rd * (t0 + (float(i) + 0.5) * dt);
        float h = abs(p.y);
        float wr = w * (0.15 + 0.85 * h);
        // ligne centrale courbée en spirale (point-symétrique entre les deux jets) ; la matière suit la courbe
        float phi = -TWIST * h;
        vec2 pq = p.xz - Pulsar.z * h * h * sign(p.y) * vec2(cos(phi), sin(phi));
        float r = length(pq) / wr;
        if (r > 1.5) continue;
        float core = exp(-r * r * 3.0);
        // matière advectée vers l'extérieur (le signe de y fait fuir les deux jets)
        float n = 0.6 * vnoise(vec3(pq.x / wr * 1.5, h * 6.0 - Time * Params.y * 0.8, pq.y / wr * 1.5) + off)
                + 0.4 * vnoise(vec3(pq.x / wr * 4.0, h * 14.0 - Time * Params.y * 1.6, pq.y / wr * 4.0) + off.zxy);
        float dens = core * (0.25 + 1.1 * n) * smoothstep(1.0, 0.55, h) * smoothstep(0.0, 0.03, h + 0.02);
        vec4 c = mix(ColorA, ColorB, h);
        acc += c.rgb * c.a * dens * dt / wr * 0.9;
    }
    fragColor = vec4(1.0 - exp(-acc), 0.0); // additif
}
