package com.moepus.byepregen.mixin.placement;

import com.moepus.byepregen.PaletteContainer.FastPalette.FastPlacementContext;
import com.moepus.byepregen.Feature.FastPlacementModifier;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.levelgen.placement.RandomOffsetPlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = RandomOffsetPlacement.class, remap = false)
public abstract class RandomOffsetPlacementMixin implements FastPlacementModifier {
    @Shadow
    @Final
    private IntProvider xzSpread;

    @Shadow
    @Final
    private IntProvider ySpread;

    @Override
    public void byepregen$collectPositions(FastPlacementContext context, int x, int y, int z, int nextIndex) {
        context.apply(
            nextIndex,
            x + this.xzSpread.sample(context.random()),
            y + this.ySpread.sample(context.random()),
            z + this.xzSpread.sample(context.random())
        );
    }
}
