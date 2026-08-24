package com.moepus.byepregen.mixin.accessor.chunksave;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@MixinGate(feature = MixinFeature.GC_FREE_CHUNK_SAVE)
@Mixin(value = BlendingData.class, remap = false)
public interface BlendingDataAccessor {
    @Accessor("areaWithOldGeneration")
    LevelHeightAccessor byepregen$areaWithOldGeneration();

    @Accessor("heights")
    double[] byepregen$heights();
}
