package de.geolykt.scs;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;

import net.minestom.server.extras.selfmodification.MinestomRootClassLoader;

import de.geolykt.starloader.api.event.EventHandler;
import de.geolykt.starloader.api.event.EventManager;
import de.geolykt.starloader.api.event.Listener;
import de.geolykt.starloader.api.event.lifecycle.ApplicationStartedEvent;
import de.geolykt.starloader.api.event.lifecycle.ApplicationStopEvent;
import de.geolykt.starloader.impl.asm.SpaceASMTransformer;
import de.geolykt.starloader.mod.Extension;
import de.geolykt.starloader.transformers.ASMTransformer;

public class SCSExtension extends Extension {

    @Override
    public void initialize() {
        EventManager.registerListener(new Listener() {
            @EventHandler
            public void afterStart(ApplicationStartedEvent e) {
                SCSCoreLogic.initializeExplodeShader();
                SCSCoreLogic.initializeBlitShader();
            }

            @EventHandler
            public void onStop(ApplicationStopEvent e) {
                SCSCoreLogic.disposeExplodeShader();
                SCSCoreLogic.disposeBlitShader();
            }
        });
    }

    static {
        MinestomRootClassLoader.getInstance().addASMTransformer(new ASMTransformer() {
            @Override
            public boolean isValidTarget(@NotNull String internalName) {
                return false;
            }

            @Override
            public boolean accept(@NotNull ClassNode node) {
                return false;
            }
        });
        SpaceASMTransformer.assumeVanillaRegionRenderingLogic(false);
    }
}
