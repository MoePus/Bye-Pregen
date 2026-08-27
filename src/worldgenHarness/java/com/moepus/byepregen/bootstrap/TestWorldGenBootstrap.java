package com.moepus.byepregen.bootstrap;

import com.moepus.byepregen.test.TestWorldGen;
import net.minecraftforge.fml.common.Mod;

@Mod(TestWorldGenBootstrap.MOD_ID)
public final class TestWorldGenBootstrap {
    public static final String MOD_ID = "byepregen_worldgen_harness";

    public TestWorldGenBootstrap() {
        TestWorldGen.registerIfEnabled();
    }
}
