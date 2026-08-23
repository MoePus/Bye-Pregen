package com.moepus.byepregen;

import com.moepus.byepregen.integration.c2me.GcFreeCompat;
import com.moepus.byepregen.config.ConfigManager;
import com.moepus.byepregen.yalight.engine.YABlockStateLightClass;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(Byepregen.MODID)
public class Byepregen {
    public static final String MODID = "byepregen";
    public Byepregen(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(Byepregen::onLoadComplete);
        GcFreeCompat.register();
    }

    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        if (ConfigManager.getConfig().lighting().ya().enabled()) {
            event.enqueueWork(() -> YABlockStateLightClass.initialize());
        }
    }
}
