package com.moepus.byepregen.mixin.feature.disk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.feature.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.DiskFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@MixinGate(config = ConfigFlag.PLACED_FEATURE_LOCAL_OPTIMIZATIONS)
@Mixin(DiskFeature.class)
public abstract class DiskFeatureMixin implements FastDiskFeature {
    @Shadow protected abstract boolean placeColumn(WorldGenLevel level, RandomSource random,
                                                    int top, int bottom, BlockPos.MutableBlockPos pos);

    @Override
    public boolean byepregen$placeColumn(FastDiskFeature.ColumnContext context) {
        return this.placeColumn(context.level(), context.random(), context.maximumY(),
                context.minimumY() - 1, context.pos());
    }

    @WrapMethod(method = "place")
    private boolean byepregen$place(WorldGenLevel level, ChunkGenerator generator,
                                    RandomSource random, BlockPos origin, Operation<Boolean> original) {
        DiskFeature disk = (DiskFeature) (Object) this;
        if (!(level instanceof WorldGenRegionSectionCache cache)
                || !(disk.stateProvider().value() instanceof FastRuleBasedBlockStateProvider provider)) {
            return original.call(level, generator, random, origin);
        }
        var placement = new FastDiskPlacement(disk, provider, level, random,
                new FastDiskStateCursor(level, cache), null, this::byepregen$placeColumn);
        return placement.placeOrigin(origin.getX(), origin.getY(), origin.getZ());
    }
}
