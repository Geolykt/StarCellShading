package de.geolykt.scs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.objectweb.asm.tree.ClassNode;

import net.minestom.server.extras.selfmodification.MinestomRootClassLoader;

import de.geolykt.starloader.api.event.EventHandler;
import de.geolykt.starloader.api.event.EventManager;
import de.geolykt.starloader.api.event.Listener;
import de.geolykt.starloader.api.event.lifecycle.ApplicationStartedEvent;
import de.geolykt.starloader.api.event.lifecycle.ApplicationStopEvent;
import de.geolykt.starloader.api.resource.DataFolderProvider;
import de.geolykt.starloader.impl.asm.SpaceASMTransformer;
import de.geolykt.starloader.mod.Extension;
import de.geolykt.starloader.transformers.ASMTransformer;

public class SCSExtension extends Extension {

    @Override
    public void initialize() {
        EventManager.registerListener(new Listener() {
            @EventHandler
            public void afterStart(ApplicationStartedEvent e) {
//                SCSCoreLogic.initializeExplodeShader();
//                SCSCoreLogic.initializeBlitShader();
            }

            @EventHandler
            public void onStop(ApplicationStopEvent e) {
                SCSExtension.this.saveConfig();
                SCSCoreLogic.disposeExplodeShader();
                SCSCoreLogic.disposeBlitShader();
                SCSCoreLogic.disposeEdgeShader();
                SCSCoreLogic.discardFBOs();
            }
        });

        Path config = this.getConfigFile();
        if (Files.exists(config)) {
            try {
                JSONObject json = new JSONObject(new String(Files.readAllBytes(config), StandardCharsets.UTF_8));
                SCSConfig.SHADING_CELL_STYLE.set(json.optString(SCSConfig.SHADING_CELL_STYLE.getName(), SCSConfig.SHADING_CELL_STYLE.getDefault()) + "");
                SCSConfig.MASTER_ALPHA_MULTIPLIER.setValue(json.optFloat(SCSConfig.MASTER_ALPHA_MULTIPLIER.getName(), SCSConfig.MASTER_ALPHA_MULTIPLIER.getDefault()));
                SCSConfig.EXPLODE_FACTOR.setValue(json.optFloat(SCSConfig.EXPLODE_FACTOR.getName(), SCSConfig.EXPLODE_FACTOR.getDefault()));
                SCSConfig.EXPLODE_DECAY.setValue(json.optFloat(SCSConfig.EXPLODE_DECAY.getName(), SCSConfig.EXPLODE_DECAY.getDefault()));
                SCSConfig.EXPLODE_FLOOR.setValue(json.optFloat(SCSConfig.EXPLODE_FLOOR.getName(), SCSConfig.EXPLODE_FLOOR.getDefault()));
                SCSConfig.EMPIRE_BORDER_SIZE.setValue(json.optFloat(SCSConfig.EMPIRE_BORDER_SIZE.getName(), SCSConfig.EMPIRE_BORDER_SIZE.getDefault()));
                } catch (JSONException | IOException e1) {
                this.getLogger().warn("Unable to read configuration; Ignoring it.", e1);
            }
        } else {
            // Load class and run <clinit> block
            SCSConfig.SHADING_CELL_STYLE.get();
        }
    }

    public void saveConfig() {
        Path file = this.getConfigFile();
        Path parent = file.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JSONObject json = new JSONObject();
            json.put(SCSConfig.SHADING_CELL_STYLE.getName(), SCSConfig.SHADING_CELL_STYLE.get());
            json.put(SCSConfig.MASTER_ALPHA_MULTIPLIER.getName(), SCSConfig.MASTER_ALPHA_MULTIPLIER.getValue());
            json.put(SCSConfig.EXPLODE_FACTOR.getName(), SCSConfig.EXPLODE_FACTOR.getValue());
            json.put(SCSConfig.EXPLODE_DECAY.getName(), SCSConfig.EXPLODE_DECAY.getValue());
            json.put(SCSConfig.EXPLODE_FLOOR.getName(), SCSConfig.EXPLODE_FLOOR.getValue());
            json.put(SCSConfig.EMPIRE_BORDER_SIZE.getName(), SCSConfig.EMPIRE_BORDER_SIZE.getValue());
            Files.write(file, json.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            this.getLogger().warn("Unable to save configuration", e);
        }
    }

    @NotNull
    public Path getConfigFile() {
        return DataFolderProvider.getProvider().provideAsPath().resolve("mods/config/star-cell-shading.json");
    }

    static {
        SpaceASMTransformer.assumeVanillaRegionRenderingLogic(false);
    }
}
