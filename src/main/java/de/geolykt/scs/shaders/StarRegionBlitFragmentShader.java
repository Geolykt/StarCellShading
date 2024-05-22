package de.geolykt.scs.shaders;

import org.jglrxavpok.jlsl.glsl.FragmentShader;
import org.jglrxavpok.jlsl.glsl.GLSL.In;
import org.jglrxavpok.jlsl.glsl.GLSL.Uniform;
import org.jglrxavpok.jlsl.glsl.Sampler2D;
import org.jglrxavpok.jlsl.glsl.Vec2;
import org.jglrxavpok.jlsl.glsl.Vec4;

public class StarRegionBlitFragmentShader extends FragmentShader {
    @Uniform
    Sampler2D u_texture;

    @In
    Vec4 v_color;
    @In
    Vec2 v_texCoords;

    @Override
    public void main() {
        Vec4 color = this.v_color;
        color.w = this.texture(this.u_texture, this.v_texCoords).x;
        super.gl_FragColor = color;
    }
}
