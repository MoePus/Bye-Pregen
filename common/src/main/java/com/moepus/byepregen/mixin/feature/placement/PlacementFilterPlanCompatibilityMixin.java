package com.moepus.byepregen.mixin.feature.placement;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.feature.PlanCompatiblePlacementModifier;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.BlockPredicateFilter;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.placement.SurfaceRelativeThresholdFilter;
import net.minecraft.world.level.levelgen.placement.SurfaceWaterDepthFilter;
import org.spongepowered.asm.mixin.Mixin;

@MixinGate(config = ConfigFlag.PLACED_FEATURE_LOCAL_OPTIMIZATIONS)
@Mixin(
        value = {
                BiomeFilter.class,
                BlockPredicateFilter.class,
                RarityFilter.class,
                SurfaceRelativeThresholdFilter.class,
                SurfaceWaterDepthFilter.class
        },
        remap = false
)
public abstract class PlacementFilterPlanCompatibilityMixin implements PlanCompatiblePlacementModifier {
}
