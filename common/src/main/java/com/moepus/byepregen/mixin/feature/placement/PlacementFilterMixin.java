package com.moepus.byepregen.mixin.feature.placement;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.feature.FastPlacementContext;
import com.moepus.byepregen.worldgen.feature.FastPlacementModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@MixinGate(config = ConfigFlag.PLACED_FEATURES)
@Mixin(value = PlacementFilter.class, remap = false)
public abstract class PlacementFilterMixin implements FastPlacementModifier {
    @Shadow
    protected abstract boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos);

    @Override
    public void byepregen$collectPositions(FastPlacementContext context, int x, int y, int z, int nextIndex) {
        BlockPos pos = context.modifierPos(x, y, z);
        if (this.shouldPlace(context.placementContext(), context.random(), pos)) {
            context.apply(nextIndex, x, y, z);
        }
    }
}
