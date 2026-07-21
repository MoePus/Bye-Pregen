package com.moepus.byepregen.mixin;

import com.moepus.byepregen.worldgen.ArenaCellCacheAccess;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$CacheAllInCell")
public abstract class NoiseChunkCellCacheArenaMixin implements ArenaCellCacheAccess {
    @Shadow @Final private DensityFunction noiseFiller;
    @Shadow @Final private double[] values;

    @Unique
    @Override
    public void byepregen$fillArenaCache(DensityFunction.ContextProvider contextProvider) {
        this.noiseFiller.fillArray(this.values, contextProvider);
    }
}
