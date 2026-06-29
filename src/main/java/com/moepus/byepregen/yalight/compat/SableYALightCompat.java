package com.moepus.byepregen.yalight.compat;

import com.moepus.byepregen.yalight.YALightEngineHolder;
import net.minecraft.world.level.lighting.LevelLightEngine;

public final class SableYALightCompat {
    private SableYALightCompat() {
    }

    public static boolean hasBlockLight(LevelLightEngine lightEngine, boolean fallback) {
        return fallback || ((YALightEngineHolder) lightEngine).byepregen$getYALightEngine().hasBlockLight();
    }

    public static boolean hasSkyLight(LevelLightEngine lightEngine, boolean fallback) {
        return fallback || ((YALightEngineHolder) lightEngine).byepregen$getYALightEngine().hasSkyLight();
    }
}
