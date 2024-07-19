#version 330

uniform sampler2D u_texture;
uniform vec2 u_pixelSize;

in vec4 v_color;
in vec2 v_texCoords;

void main()
{
    vec4 color = vec4(0.0, 0.0, 0.0, 0.0);
    color += texture(u_texture, v_texCoords + u_pixelSize.xy);
    color += texture(u_texture, v_texCoords + vec2(0, u_pixelSize.y));
    color += texture(u_texture, v_texCoords + vec2(-u_pixelSize.x, u_pixelSize.y));
    color += texture(u_texture, v_texCoords + vec2(u_pixelSize.x, 0));
    color -= texture(u_texture, v_texCoords) * 8;
    color += texture(u_texture, v_texCoords + vec2(-u_pixelSize.x, 0));
    color += texture(u_texture, v_texCoords + vec2(u_pixelSize.x, -u_pixelSize.y));
    color += texture(u_texture, v_texCoords + vec2(0, -u_pixelSize.y));
    color += texture(u_texture, v_texCoords - u_pixelSize.xy);

    gl_FragColor = texture(u_texture, v_texCoords) * (1.0 - length(color)) * v_color;
}
