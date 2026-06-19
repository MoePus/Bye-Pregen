package com.moepus.byepregen;

import com.moepus.byepregen.compat.GcFreeCompat;
import com.moepus.byepregen.test.TestWorldGen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(Byepregen.MODID)
public class Byepregen {
    public static final String MODID = "byepregen";

    public Byepregen(IEventBus modEventBus, ModContainer modContainer) {
        GcFreeCompat.register();
        TestWorldGen.registerIfEnabled();
    }
}
