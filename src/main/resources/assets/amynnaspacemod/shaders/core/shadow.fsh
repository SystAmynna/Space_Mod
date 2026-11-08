#version 150

in vec3 Dir;

// Repère de l'objet ombré, unité = rayon de la sphère. Sortie multipliée à l'image (blend ZERO, SRC_COLOR).
uniform mat3 GalRot;
uniform vec3 CamPos;
uniform vec4 Params;      // x = éclairement ambiant (face nocturne), y = largeur du terminateur (en cosinus)
uniform mat4 Lights;      // colonne i : direction de l'étoile i vue du centre (repère de l'objet), w = flux relatif (0 = pas d'étoile)
uniform mat4 LightColors; // colonne i : couleur de l'étoile i (max = 1)

out vec4 fragColor;

void main() {
    vec3 rd = normalize(GalRot * Dir);
    float b = dot(CamPos, rd);
    float c = dot(CamPos, CamPos) - 1.0;
    float h = b * b - c;
    if (c <= 0.0 || h < 0.0) discard; // observateur dans la sphère, ou rayon hors du disque
    float t = -b - sqrt(h);
    if (t <= 0.0) discard;
    vec3 n = normalize(CamPos + rd * t);

    vec3 light = vec3(Params.x);
    for (int i = 0; i < 4; i++) {
        vec4 L = Lights[i];
        if (L.w <= 0.0) continue;
        float mu = dot(n, L.xyz);
        // terminateur doux, puis Lambert atténué : relief sans noircir le limbe éclairé
        float lit = smoothstep(-Params.y, Params.y, mu) * (0.35 + 0.65 * max(mu, 0.0));
        light += L.w * lit * mix(vec3(1.0), LightColors[i].rgb, 0.35); // teinte de l'étoile adoucie vers le blanc
    }
    fragColor = vec4(min(light, vec3(1.0)), 1.0);
}
