package com.moepus.byepregen.worldgen.feature;

import com.moepus.byepregen.config.ConfigManager;
import java.util.List;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

public final class FeaturePlanCompiler {
    private FeaturePlanCompiler() {
    }

    public static CompiledFeaturePlan compile(
            ConfiguredFeature<?, ?> feature,
            List<PlacementModifier> modifiers
    ) {
        if (!ConfigManager.getConfig().worldgen().placedFeatures().memoizedDiskPlan()) {
            return UnsupportedFeaturePlan.INSTANCE;
        }
        if (feature.feature() != Feature.DISK || !(feature.config() instanceof DiskConfiguration config)) {
            return UnsupportedFeaturePlan.INSTANCE;
        }
        if (!((Object) config.stateProvider() instanceof FastRuleBasedBlockStateProvider)) {
            return UnsupportedFeaturePlan.INSTANCE;
        }

        PlacementCapabilities capabilities = inspectModifiers(modifiers);
        if (!capabilities.supported()) {
            return UnsupportedFeaturePlan.INSTANCE;
        }
        Vec3i[] dependencies = BlockPredicateDependencies.find(config.target());
        if (dependencies == null) {
            return UnsupportedFeaturePlan.INSTANCE;
        }
        return new DiskFeaturePlan(config, dependencies, capabilities.mayProduceMultipleOrigins());
    }

    private static PlacementCapabilities inspectModifiers(List<PlacementModifier> modifiers) {
        boolean mayProduceMultipleOrigins = false;
        for (PlacementModifier modifier : modifiers) {
            if (!(modifier instanceof PlanCompatiblePlacementModifier compatible)
                    || !PlanCompatiblePlacementModifier.isDirectlyImplementedBy(modifier)) {
                return PlacementCapabilities.UNSUPPORTED;
            }
            mayProduceMultipleOrigins |= compatible.byepregen$mayProduceMultipleOrigins();
        }
        return new PlacementCapabilities(true, mayProduceMultipleOrigins);
    }

    private record PlacementCapabilities(boolean supported, boolean mayProduceMultipleOrigins) {
        private static final PlacementCapabilities UNSUPPORTED = new PlacementCapabilities(false, false);
    }
}
