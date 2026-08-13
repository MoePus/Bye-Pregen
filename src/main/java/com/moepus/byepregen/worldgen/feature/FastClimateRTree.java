package com.moepus.byepregen.worldgen.feature;

import net.minecraft.world.level.biome.Climate;

public interface FastClimateRTree<T> {
    T byepregen$search(Climate.TargetPoint targetPoint);
}
