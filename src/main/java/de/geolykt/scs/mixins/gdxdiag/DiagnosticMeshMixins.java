package de.geolykt.scs.mixins.gdxdiag;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

import de.geolykt.scs.extern.gdxdiag.GDXDiagDisposable;
import de.geolykt.scs.extern.gdxdiag.GDXDiagError;

@Mixin(Mesh.class)
public class DiagnosticMeshMixins implements GDXDiagDisposable {

    @Unique
    private boolean gdxdiagDisposed;

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

    @Inject(target = @Desc(value = "render", args = {ShaderProgram.class, int.class, int.class, int.class, boolean.class}), at = @At("HEAD"))
    public void gdxdiag$onUse(CallbackInfo ci) {
        if (this.gdxdiagDisposed) {
            throw new GDXDiagError("Use-after-free of Mesh: " + this.toString());
        }
    }
}
