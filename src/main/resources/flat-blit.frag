#version 330

uniform sampler2D u_texture;
in vec4 v_color;
in vec2 v_texCoords;

void main()
{
    gl_FragColor = v_color;
    gl_FragDepth = 1.0 - (texture(u_texture, v_texCoords)).w;
}
