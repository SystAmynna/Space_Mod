#version 150

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

// Direction du fragment dans le repère de l'observateur (la sphère du ciel est centrée sur lui).
out vec3 Dir;

void main() {
    Dir = Position;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
