package de.geolykt.scs.shaders;

import org.jglrxavpok.jlsl.glsl.FragmentShader;
import org.jglrxavpok.jlsl.glsl.GLSLMath;
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
        color.w = color.w * this.texture2D(this.u_texture, this.v_texCoords).w;
//        color.w = GLSLMath.smoothstep(1, 0, color.w);
//        color.w = Math.pow(1 + color.w, 1.5) - 1;
//        color.w = Math.min((color.w + GLSLMath.smoothstep(0, 1, color.w)), 1) * 0.9;
        super.gl_FragColor = color;
    }
}
