package com.moepus.byepregen.mixin.feature.placement;

import com.moepus.byepregen.worldgen.feature.FastPlacementContext;
import com.moepus.byepregen.worldgen.feature.PlanCompatiblePlacementModifier;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(HeightRangePlacement.class)
public abstract class HeightRangePlacementMixin implements PlanCompatiblePlacementModifier {
    @Shadow
    @Final
    private HeightProvider height;

    @Override
    public void byepregen$collectPositions(FastPlacementContext context, int x, int y, int z, int nextIndex) {
        context.apply(nextIndex, x, this.height.sample(context.random(), context.placementContext()), z);
    }
}
