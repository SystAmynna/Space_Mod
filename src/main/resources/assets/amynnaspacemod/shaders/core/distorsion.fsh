#version 150

in vec3 Dir;

// Repère de l'objet, unité = rayon de l'objet.
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 GalRot;   // repère de l'observateur -> repère de l'objet
uniform vec3 CamPos;
uniform vec2 Params;   // x = rayon d'Einstein (en rayons de l'objet), y = portée de la déformation (en rayons)
uniform vec2 ScreenSize;
uniform sampler2D Sampler0; // copie de l'image déjà dessinée (ce qui est derrière l'objet)

out vec4 fragColor;

// Lentille ponctuelle : un pixel vu à l'angle theta du centre montre la direction beta = theta - thetaE^2 / theta.
// Au-delà de la portée la déformation s'estompe (la sphère du trou noir couvre theta < rayon apparent).
void main() {
    float dist = length(CamPos);
    if (dist < 1.05) discard; // observateur dans l'objet : rien à déformer
    vec3 rd = normalize(GalRot * Dir);
    vec3 ctr = -CamPos / dist;
    float thetaR = asin(1.0 / dist);
    float thetaE = Params.x * thetaR;
    float maxTheta = Params.y * thetaR;
    float theta = acos(clamp(dot(rd, ctr), -1.0, 1.0));
    if (theta > maxTheta) discard;
    vec3 perp = rd - ctr * dot(ctr, rd);
    float pl = length(perp);
    if (pl < 1e-6) discard;
    float f = smoothstep(maxTheta, maxTheta * 0.4, theta);
    float beta = theta - f * thetaE * thetaE / theta;
    vec3 src = ctr * cos(beta) + perp / pl * sin(beta);
    vec4 clip = ProjMat * ModelViewMat * vec4(transpose(GalRot) * src * 100.0, 1.0);
    if (clip.w <= 0.0) discard;
    vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
    fragColor = vec4(texture(Sampler0, uv).rgb, 1.0);
}
