package com.moepus.byepregen.mixin.feature.placement;

import com.moepus.byepregen.worldgen.feature.PlanCompatiblePlacementModifier;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.NoiseBasedCountPlacement;
import net.minecraft.world.level.levelgen.placement.NoiseThresholdCountPlacement;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(
        value = {
                CountPlacement.class,
                NoiseBasedCountPlacement.class,
                NoiseThresholdCountPlacement.class
        }
)
public abstract class RepeatingPlacementPlanCompatibilityMixin implements PlanCompatiblePlacementModifier {
    @Override
    public boolean byepregen$mayProduceMultipleOrigins() {
        return true;
    }
}
