package de.geolykt.scs.shaders;

import org.jglrxavpok.jlsl.glsl.GLSL.In;
import org.jglrxavpok.jlsl.glsl.GLSL.Out;
import org.jglrxavpok.jlsl.glsl.GLSL.Uniform;
import org.jglrxavpok.jlsl.glsl.Mat4;
import org.jglrxavpok.jlsl.glsl.Vec2;
import org.jglrxavpok.jlsl.glsl.Vec4;
import org.jglrxavpok.jlsl.glsl.VertexShader;

public class StarRegionBlitVertexShader extends VertexShader {
    @In
    Vec4 a_position;
    @In
    Vec4 a_color;
    @In
    Vec2 a_texCoord0;

    @Uniform
    Mat4 u_projTrans;

    @Out
    Vec4 v_color;
    @Out
    Vec2 v_texCoords;

    @Override
    public void main() {
        this.v_color = this.a_color;
        this.v_color.w = this.v_color.w * (255.0/254.0);
        this.v_texCoords = this.a_texCoord0;
        super.gl_Position = this.u_projTrans.mul(this.a_position);
    }
}
