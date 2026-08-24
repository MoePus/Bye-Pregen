package com.moepus.byepregen;

import com.moepus.byepregen.integration.c2me.GcFreeCompat;
import com.moepus.byepregen.config.ConfigManager;
import com.moepus.byepregen.yalight.engine.YABlockStateLightClass;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Byepregen.MODID)
public class Byepregen {
    public static final String MODID = "byepregen";
    private static final Logger LOGGER = LoggerFactory.getLogger(Byepregen.class);

    public Byepregen(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(Byepregen::onLoadComplete);
        GcFreeCompat.register();
    }

    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        if (ConfigManager.getConfig().lighting().ya().enabled()) {
            if (YALightCompatibility.isScalableLuxLoaded()) {
                LOGGER.warn("ScalableLux is installed, so ByePregen YA light has been disabled "
                        + "despite lighting.ya.enabled=true");
                return;
            }
            event.enqueueWork(() -> YABlockStateLightClass.initialize());
        }
    }
}
