#version 330
// Original class name: de.geolykt.scs.shaders.StarRegionExplodeVertexShader compiled from StarRegionExplodeVertexShader.java and of version 52
in vec2 a_position;
in vec2 a_centerpos;
uniform mat4 u_projTrans;
flat out vec2 f_centerpos;
out vec2 v_origincoords;

void main()
{
    f_centerpos = a_centerpos; //Line #32
    v_origincoords = a_position; //Line #33
    gl_Position = u_projTrans*vec4(a_position, 0.0, 1.0); //Line #34
}
