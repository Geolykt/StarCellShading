package de.geolykt.scs.shaders;

import org.jglrxavpok.jlsl.glsl.GLSL.Flat;
import org.jglrxavpok.jlsl.glsl.GLSL.In;
import org.jglrxavpok.jlsl.glsl.GLSL.Out;
import org.jglrxavpok.jlsl.glsl.GLSL.Uniform;
import org.jglrxavpok.jlsl.glsl.Mat4;
import org.jglrxavpok.jlsl.glsl.Vec2;
import org.jglrxavpok.jlsl.glsl.Vec4;
import org.jglrxavpok.jlsl.glsl.VertexShader;

public class StarRegionExplodeVertexShader extends VertexShader {
    // Input attributes
    @In
    Vec2 a_position;
    @In
    Vec2 a_centerpos;

    // Uniforms
    @Uniform
    Mat4 u_projTrans;

    @Out
    @Flat
    Vec2 f_centerpos;

    @Out
    Vec2 v_origincoords;

    @Override
    public void main() {
        this.f_centerpos = this.a_centerpos;
        this.v_origincoords = this.a_position;
        super.gl_Position =  this.u_projTrans.mul(new Vec4(this.a_position, 0, 1));
    }
}
