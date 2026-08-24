package com.moepus.byepregen.mixin.worldgen.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = LeavesBlock.class,remap = false, priority = 500)
public abstract class LeavesBlockWorldgenTickMixin {
    @InjectLite(method = "updateShape", at = @At("HEAD"), cancel = true, cancelOnNonNull = true)
    private BlockState byepregen$skipStableWorldgenLeafTick(
            final BlockState state,
            final LevelReader level,
            final ScheduledTickAccess scheduledTicks,
            final BlockPos pos,
            final Direction direction,
            final BlockPos neighborPos,
            final BlockState neighborState,
            final RandomSource random
    ) {
        if (!(level instanceof final WorldGenRegion worldGenRegion)) {
            return null;
        }

        if (state.getValue(LeavesBlock.WATERLOGGED)) {
            scheduledTicks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return state;
    }
}
