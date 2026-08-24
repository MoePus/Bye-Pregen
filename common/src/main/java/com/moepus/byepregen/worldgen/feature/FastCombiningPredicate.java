package com.moepus.byepregen.worldgen.feature;

import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;

public interface FastCombiningPredicate {
    BlockPredicate[] byepregen$getPredicates();
}
