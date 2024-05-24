package de.geolykt.scs;

import de.geolykt.starloader.api.gui.modconf.BooleanOption;
import de.geolykt.starloader.api.gui.modconf.ConfigurationSection;
import de.geolykt.starloader.api.gui.modconf.ModConf;

public class SCSConfig {

    private static final ConfigurationSection CONFIG_SECTION = ModConf.createSection("Star Cell Shading");
    public static final BooleanOption USE_VANILLA_CELL_SHADING = CONFIG_SECTION.addBooleanOption("Use vanilla region shading", false, false);

}
