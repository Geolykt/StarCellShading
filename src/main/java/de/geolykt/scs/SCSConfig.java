package de.geolykt.scs;

import java.util.Arrays;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import de.geolykt.starloader.api.gui.modconf.ConfigurationSection;
import de.geolykt.starloader.api.gui.modconf.FloatOption;
import de.geolykt.starloader.api.gui.modconf.ModConf;
import de.geolykt.starloader.api.gui.modconf.StringOption;

public class SCSConfig {

    public static enum CellStyle {
        BLOOM,
        FLAT,
        VANILLA;

        @NotNull
        public static CellStyle getCurrentStyle() {
            return CellStyle.valueOf(SCSConfig.SHADING_CELL_STYLE.get());
        }

        @SuppressWarnings("null")
        @NotNull
        public static String @NotNull[] getOptions() {
            CellStyle[] styles = CellStyle.values();
            @NotNull String[] options = new @NotNull String[styles.length];
            for (int i = 0; i < styles.length; i++) {
                options[i] = Objects.toString(styles[i]);
            }
            return options;
        }
    }

    private static final ConfigurationSection CONFIG_SECTION = ModConf.createSection("Star Cell Shading");
    public static final FloatOption EMPIRE_BORDER_SIZE = SCSConfig.CONFIG_SECTION.addFloatOption("Empire border size", 1.0F, 1.0F, 0F, Float.MAX_VALUE, Arrays.asList(1.0F, 2.0F));
    public static final FloatOption EXPLODE_DECAY = SCSConfig.CONFIG_SECTION.addFloatOption("u_explodeDecay", 4F, 4F, 0F, Float.POSITIVE_INFINITY, Arrays.asList(4F));
    public static final FloatOption EXPLODE_FACTOR = SCSConfig.CONFIG_SECTION.addFloatOption("u_explodeFactor", 1.3F, 1.3F, 0F, Float.POSITIVE_INFINITY, Arrays.asList(1.3F));
    public static final FloatOption EXPLODE_FLOOR = SCSConfig.CONFIG_SECTION.addFloatOption("u_explodeFloor", 0.0F, 0.0F, 0F, 1F, Arrays.asList(0.0F));
    public static final FloatOption MASTER_ALPHA_MULTIPLIER = SCSConfig.CONFIG_SECTION.addFloatOption("Master alpha multiplier", 0.8F, 0.8F, 0F, 1F, Arrays.asList(0.8F, 1F));
    public static final StringOption SHADING_CELL_STYLE = SCSConfig.CONFIG_SECTION.addStringChooseOption("Style", Objects.toString(CellStyle.FLAT), Objects.toString(CellStyle.FLAT), CellStyle.getOptions());
}
