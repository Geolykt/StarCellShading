package de.geolykt.scs.shaders;

import org.jglrxavpok.jlsl.glsl.GLSL.In;
import org.jglrxavpok.jlsl.glsl.GLSL.Out;
import org.jglrxavpok.jlsl.glsl.GLSL.Uniform;
import org.jglrxavpok.jlsl.glsl.Mat4;
import org.jglrxavpok.jlsl.glsl.Vec2;
import org.jglrxavpok.jlsl.glsl.Vec4;
import org.jglrxavpok.jlsl.glsl.VertexShader;

public class StarRegionVertexShader extends VertexShader {
    // Input attributes
    @In
    Vec4 a_color;
    @In
    Vec2 a_position;
    @In
    int a_owner;

    // Uniforms
    @Uniform
    Mat4 u_projTrans;
    @Uniform
    Vec2 u_ccoords;

    @Out
    Vec4 v_color;
    @Out
    Vec2 v_origincoords;

    @Override
    public void main() {
        this.v_color = this.a_color/*.mul(new Vec4(1, 1, 1, 0.35))*/;
        this.v_origincoords = this.a_position;
        this.gl_Position =  this.u_projTrans.mul(new Vec4(this.a_position, 0, 1));
    }
}
