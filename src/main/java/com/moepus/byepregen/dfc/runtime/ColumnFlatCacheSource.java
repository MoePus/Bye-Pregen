package com.moepus.byepregen.dfc.runtime;

import com.ishland.c2me.opts.dfc.common.ast.EvalType;
import com.ishland.c2me.opts.dfc.common.ducks.IFastCacheLike;
import com.ishland.c2me.opts.dfc.common.gen.jvm.util.DfcObjectCache;
import com.ishland.c2me.opts.dfc.common.gen.jvm.vif.NoisePosVanillaInterface;

public interface ColumnFlatCacheSource {
    double byepregen$getColumnValue(int blockX, int blockY, int blockZ, DfcObjectCache objectCache);

    static double sample(
            IFastCacheLike source,
            int blockX,
            int blockY,
            int blockZ,
            DfcObjectCache objectCache
    ) {
        if (!(source instanceof ColumnFlatCacheSource columnSource)) {
            return sampleGeneric(source, blockX, blockY, blockZ, objectCache);
        }
        return columnSource.byepregen$getColumnValue(blockX, blockY, blockZ, objectCache);
    }

    private static double sampleGeneric(
            IFastCacheLike source,
            int blockX,
            int blockY,
            int blockZ,
            DfcObjectCache objectCache
    ) {
        double cached = source.c2me$getCached(blockX, blockY, blockZ, EvalType.NORMAL);
        if (Double.doubleToRawLongBits(cached) != IFastCacheLike.CACHE_MISS_NAN_BITS) {
            return cached;
        }
        NoisePosVanillaInterface context = objectCache.getNoisePosVanillaInterface(
                blockX, blockY, blockZ, EvalType.NORMAL, objectCache);
        try {
            return source.c2me$getDelegate().compute(context);
        } finally {
            objectCache.recycle(context);
        }
    }
}
