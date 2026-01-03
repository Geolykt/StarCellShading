package de.geolykt.scs.mixins.gdxdiag;

import java.nio.Buffer;
import java.nio.ShortBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.glutils.IndexData;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

import de.geolykt.scs.extern.gdxdiag.GDXDiagDisposable;
import de.geolykt.scs.extern.gdxdiag.GDXDiagError;

@Mixin(Mesh.class)
public class DiagnosticMeshMixins implements GDXDiagDisposable {
    @Unique
    private static final boolean GDXDIAG_VERBOSE = Boolean.getBoolean("de.geolykt.scs.extern.gdxdiag.verbose");

    @Unique
    private boolean gdxdiagDisposed;

    @Shadow
    IndexData indices;

    @Shadow
    boolean isVertexArray;

    @Override
    public boolean isGDXDiagDisposed() {
        return this.gdxdiagDisposed;
    }

    @Inject(target = @Desc(value = "dispose"), at = @At("HEAD"))
    public void gdxdiag$onDispose(CallbackInfo ci) {
        if (this.gdxdiagDisposed) {
            throw new GDXDiagError("Attempted to double-dispose mesh " + this.toString());
        }
        this.gdxdiagDisposed = true;
    }

    @Inject(target = @Desc(value = "render", args = {ShaderProgram.class, int.class, int.class, int.class, boolean.class}), at = @At("HEAD"), require = 1, allow = 1)
    public void gdxdiag$onUse(CallbackInfo ci) {
        if (this.gdxdiagDisposed) {
            throw new GDXDiagError("Use-after-free of Mesh: " + this.toString());
        }
    }

    @Inject(
        target = @Desc(value = "render", args = {ShaderProgram.class, int.class, int.class, int.class, boolean.class}),
        at = @At(
            value = "INVOKE",
            desc = @Desc(owner = GL20.class, value = "glDrawElements", args = {int.class, int.class, int.class, Buffer.class})
        ),
        require = 1,
        allow = 1
    )
    public void gdxdiag$onDrawElements(CallbackInfo ci) {
        if (!DiagnosticMeshMixins.GDXDIAG_VERBOSE) {
            return;
        }

        ShortBuffer sb = this.indices.getBuffer();
        int limit = sb.limit();
        int pos = sb.position();

        if (limit > sb.capacity()) {
            throw new GDXDiagError("Limit (currently stored indices) above capacity (maximum allowed indices)");
        } else if (pos >= limit) {
            throw new GDXDiagError("Position outside bounds");
        }
    }
}
