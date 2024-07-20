#version 330

uniform mat4 u_projTrans;
uniform float u_explodeDecay;
uniform float u_explodeFactor;
uniform float u_explodeFloor;
flat in vec2 f_centerpos;
in vec2 v_origincoords;

void main()
{
    vec4 color = vec4(1.0, 1.0, 1.0, (1.0 - distance(v_origincoords, f_centerpos) * u_explodeDecay) * u_explodeFactor);
    color.w = min(max(smoothstep(u_explodeFloor, 1.0, color.w) - u_explodeFloor, 0.0), 1.0);
    gl_FragColor = color;
}
