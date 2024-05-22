package de.geolykt.scs.shaders;

import org.jglrxavpok.jlsl.glsl.FragmentShader;
import org.jglrxavpok.jlsl.glsl.GLSLMath;
import org.jglrxavpok.jlsl.glsl.GLSL.Flat;
import org.jglrxavpok.jlsl.glsl.GLSL.In;
import org.jglrxavpok.jlsl.glsl.GLSL.Uniform;
import org.jglrxavpok.jlsl.glsl.Mat4;
import org.jglrxavpok.jlsl.glsl.Vec2;
import org.jglrxavpok.jlsl.glsl.Vec4;

public class StarRegionExplodeFragmentShader extends FragmentShader {
    @Uniform
    Mat4 u_projTrans;

    @In
    @Flat
    Vec2 f_centerpos;

    @In
    Vec2 v_origincoords;

    @Override
    public void main() {
        Vec2 diff = this.v_origincoords.sub(this.f_centerpos);
        Vec4 color = new Vec4(1, 1, 1, Math.min(Math.max(1 - Math.sqrt(diff.x * diff.x + diff.y * diff.y) * 4, 0) * 1.3F, 1));
        color.w = Math.min(Math.max((GLSLMath.smoothstep(0.2, 1, color.w) - 0.2), 0), 1);

        super.gl_FragColor = new Vec4(1, 1, 1, color.w);
    }
}
