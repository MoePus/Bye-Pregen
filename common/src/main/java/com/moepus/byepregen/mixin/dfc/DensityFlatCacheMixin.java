package com.moepus.byepregen.mixin.dfc;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.dfc.runtime.FlatCacheAccess;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@MixinGate(feature = MixinFeature.DFC)
@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$FlatCache")
public abstract class DensityFlatCacheMixin implements FlatCacheAccess {
    @Shadow @Final private DensityFunction noiseFiller;
    @Shadow @Final private double[] values;
    @Shadow @Final private int sizeXZ;
    @Shadow(aliases = "this$0") @Final private NoiseChunk byepregen$owner;

    @Override
    public double byepregen$sampleFlatCache(
            int blockX,
            int blockZ,
            DensityFunction.FunctionContext fallbackContext
    ) {
        int x = QuartPos.fromBlock(blockX) - this.byepregen$owner.firstNoiseX;
        int z = QuartPos.fromBlock(blockZ) - this.byepregen$owner.firstNoiseZ;
        return x >= 0 && z >= 0 && x < this.sizeXZ && z < this.sizeXZ
                ? this.values[x + z * this.sizeXZ]
                : this.noiseFiller.compute(fallbackContext);
    }
}
