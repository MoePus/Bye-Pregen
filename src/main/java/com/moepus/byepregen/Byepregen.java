package com.moepus.byepregen;

import com.moepus.byepregen.bootstrap.TestWorldGenBootstrap;
import com.moepus.byepregen.compat.GcFreeCompat;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(Byepregen.MODID)
public class Byepregen {
    public static final String MODID = "byepregen";
    private static final String TEST_WORLD_GEN_PROPERTY = "byepregen.testWorldGen";

    public Byepregen(IEventBus modEventBus, ModContainer modContainer) {
        GcFreeCompat.register();
        if (Boolean.getBoolean(TEST_WORLD_GEN_PROPERTY)) {
            TestWorldGenBootstrap.register();
        }
    }
}
