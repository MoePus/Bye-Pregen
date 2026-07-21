package com.moepus.byepregen.worldgen;

import net.minecraft.world.level.levelgen.DensityFunction;

public interface ArenaCellCacheAccess {
    void byepregen$fillArenaCache(DensityFunction.ContextProvider contextProvider);
}
