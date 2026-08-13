package com.moepus.byepregen.mixin.feature.predicate;

import com.moepus.byepregen.Feature.FastBlockPredicateOptimizer;
import com.moepus.byepregen.Feature.FastDiskBlockPredicate;
import com.moepus.byepregen.Feature.FastDiskStateCursor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.HasSturdyFacePredicate;
import org.spongepowered.asm.mixin.*;

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
    public boolean test(WorldGenLevel level, BlockPos pos) {
        BlockState state = FastBlockPredicateOptimizer.getState(level, pos, this.offset);
        BlockPos queriedPos = byePregen$isZero(this.offset) ? pos : pos.offset(this.offset);
        return state.isFaceSturdy(level, queriedPos, this.direction);
    }

    @Override
    public boolean bpg$test(FastDiskStateCursor cursor, BlockPos pos) {
        BlockState state = cursor.getState(this.offset);
        BlockPos queriedPos = byePregen$isZero(this.offset) ? pos : pos.offset(this.offset);
        return state.isFaceSturdy(cursor.level(), queriedPos, this.direction);
    }

    @Unique
    private static boolean byePregen$isZero(Vec3i offset) {
        return offset.getX() == 0 && offset.getY() == 0 && offset.getZ() == 0;
    }
}
