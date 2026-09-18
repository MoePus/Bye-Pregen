package com.moepus.byepregen.mixin.feature.predicate;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.feature.FastBlockPredicateOptimizer;
import com.moepus.byepregen.worldgen.feature.FastDiskBlockPredicate;
import com.moepus.byepregen.worldgen.feature.FastDiskStateCursor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.HasSturdyFacePredicate;
import org.spongepowered.asm.mixin.*;

@MixinGate(config = ConfigFlag.PLACED_FEATURE_LOCAL_OPTIMIZATIONS)
@Mixin(value = HasSturdyFacePredicate.class, remap = false)
public abstract class HasSturdyFacePredicateMixin implements FastDiskBlockPredicate {
    @Shadow
    @Final
    private Vec3i offset;

    @Shadow
    @Final
    private Direction direction;

    /**
     * @author MoePus
     * @reason Use FastBlockPredicateOptimizer fast path.
     */
    @Overwrite
    public boolean test(LevelAccessor level, BlockPos pos) {
        BlockState state = FastBlockPredicateOptimizer.getState(level, pos, this.offset);
        BlockPos queriedPos = this.offset.equals(Vec3i.ZERO) ? pos : pos.offset(this.offset);
        return state.isFaceSturdy(level, queriedPos, this.direction);
    }

    @Override
    public boolean byepregen$test(FastDiskStateCursor cursor, BlockPos pos) {
        BlockState state = cursor.getState(this.offset);
        BlockPos queriedPos = this.offset.equals(Vec3i.ZERO) ? pos : pos.offset(this.offset);
        return state.isFaceSturdy(cursor.level(), queriedPos, this.direction);
    }
}
