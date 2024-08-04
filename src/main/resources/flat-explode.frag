#version 330
#define PI 3.1415926538

uniform mat4 u_projTrans;
uniform float u_explodeDecay;
uniform float u_explodeFactor;
uniform float u_explodeFloor;
flat in vec2 f_centerpos;
in vec2 v_origincoords;

void main()
{
    //float newDist = dot(v_origincoords, f_centerpos) * u_explodeDecay / u_explodeFactor;
    //gl_FragColor = vec4(1.0, 1.0, 1.0, u_explodeFloor - newDist);

    // \left(\sin\left(\pi\cdot\min\left(dx^{0.5}+0.5,1.5\right)\right)+1\right)\cdot0.5f
    float linearDepth = (sin(PI * min(pow(u_explodeDecay * distance(v_origincoords, f_centerpos), 0.25) + 0.5, 1.5)) + 1) * 0.5 * u_explodeFactor;
    gl_FragColor = vec4(1.0, 1.0, 1.0, max(linearDepth, u_explodeFloor) - u_explodeFloor);


    //float linearDepth = (1.0 - (distance(v_origincoords, f_centerpos) * u_explodeDecay)) * u_explodeFactor;
    //gl_FragColor = vec4(1.0, 1.0, 1.0, smoothstep(u_explodeFloor, 1.0, linearDepth));
}
