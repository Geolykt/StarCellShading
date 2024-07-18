#version 330
// Original class name: de.geolykt.scs.shaders.StarRegionBlitVertexShader compiled from StarRegionBlitVertexShader.java and of version 52
in vec4 a_position;
in vec4 a_color;
in vec2 a_texCoord0;
uniform mat4 u_projTrans;
out vec4 v_color;
out vec2 v_texCoords;

void main()
{
    v_color = a_color; //Line #29
    v_color.w = v_color.w*1.0039370078740157; //Line #30
    v_texCoords = a_texCoord0; //Line #31
    gl_Position = u_projTrans*a_position; //Line #32
}
