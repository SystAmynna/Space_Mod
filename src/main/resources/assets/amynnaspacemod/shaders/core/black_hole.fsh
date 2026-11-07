#version 150

in vec3 Dir;

// Repère du trou noir, unité = rayon de la sphère apparente.
uniform mat3 GalRot;
uniform vec3 CamPos;
uniform vec4 ColorA;   // couleur de la sphère (noir par défaut)

out vec4 fragColor;

void main() {
    vec3 rd = normalize(GalRot * Dir);
    float b = dot(CamPos, rd);
    float c = dot(CamPos, CamPos) - 1.0;
    // observateur dans la sphère : tout le ciel prend sa couleur
    if (c >= 0.0) {
        float h = b * b - c;
        if (h < 0.0 || -b - sqrt(h) < 0.0) discard;
    }
    fragColor = vec4(ColorA.rgb, 1.0);
}
