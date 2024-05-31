package de.geolykt.scs;

import java.util.Arrays;

import de.geolykt.starloader.api.gui.modconf.BooleanOption;
import de.geolykt.starloader.api.gui.modconf.ConfigurationSection;
import de.geolykt.starloader.api.gui.modconf.FloatOption;
import de.geolykt.starloader.api.gui.modconf.ModConf;

public class SCSConfig {
    private static final ConfigurationSection CONFIG_SECTION = ModConf.createSection("Star Cell Shading");
    public static final FloatOption EXPLODE_DECAY = SCSConfig.CONFIG_SECTION.addFloatOption("u_explodeDecay", 4F, 4F, 0F, Float.POSITIVE_INFINITY, Arrays.asList(4F));
    public static final FloatOption EXPLODE_FACTOR = SCSConfig.CONFIG_SECTION.addFloatOption("u_explodeFactor", 1.3F, 1.3F, 0F, Float.POSITIVE_INFINITY, Arrays.asList(1.3F));
    public static final FloatOption EXPLODE_FLOOR = SCSConfig.CONFIG_SECTION.addFloatOption("u_explodeFloor", 0.0F, 0.0F, 0F, 1F, Arrays.asList(0.0F));
    public static final FloatOption MASTER_ALPHA_MULTIPLIER = SCSConfig.CONFIG_SECTION.addFloatOption("Master alpha multiplier", 0.8F, 0.8F, 0F, 1F, Arrays.asList(0.8F, 1F));
    public static final BooleanOption USE_VANILLA_CELL_SHADING = SCSConfig.CONFIG_SECTION.addBooleanOption("Use vanilla region shading", false, false);
}
