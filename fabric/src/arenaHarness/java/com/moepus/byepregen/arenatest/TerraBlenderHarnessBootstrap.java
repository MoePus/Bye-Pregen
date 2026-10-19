package com.moepus.byepregen.arenatest;

import com.moepus.byepregen.harness.terrablender.TerraBlenderSurfaceChecks;
import net.fabricmc.api.ModInitializer;

public final class TerraBlenderHarnessBootstrap implements ModInitializer {
    @Override
    public void onInitialize() {
        if (System.getProperty("byepregen.terraBlenderHarness.result") != null) {
            TerraBlenderSurfaceChecks.registerRules();
        }
    }
}
