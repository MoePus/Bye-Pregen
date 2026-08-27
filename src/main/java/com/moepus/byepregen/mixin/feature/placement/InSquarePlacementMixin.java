package com.moepus.byepregen.mixin.feature.placement;

import com.moepus.byepregen.worldgen.feature.FastPlacementContext;
import com.moepus.byepregen.worldgen.feature.PlanCompatiblePlacementModifier;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(InSquarePlacement.class)
public abstract class InSquarePlacementMixin implements PlanCompatiblePlacementModifier {
    @Override
    public void byepregen$collectPositions(FastPlacementContext context, int x, int y, int z, int nextIndex) {
        context.apply(nextIndex, x + context.random().nextInt(16), y, z + context.random().nextInt(16));
    }
}
