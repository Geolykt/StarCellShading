#version 330
// Original class name: de.geolykt.scs.shaders.StarRegionBlitFragmentShader compiled from StarRegionBlitFragmentShader.java and of version 52
uniform sampler2D u_texture;
in vec4 v_color;
in vec2 v_texCoords;

void main()
{
    vec4 color = v_color; //Line #21
    color.w = (texture(u_texture, v_texCoords)).w; //Line #22
    gl_FragColor = color; //Line #23
}
