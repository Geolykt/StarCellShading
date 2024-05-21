package de.geolykt.scs.shaders;

import org.jglrxavpok.jlsl.glsl.FragmentShader;
import org.jglrxavpok.jlsl.glsl.GLSL.In;
import org.jglrxavpok.jlsl.glsl.GLSL.Uniform;
import org.jglrxavpok.jlsl.glsl.Mat4;
import org.jglrxavpok.jlsl.glsl.Vec2;
import org.jglrxavpok.jlsl.glsl.Vec4;

public class StarRegionFragmentShader extends FragmentShader {
    @Uniform
    Mat4 u_projTrans;
    @Uniform
    Vec2 u_ccoords;

    @In
    Vec4 v_color;
    @In
    Vec2 v_origincoords;

    @Override
    public void main() {
        Vec2 diff = this.v_origincoords.sub(this.u_ccoords);
        Vec4 fragcolor = v_color;
        fragcolor.w = Math.max(1 - Math.sqrt(diff.x * diff.x + diff.y * diff.y) * 8, 0F);
        super.gl_FragColor = fragcolor;
    }
}
