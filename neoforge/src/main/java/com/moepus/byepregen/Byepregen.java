package com.moepus.byepregen;

import com.moepus.byepregen.config.Config;
import com.moepus.byepregen.config.ConfigManager;
import com.moepus.byepregen.yalight.engine.YABlockStateLightClass;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Byepregen.MODID)
public class Byepregen {
    public static final String MODID = "byepregen";
    private static final Logger LOGGER = LoggerFactory.getLogger(Byepregen.class);

    public Byepregen(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(Byepregen::onLoadComplete);
        LOGGER.info("ByePregen world generation optimizations initialized for 26.3");
    }

    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        Config config = ConfigManager.getConfig();
        if (!config.lighting().ya().enabled()) {
            return;
        }
        if (!MixinPlugin.isFeatureEnabled(MixinFeature.YA_LIGHT, config)) {
            LOGGER.warn("ScalableLux is installed, so ByePregen YA light has been disabled "
                    + "despite lighting.ya.enabled=true");
            return;
        }
        event.enqueueWork(YABlockStateLightClass::initialize);
    }
}
