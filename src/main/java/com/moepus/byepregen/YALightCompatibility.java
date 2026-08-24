package com.moepus.byepregen;

import com.moepus.byepregen.integration.runtime.ModEnvironment;

public final class YALightCompatibility {
    public static final String SCALABLELUX_MOD_ID = "scalablelux";

    private YALightCompatibility() {
    }

    public static boolean isScalableLuxLoaded() {
        return ModEnvironment.isModLoaded(SCALABLELUX_MOD_ID);
    }
}
