package de.geolykt.scs.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.example.Main;

@Mixin(Main.class)
public class MainMixins {
    /*
    @ModifyArg(
            target = @Desc(value = "main", args = String[].class),
            at = @At(value = "INVOKE",
                desc = @Desc(
                    owner = LwjglApplication.class,
                    value = "<init>",
                    args = {ApplicationListener.class, LwjglApplicationConfiguration.class}
                )
            ),
            allow = 1,
            index = -1,
            expect = 1,
            require = 0
    )
    private static LwjglApplicationConfiguration scs$enableGL30(LwjglApplicationConfiguration config) {
        config.useGL30 = true;
        return config;
    }*/
}
