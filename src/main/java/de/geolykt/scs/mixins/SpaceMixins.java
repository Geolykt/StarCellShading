package de.geolykt.scs.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import de.geolykt.scs.SCSConfig;
import de.geolykt.scs.SCSCoreLogic;
import de.geolykt.starloader.api.Galimulator;
import de.geolykt.starloader.api.registry.RegistryKeys;

import snoddasmannen.galimulator.Settings.EnumSettings;
import snoddasmannen.galimulator.Space;
import snoddasmannen.galimulator.Star;
import snoddasmannen.galimulator.rendersystem.RenderCache;

@Mixin(Space.class)
public class SpaceMixins {

    @Redirect(require = 1, allow = 1,
            target = @Desc(value = "drawToCache", ret = RenderCache.class),
            slice = @Slice(
                from = @At(value = "FIELD", desc = @Desc(owner = EnumSettings.class, value = "DRAW_STAR_REGIONS", ret = EnumSettings.class)),
                to = @At(value = "INVOKE", desc = @Desc(owner = Star.class, value = "renderRegion"))
            ),
            at = @At(value = "INVOKE", desc = @Desc(owner = EnumSettings.class, value = "getValue", ret = Object.class)))
    private static Object starcellshading$redirectDrawStarRegions(EnumSettings settings) {
        if (settings != EnumSettings.DRAW_STAR_REGIONS) {
            throw new IllegalArgumentException("settings != D_S_R");
        }

        if (SCSConfig.USE_VANILLA_CELL_SHADING.get()
                || settings.getValue() == Boolean.FALSE
                // Force the use of the vanilla cell shading logic if our logic cannot be applied on MapModes without impacting
                // the quality of the represented contents
                || Galimulator.getActiveMapmode().getRegistryKey().equals(RegistryKeys.GALIMULATOR_FACTIONS_MAPMODE)
                || Galimulator.getActiveMapmode().getRegistryKey().equals(RegistryKeys.GALIMULATOR_RELIGION_MAPMODE)
                // With the wealth MapMode the bloom of stars versus the bloom of wealth cannot be easily distinguished,
                // prompting this distinction
                || Galimulator.getActiveMapmode().getRegistryKey().equals(RegistryKeys.GALIMULATOR_WEALTH_MAPMODE)) {
            return settings.getValue();
        }

        SCSCoreLogic.drawRegionsAsync();

        return Boolean.FALSE;
    }
}
