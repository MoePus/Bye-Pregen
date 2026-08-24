package com.moepus.byepregen.fabric;

import com.moepus.byepregen.YALightCompatibility;
import com.moepus.byepregen.config.ConfigManager;
import com.moepus.byepregen.yalight.engine.YABlockStateLightClass;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ByepregenFabric implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ByepregenFabric.class);

    @Override
    public void onInitialize() {
        if (ConfigManager.getConfig().lighting().ya().enabled()) {
            if (YALightCompatibility.isScalableLuxLoaded()) {
                LOGGER.warn("ScalableLux is installed, so ByePregen YA light has been disabled "
                        + "despite lighting.ya.enabled=true");
                return;
            }
            YABlockStateLightClass.initialize();
        }
    }
}
