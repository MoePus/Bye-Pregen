package com.moepus.byepregen.mixin.feature.predicate;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.feature.FastBlockPredicateOptimizer;
import com.moepus.byepregen.worldgen.feature.FastDiskBlockPredicate;
import com.moepus.byepregen.worldgen.feature.FastDiskStateCursor;
import com.moepus.byepregen.worldgen.feature.FastStateTestingPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.StateTestingPredicate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@MixinGate(config = ConfigFlag.PLACED_FEATURE_LOCAL_OPTIMIZATIONS)
@Mixin(value = StateTestingPredicate.class, priority = 1100)
public abstract class StateTestingPredicateMixin implements FastDiskBlockPredicate, FastStateTestingPredicate {
    @Shadow
    @Final
    protected Vec3i offset;

    @Shadow
    protected abstract boolean test(BlockState state);

    @Override
    public final Vec3i byepregen$getOffset() {
        return this.offset;
    }

    @Override
    public final boolean byepregen$test(FastDiskStateCursor cursor, BlockPos pos) {
        return this.test(cursor.getState(this.offset));
    }

    /**
     * @author moepus
     * @reason Avoid allocating BlockPos instances while reading predicate target states during worldgen.
     */
    @Overwrite
    public final boolean test(final WorldGenLevel level, final BlockPos pos) {
        return this.test(FastBlockPredicateOptimizer.getState(level, pos, this.offset));
    }
}
