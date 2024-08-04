#version 330
#define PI 3.1415926538
#define DROPOFF 0.1

uniform mat4 u_projTrans;
uniform float u_explodeDecay;
uniform float u_explodeFactor;
uniform float u_explodeFloor;
flat in vec2 f_centerpos;
in vec2 v_origincoords;

void main()
{
    float rawDist = distance(v_origincoords, f_centerpos);
    // \frac{af}{\sin\left(dx\right)+a}\cdot\left(1-dx\right)
    float linearDepth = (DROPOFF * u_explodeFactor) / (sin(rawDist) + DROPOFF) * (1 - u_explodeDecay * rawDist);
    gl_FragColor = vec4(max(linearDepth, u_explodeFloor) - u_explodeFloor, 1.0, 1.0, 1.0);
}
