package de.geolykt.scs.mixins;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.stianloader.micromixin.transform.internal.util.Objects;

import de.geolykt.scs.StarAccess;

import snoddasmannen.galimulator.Star;

@Mixin(Star.class)
public class StarMixins implements StarAccess {
    @Shadow
    private transient float[] starRegionVertices;

    /*

    @Overwrite
    public void drawStarBorders() {
        GalimulatorImplementation.crash("Star-cell-shading overwrites the way star region textures are rendered (which also includes region borders) and as such this method shouldn't be used. This is a mod compatibility problem: Report it to the relevant mod developers.", true);
    }

    @Overwrite
    public void renderRegion() {
        GalimulatorImplementation.crash("Star-cell-shading overwrites the way star region textures are rendered and as such this method shouldn't be used. This is a mod compatibility problem: Report it to the relevant mod developers.", true);
    }

    */

    @Override
    public float @NotNull[] starCellShading$getStarRegionVertices() {
        return Objects.requireNonNull(this.starRegionVertices);
    }
}
