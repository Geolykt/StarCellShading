package de.geolykt.scs.rendercache;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import com.badlogic.gdx.math.Rectangle;

import de.geolykt.starloader.api.Galimulator;
import de.geolykt.starloader.api.gui.rendercache.RenderObject;

import snoddasmannen.galimulator.GalFX;
import snoddasmannen.galimulator.rendersystem.RenderItem;

public class DeferredGlobalRenderObject extends RenderItem implements RenderObject {
    @NotNull
    private final Runnable renderAction;

    public DeferredGlobalRenderObject(@NotNull Runnable renderAction) {
        this.renderAction = Objects.requireNonNull(renderAction, "renderAction may not be null");

        this.c = GalFX.get_m();
        float w = Galimulator.getMap().getWidth();
        float h = Galimulator.getMap().getWidth();
        ((RenderItemAccess) this).starcellshading$setAABB(new Rectangle(w / -2, h / -2, w, h));
    }

    @Override
    public void a() {
        this.renderAction.run();
    }
}
