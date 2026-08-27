package com.moepus.byepregen.mixin.feature.placement;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.feature.PlanCompatiblePlacementModifier;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.NoiseBasedCountPlacement;
import net.minecraft.world.level.levelgen.placement.NoiseThresholdCountPlacement;
import org.spongepowered.asm.mixin.Mixin;

@MixinGate(config = ConfigFlag.PLACED_FEATURE_LOCAL_OPTIMIZATIONS)
@Mixin(
        value = {
                CountPlacement.class,
                NoiseBasedCountPlacement.class,
                NoiseThresholdCountPlacement.class
        },
        remap = false
)
public abstract class RepeatingPlacementPlanCompatibilityMixin implements PlanCompatiblePlacementModifier {
    @Override
    public boolean byepregen$mayProduceMultipleOrigins() {
        return true;
    }
}
