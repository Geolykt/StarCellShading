#version 330

uniform sampler2D u_texture;
in vec4 v_color;
in vec2 v_texCoords;

void main()
{
    vec4 color = v_color;
    color.w = (texture(u_texture, v_texCoords)).w;
    gl_FragColor = color;
}
