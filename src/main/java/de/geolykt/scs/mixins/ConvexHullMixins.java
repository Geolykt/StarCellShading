package de.geolykt.scs.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.badlogic.gdx.math.ConvexHull;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;

@Mixin(ConvexHull.class)
public class ConvexHullMixins {

    /*
    @ModifyVariable(
            target = @Desc(
                    owner = ConvexHull.class,
                    value = "computeIndices",
                    args = {float[].class, int.class, int.class, boolean.class, boolean.class},
                    ret = IntArray.class
             ),
             at = @At(value = "INVOKE", desc = @Desc(value = "sortWithIndices", args = {float[].class, int.class, boolean.class})),
             name = "end",
             require = 1
    )
    public int scs$fixOffsetIndices(int originalValue, float[] points, int offset, int count, boolean sorted, boolean yDown) {
        return count;
    }

    @ModifyVariable(
            target = @Desc(
                    owner = ConvexHull.class,
                    value = "computePolygon",
                    args = {float[].class, int.class, int.class, boolean.class},
                    ret = FloatArray.class
             ),
            at = @At(value = "INVOKE", desc = @Desc(value = "sort", args = {float[].class, int.class})),
             name = "end"
    )
    public int scs$fixOffsetPolygon(int originalValue, float[] points, int offset, int count, boolean sorted) {
        return count;
    }*/
}
