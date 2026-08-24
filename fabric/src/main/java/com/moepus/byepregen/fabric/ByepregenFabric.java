package com.moepus.byepregen.fabric;

import com.moepus.byepregen.config.ConfigManager;
import com.moepus.byepregen.yalight.engine.YABlockStateLightClass;
import net.fabricmc.api.ModInitializer;

public final class ByepregenFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        if (ConfigManager.getConfig().lighting().ya().enabled()) {
            YABlockStateLightClass.initialize();
        }
    }
}
