package com.moepus.byepregen.mixin.feature.predicate;

import com.moepus.byepregen.Feature.DiskBlockPredicateEvaluator;
import com.moepus.byepregen.Feature.FastDiskBlockPredicate;
import com.moepus.byepregen.Feature.FastDiskStateCursor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.level.levelgen.blockpredicates.NotPredicate", remap = false)
public abstract class NotPredicateMixin implements FastDiskBlockPredicate {
    @Shadow
    @Final
    private BlockPredicate predicate;

    @Override
    public boolean byepregen$test(FastDiskStateCursor cursor, BlockPos pos) {
        return !DiskBlockPredicateEvaluator.test(this.predicate, cursor, pos);
    }
}
