package com.moepus.byepregen.mixin.dfc;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.dfc.runtime.FlatCacheAccess;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@MixinGate(config = ConfigFlag.FLAT_CACHE_ACCESS)
@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$FlatCache")
public abstract class DensityFlatCacheMixin implements FlatCacheAccess {
    @Shadow @Final private DensityFunction noiseFiller;
    @Shadow @Final private double[][] values;
    @Unique private int byepregen$firstNoiseX;
    @Unique private int byepregen$firstNoiseZ;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void byepregen$captureOrigin(
            NoiseChunk owner,
            DensityFunction noiseFiller,
            boolean precompute,
            CallbackInfo ci
    ) {
        this.byepregen$firstNoiseX = owner.firstNoiseX;
        this.byepregen$firstNoiseZ = owner.firstNoiseZ;
    }

    @Override
    public double byepregen$sampleFlatCache(
            int blockX,
            int blockZ,
            DensityFunction.FunctionContext fallbackContext
    ) {
        int x = QuartPos.fromBlock(blockX) - this.byepregen$firstNoiseX;
        int z = QuartPos.fromBlock(blockZ) - this.byepregen$firstNoiseZ;
        int length = this.values.length;
        return x >= 0 && z >= 0 && x < length && z < length
                ? this.values[x][z]
                : this.noiseFiller.compute(fallbackContext);
    }
}
