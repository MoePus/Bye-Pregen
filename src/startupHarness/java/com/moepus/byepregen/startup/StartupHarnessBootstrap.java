package com.moepus.byepregen.startup;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(StartupHarnessBootstrap.MOD_ID)
public final class StartupHarnessBootstrap {
    static final String MOD_ID = "byepregen_startup_harness";

    public StartupHarnessBootstrap() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            new ClientStartupProbe();
        } else {
            new ServerStartupProbe();
        }
    }
}
