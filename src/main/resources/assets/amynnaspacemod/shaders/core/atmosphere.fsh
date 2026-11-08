#version 150

in vec3 Dir;

// Repère de la planète, unité = rayon extérieur de l'atmosphère. Sortie prémultipliée.
uniform mat3 GalRot;
uniform vec3 CamPos;
uniform vec4 ColorA;      // couleur de jour (étoile haute), rgb + intensité
uniform vec4 ColorB;      // couleur de crépuscule (terminateur, basses couches en contre-jour)
uniform vec4 Params;      // x = rayon de la surface, y = densité
uniform mat4 Lights;      // colonne i : direction de l'étoile i vue du centre (repère de la planète), w = flux relatif (0 = pas d'étoile)
uniform mat4 LightColors; // colonne i : couleur de l'étoile i (max = 1)

out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// shortcut: pas fixes le long du rayon ; un halo très fin peut montrer un grain, augmenter STEPS si ça se voit.
const int STEPS = 24;
const float K = 0.05; // profondeur optique verticale à densité 1 (un rayon rasant au limbe en traverse ~20 à 30 fois plus)

void main() {
    vec3 rd = normalize(GalRot * Dir);
    vec3 ro = CamPos;
    float ri = Params.x, thick = 1.0 - ri, H = 0.25 * thick; // hauteur d'échelle
    float b = dot(ro, rd);
    float h = b * b - (dot(ro, ro) - 1.0);
    if (h < 0.0) discard;
    float sq = sqrt(h);
    float t0 = max(0.0, -b - sq), t1 = -b + sq;
    if (t1 <= t0) discard;
    float hs = b * b - (dot(ro, ro) - ri * ri); // arrêt sur la surface
    if (hs > 0.0) {
        float ts = -b - sqrt(hs);
        if (ts > 0.0) t1 = min(t1, ts);
    }
    if (t1 <= t0) discard;

    float dt = (t1 - t0) / float(STEPS);
    float t = t0 + dt * hash12(gl_FragCoord.xy);
    vec3 acc = vec3(0.0);
    float tau = 0.0;
    for (int s = 0; s < STEPS; s++, t += dt) {
        vec3 p = ro + rd * t;
        float r = length(p);
        float alt = clamp((r - ri) / thick, 0.0, 1.0);
        float dtau = K * Params.y * exp(-(r - ri) / H) * dt / H;
        vec3 sc = vec3(0.0);
        for (int i = 0; i < 4; i++) {
            vec4 L = Lights[i];
            if (L.w <= 0.0) continue;
            // ombre de la planète sur l'atmosphère (cylindre derrière elle, bord doux)
            float pl = dot(p, L.xyz);
            float lit = pl > 0.0 ? 1.0 : smoothstep(ri, ri + 0.25 * thick, length(p - pl * L.xyz));
            if (lit <= 0.0) continue;
            // étoile basse vue du point, ou couche basse : lumière rasante rougie -> crépuscule ; sinon jour
            float k = smoothstep(-0.1, 0.4, dot(p / r, L.xyz) + 0.6 * alt - 0.15);
            vec3 col = mix(ColorB.rgb * ColorB.a, ColorA.rgb * ColorA.a, k);
            float cosT = dot(rd, L.xyz);
            float phase = 0.75 * (1.0 + cosT * cosT) + 2.0 * pow(max(cosT, 0.0), 8.0); // Rayleigh + pointe avant (halo en contre-jour)
            sc += L.w * lit * phase * col * mix(vec3(1.0), LightColors[i].rgb, 0.35);
        }
        acc += exp(-tau) * (1.0 - exp(-dtau)) * sc;
        tau += dtau;
    }
    fragColor = vec4(0.6 * acc, 1.0 - exp(-tau));
}
