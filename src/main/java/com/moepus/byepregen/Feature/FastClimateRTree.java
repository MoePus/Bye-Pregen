package com.moepus.byepregen.Feature;

import net.minecraft.world.level.biome.Climate;

public interface FastClimateRTree<T> {
    T byepregen$search(Climate.TargetPoint targetPoint);
}
