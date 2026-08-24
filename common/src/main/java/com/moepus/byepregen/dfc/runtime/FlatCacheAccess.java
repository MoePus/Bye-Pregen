package com.moepus.byepregen.dfc.runtime;

import net.minecraft.world.level.levelgen.DensityFunction;

public interface FlatCacheAccess {
    double byepregen$sampleFlatCache(
            int blockX,
            int blockZ,
            DensityFunction.FunctionContext fallbackContext
    );
}
