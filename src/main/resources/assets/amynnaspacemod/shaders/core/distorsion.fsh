#version 150

in vec3 Dir;

// Repère de l'objet, unité = rayon de l'horizon R.
uniform mat3 GalRot;     // repère de l'observateur -> repère de l'objet
uniform vec3 CamPos;
uniform vec2 Params;     // x = rayon d'Einstein en rayons apparents, signé (> 0 trou noir, < 0 trou blanc), y = portée de la lentille (rayons apparents)
uniform samplerCube Sky; // ciel déjà dessiné derrière l'objet, capturé dans toutes les directions (repère de l'observateur)
uniform samplerCube Bright; // objets lumineux de Sky (au-dessus d'un seuil, quart de résolution, mips) : leurs halos en fin de plongée

out vec4 fragColor;

// Selon la distance d (en R) :
//  - loin : ombre (noire ou blanche) de demi-angle physique, lentille ponctuelle autour : un pixel à l'angle theta du centre montre beta = theta - sign·thetaE²/theta
//    (trou noir : anneau d'Einstein ; trou blanc : le fond est tiré vers le centre) ;
//  - FALL_FROM -> PHOTON : chute, l'univers entier se replie dans la partie du ciel hors de l'ombre ;
//  - sous PHOTON : l'ombre passe derrière l'observateur, l'univers ne tient plus que dans une bulle derrière lui, qui rétrécit jusqu'au centre ;
//  - GLOW_FROM -> GLOW_PEAK : chaque objet lumineux de la bulle brille de plus en plus et s'étale (halos de plus en plus larges), le fond reste sombre ;
//  - DARK_FROM -> DARK_TO : puis elle prend la couleur du trou (noir complet à 10 % de R pour un trou noir).
const float PI = 3.14159265;
const float SHADOW = 2.5980762;      // paramètre d'impact critique 3√3/2 : ombre ≈ 2,6 R vue de loin
const float PHOTON = 1.5;            // sphère de photons : l'ombre couvre tout l'hémisphère avant
const float FALL_FROM = 4.0;
const float BUBBLE_HORIZON = 1.2;    // demi-angle (rad) de la bulle derrière soi à l'horizon (~70°)
const float BUBBLE_CENTER = 0.35;    // ... au centre (~20°)
const float GLOW_FROM = 0.4;
const float GLOW_PEAK = 0.2;
const float BLOOM_GAIN = 1.5;        // intensité des halos au pic
const int BLOOM_LEVELS = 6;          // niveaux de mip de Bright ouverts l'un après l'autre : rayon du halo x2 par niveau
const float DARK_FROM = 0.2;
const float DARK_TO = 0.1;

// Demi-angle apparent de l'ombre vu de la distance d.
float shadow(float d) {
    if (d >= PHOTON) return asin(min(SHADOW * sqrt(1.0 - 1.0 / d) / d, 1.0));
    float bubble = d >= 1.0 ? mix(BUBBLE_HORIZON, PI / 2.0, (d - 1.0) / (PHOTON - 1.0)) : mix(BUBBLE_CENTER, BUBBLE_HORIZON, d);
    return PI - bubble;
}

void main() {
    float d = max(length(CamPos), 1e-4);
    float sigma = Params.x < 0.0 ? -1.0 : 1.0;
    vec3 hole = vec3(sigma < 0.0 ? 1.0 : 0.0);
    vec3 rd = normalize(GalRot * Dir);
    vec3 ctr = -CamPos / d;
    float theta = atan(length(cross(rd, ctr)), dot(rd, ctr)); // précis aux petits angles (objet lointain)
    float alpha = shadow(d);
    float fall = smoothstep(FALL_FROM, PHOTON, d);
    float glow = 1.0 - smoothstep(GLOW_PEAK, GLOW_FROM, d);
    float dark = 1.0 - smoothstep(DARK_TO, DARK_FROM, d);
    float thetaR = asin(min(1.0 / d, 1.0));
    float reach = min(Params.y * thetaR, PI);
    float w = max(fwidth(theta), 1e-6);
    float inShadow = 1.0 - smoothstep(alpha - w, alpha + w, theta); // bord anti-aliasé
    // opacité : la lentille s'efface vers sa portée (l'image d'origine, plus nette que la cubemap, reprend) ; pendant la chute tout le ciel est redessiné
    float a = max(max(smoothstep(reach, 0.6 * reach, theta), inShadow), max(fall, dark));
    if (a <= 0.0) discard;

    vec3 perp = rd - ctr * dot(ctr, rd);
    float pl = length(perp);
    vec3 pdir = pl > 1e-6 ? perp / pl : normalize(cross(ctr, abs(ctr.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
    float thetaE = abs(Params.x) * thetaR;
    float lens = -sigma * thetaE * thetaE / max(theta, 1e-6) * smoothstep(reach, 0.4 * reach, theta);
    float beta = mix(theta + lens, PI * (theta - alpha) / max(PI - alpha, 1e-4), fall);
    vec3 src = ctr * cos(beta) + pdir * sin(beta);
    vec3 dir = transpose(GalRot) * src;
    vec3 col = texture(Sky, dir).rgb;
    if (glow > 0.0) {
        // halos des objets lumineux : chaque niveau de mip double le rayon ; exp2(k) compense la dilution (énergie du halo / rayon)
        vec3 bloom = vec3(0.0);
        for (int k = 0; k < BLOOM_LEVELS; k++)
            bloom += textureLod(Bright, dir, float(k)).rgb * exp2(float(k)) * clamp(glow * float(BLOOM_LEVELS) - float(k), 0.0, 1.0);
        col += bloom * glow * BLOOM_GAIN;
        float m = max(col.r, max(col.g, col.b)); // au-delà de 1 : la teinte est gardée et les cœurs blanchissent (pas de saturation brutale par canal)
        if (m > 1.0) col = mix(col / m, vec3(1.0), 0.5 * (1.0 - 1.0 / m));
    }

    col = mix(col, hole, inShadow);
    col = mix(col, hole, dark);
    fragColor = vec4(col * a, a); // pré-multiplié
}
