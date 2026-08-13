package com.moepus.byepregen.mixin.dfc;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.ishland.c2me.opts.dfc.common.ast.EvalType;
import com.ishland.c2me.opts.dfc.common.ducks.IFastCacheLike;
import com.ishland.c2me.opts.dfc.common.gen.jvm.util.DfcObjectCache;
import com.ishland.c2me.opts.dfc.common.gen.jvm.vif.NoisePosVanillaInterface;
import com.moepus.byepregen.dfc.column.ColumnFlatCacheSource;
import net.minecraft.core.QuartPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@MixinGate(feature = MixinFeature.DFC)
@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$FlatCache", priority = 900)
public abstract class DfcFlatCacheMixin implements ColumnFlatCacheSource {
    @Unique private static final int byepregen$QUART_ALIGNMENT_MASK = 3;
    @Unique private boolean byepregen$fallbackValid;
    @Unique private int byepregen$lastFallbackQuartX;
    @Unique private int byepregen$lastFallbackQuartZ;
    @Unique private double byepregen$lastFallbackValue;

    @Override
    public double byepregen$getColumnValue(
            int blockX,
            int blockY,
            int blockZ,
            DfcObjectCache objectCache
    ) {
        IFastCacheLike cache = (IFastCacheLike) this;
        double value = cache.c2me$getCached(blockX, blockY, blockZ, EvalType.NORMAL);
        if (Double.doubleToRawLongBits(value) != IFastCacheLike.CACHE_MISS_NAN_BITS) {
            return value;
        }

        int quartX = QuartPos.fromBlock(blockX);
        int quartZ = QuartPos.fromBlock(blockZ);
        boolean quartAligned = byepregen$isQuartAligned(blockX, blockZ);
        if (quartAligned && this.byepregen$fallbackValid
                && quartX == this.byepregen$lastFallbackQuartX
                && quartZ == this.byepregen$lastFallbackQuartZ) {
            return this.byepregen$lastFallbackValue;
        }

        NoisePosVanillaInterface context = objectCache.getNoisePosVanillaInterface(
                blockX, blockY, blockZ, EvalType.NORMAL, objectCache);
        try {
            value = cache.c2me$getDelegate().compute(context);
        } finally {
            objectCache.recycle(context);
        }
        if (quartAligned) {
            this.byepregen$lastFallbackQuartX = quartX;
            this.byepregen$lastFallbackQuartZ = quartZ;
            this.byepregen$lastFallbackValue = value;
            this.byepregen$fallbackValid = true;
        }
        return value;
    }

    @Unique
    private static boolean byepregen$isQuartAligned(int blockX, int blockZ) {
        return (blockX & byepregen$QUART_ALIGNMENT_MASK) == 0
                && (blockZ & byepregen$QUART_ALIGNMENT_MASK) == 0;
    }
}
