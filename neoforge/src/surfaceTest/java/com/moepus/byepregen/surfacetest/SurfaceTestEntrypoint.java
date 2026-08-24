package com.moepus.byepregen.surfacetest;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/** Test-only entrypoint for probes that require a transformed Minecraft runtime. */
@Mod(value = SurfaceTestEntrypoint.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class SurfaceTestEntrypoint {
    static final String MOD_ID = "byepregen_surface_test";

    public SurfaceTestEntrypoint() {
        if (Boolean.getBoolean(SurfaceOpaqueRuntimeTest.ENABLED_PROPERTY)) {
            SurfaceOpaqueRuntimeTest.runAndExit();
        }
    }
}
