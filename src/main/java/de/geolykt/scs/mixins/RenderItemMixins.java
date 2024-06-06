package de.geolykt.scs.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.badlogic.gdx.math.Rectangle;

import de.geolykt.scs.rendercache.RenderItemAccess;

import snoddasmannen.galimulator.rendersystem.RenderItem;

@Mixin(RenderItem.class)
public class RenderItemMixins implements RenderItemAccess {
    @Shadow
    private Rectangle b;

    @Override
    @Unique(silent = false)
    public void starcellshading$setAABB(Rectangle rect) {
        this.b = rect;
    }
}
