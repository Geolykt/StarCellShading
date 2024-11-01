package de.geolykt.scs.mixins.gdxdiag;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;

import de.geolykt.scs.extern.gdxdiag.GDXDiagDisposable;
import de.geolykt.scs.extern.gdxdiag.GDXDiagError;

@Mixin(GLFrameBuffer.class)
public class DiagnosticFramebufferMixins implements GDXDiagDisposable {

    @Unique
    private boolean gdxdiagDisposed;

    @Override
    public boolean isGDXDiagDisposed() {
        return this.gdxdiagDisposed;
    }

    @Inject(target = @Desc(value = "dispose"), at = @At("HEAD"))
    public void gdxdiag$onDispose(CallbackInfo ci) {
        if (this.gdxdiagDisposed) {
            throw new GDXDiagError("Attempted to double-dispose Framebuffer " + this.toString());
        }
        this.gdxdiagDisposed = true;
    }

    @Inject(target = @Desc(value = "bind"), at = @At("HEAD"))
    public void gdxdiag$onUse(CallbackInfo ci) {
        if (this.gdxdiagDisposed) {
            throw new GDXDiagError("Use-after-free of Framebuffer: " + this.toString());
        }
    }
}
