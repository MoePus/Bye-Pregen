package com.moepus.byepregen;

import com.moepus.byepregen.compat.GcFreeCompat;
import com.moepus.byepregen.yalight.YABlockStateLightClass;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Byepregen.MODID)
public class Byepregen {
    public static final String MODID = "byepregen";

    public Byepregen() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(Byepregen::onLoadComplete);
        GcFreeCompat.register();
    }

    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        if (ConfigParser.getConfig().enableYALightEngine) {
            event.enqueueWork(YABlockStateLightClass::initialize);
        }
    }
}
