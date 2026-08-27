package com.moepus.byepregen.mixin.dfc;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.arena.InterpolatedMarkerAccess;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Propagates marker provenance while the vanilla Marker record rebuilds itself. */
@MixinGate(feature = MixinFeature.DFC)
@Mixin(value = DensityFunctions.Marker.class, priority = 500)
public abstract class DensityMarkerMapTokenMixin implements DensityFunctions.MarkerOrMarked {
    @Shadow public abstract DensityFunctions.Marker.Type type();
    @Shadow public abstract DensityFunction wrapped();

    @Override
    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        DensityFunction mappedWrapped = this.wrapped().mapAll(visitor);
        DensityFunction mapped = switch (this.type()) {
            case Interpolated -> DensityFunctions.interpolated(mappedWrapped);
            case FlatCache -> DensityFunctions.flatCache(mappedWrapped);
            case Cache2D -> DensityFunctions.cache2d(mappedWrapped);
            case CacheOnce -> DensityFunctions.cacheOnce(mappedWrapped);
            case CacheAllInCell -> DensityFunctions.cacheAllInCell(mappedWrapped);
        };
        if ((Object) this instanceof InterpolatedMarkerAccess source
                && mapped instanceof InterpolatedMarkerAccess target) {
            Object token = source.byepregen$getInterpolationToken();
            if (token != null) target.byepregen$setInterpolationToken(token);
        }
        return visitor.apply(mapped);
    }
}
