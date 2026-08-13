package com.moepus.byepregen.mixin.feature.placement;

import com.moepus.byepregen.Feature.FastPlacementContext;
import com.moepus.byepregen.Feature.FastPlacementModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.RepeatingPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = RepeatingPlacement.class, remap = false)
public abstract class RepeatingPlacementMixin implements FastPlacementModifier {
    @Shadow
    protected abstract int count(RandomSource random, BlockPos pos);

    @Override
    public void byepregen$collectPositions(FastPlacementContext context, int x, int y, int z, int nextIndex) {
        BlockPos pos = context.modifierPos(x, y, z);
        for (int i = 0, count = this.count(context.random(), pos); i < count; i++) {
            context.apply(nextIndex, x, y, z);
        }
    }
}
