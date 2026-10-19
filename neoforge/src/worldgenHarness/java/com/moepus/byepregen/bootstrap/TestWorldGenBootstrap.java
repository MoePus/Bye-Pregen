package com.moepus.byepregen.bootstrap;

import com.moepus.byepregen.test.TestWorldGen;
import com.moepus.byepregen.lighttest.DynamicLightTestBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(TestWorldGenBootstrap.MOD_ID)
public final class TestWorldGenBootstrap {
    public static final String MOD_ID = "byepregen_worldgen_harness";

    public TestWorldGenBootstrap(IEventBus modEventBus) {
        DynamicLightTestBlock.register(modEventBus, MOD_ID);
        TestWorldGen.registerIfEnabled();
    }
}
