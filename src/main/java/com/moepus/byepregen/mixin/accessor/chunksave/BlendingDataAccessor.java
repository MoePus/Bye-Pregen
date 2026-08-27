package com.moepus.byepregen.mixin.accessor.chunksave;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlendingData.class)
public interface BlendingDataAccessor {
    @Accessor("areaWithOldGeneration")
    LevelHeightAccessor byepregen$areaWithOldGeneration();

    @Accessor("heights")
    double[] byepregen$heights();
}
