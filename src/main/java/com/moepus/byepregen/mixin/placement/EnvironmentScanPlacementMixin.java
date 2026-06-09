package com.moepus.byepregen.mixin.placement;

import com.moepus.byepregen.FastPlacementContext;
import com.moepus.byepregen.FastPlacementModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.placement.EnvironmentScanPlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = EnvironmentScanPlacement.class, remap = false)
public abstract class EnvironmentScanPlacementMixin implements FastPlacementModifier {
    @Shadow
    @Final
    private Direction directionOfSearch;

    @Shadow
    @Final
    private BlockPredicate targetCondition;

    @Shadow
    @Final
    private BlockPredicate allowedSearchCondition;

    @Shadow
    @Final
    private int maxSteps;

    @Override
    public void byepregen$collectPositions(FastPlacementContext context, int x, int y, int z, int nextIndex) {
        BlockPos.MutableBlockPos pos = context.modifierPos(x, y, z);
        WorldGenLevel level = context.placementContext().getLevel();
        if (!this.allowedSearchCondition.test(level, pos)) {
            return;
        }

        for (int i = 0; i < this.maxSteps; i++) {
            if (this.targetCondition.test(level, pos)) {
                context.apply(nextIndex, pos.getX(), pos.getY(), pos.getZ());
                return;
            }

            pos.move(this.directionOfSearch);
            if (level.isOutsideBuildHeight(pos.getY()) || !this.allowedSearchCondition.test(level, pos)) {
                return;
            }
        }

        if (this.targetCondition.test(level, pos)) {
            context.apply(nextIndex, pos.getX(), pos.getY(), pos.getZ());
        }
    }
}
