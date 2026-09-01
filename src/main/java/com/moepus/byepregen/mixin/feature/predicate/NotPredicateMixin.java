package com.moepus.byepregen.mixin.feature.predicate;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.feature.DiskBlockPredicateEvaluator;
import com.moepus.byepregen.worldgen.feature.FastDiskBlockPredicate;
import com.moepus.byepregen.worldgen.feature.FastDiskStateCursor;
import com.moepus.byepregen.worldgen.feature.FastNegatingPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@MixinGate(config = ConfigFlag.PLACED_FEATURE_LOCAL_OPTIMIZATIONS)
@Mixin(targets = "net.minecraft.world.level.levelgen.blockpredicates.NotPredicate", remap = false)
public abstract class NotPredicateMixin implements FastDiskBlockPredicate, FastNegatingPredicate {
    @Shadow
    @Final
    private BlockPredicate predicate;

    @Override
    public BlockPredicate byepregen$getPredicate() {
        return this.predicate;
    }

    @Override
    public boolean byepregen$test(FastDiskStateCursor cursor, BlockPos pos) {
        return !DiskBlockPredicateEvaluator.test(this.predicate, cursor, pos);
    }
}
