package com.moepus.byepregen.mixin.placement;

import com.moepus.byepregen.Feature.FastPlacementContext;
import com.moepus.byepregen.Feature.FastPlacementModifier;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = InSquarePlacement.class, remap = false)
public abstract class InSquarePlacementMixin implements FastPlacementModifier {
    @Override
    public void byepregen$collectPositions(FastPlacementContext context, int x, int y, int z, int nextIndex) {
        context.apply(nextIndex, x + context.random().nextInt(16), y, z + context.random().nextInt(16));
    }
}
