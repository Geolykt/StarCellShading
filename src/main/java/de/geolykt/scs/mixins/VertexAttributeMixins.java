package de.geolykt.scs.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

@Mixin(VertexAttribute.class)
public class VertexAttributeMixins {
    @Shadow
    private int numComponents;

    @Shadow
    private int type;

    @ModifyReturnValue(method = "getSizeInBytes()I", at = @At("TAIL"))
    public int scs$getSizeInBytesForInts(int oldValue) {
        if (oldValue == 0 && (this.type == GL20.GL_INT || this.type == GL20.GL_UNSIGNED_INT)) {
            return this.numComponents * 4;
        }
        return oldValue;
    }
}
