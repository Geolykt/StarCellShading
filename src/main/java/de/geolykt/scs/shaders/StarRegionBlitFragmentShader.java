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
        super.gl_FragColor = this.v_color.mul(this.texture2D(this.u_texture, this.v_texCoords));
    }
}
