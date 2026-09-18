package com.moepus.byepregen.worldgen.feature;

import com.moepus.byepregen.config.ConfigManager;
import java.util.List;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.DiskFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RepeatingPlacement;

public final class FeaturePlan {
    private static final String VANILLA_PLACEMENT_PACKAGE =
            "net.minecraft.world.level.levelgen.placement";
    private static final FeaturePlan UNSUPPORTED = new FeaturePlan(null, null, null, false);

    private final DiskFeature diskConfig;
    private final FastRuleBasedBlockStateProvider stateProvider;
    private final Vec3i[] predicateDependencies;
    private final boolean repeatingPlacement;

    private FeaturePlan(
            DiskFeature diskConfig,
            FastRuleBasedBlockStateProvider stateProvider,
            Vec3i[] predicateDependencies,
            boolean repeatingPlacement
    ) {
        this.diskConfig = diskConfig;
        this.stateProvider = stateProvider;
        this.predicateDependencies = predicateDependencies;
        this.repeatingPlacement = repeatingPlacement;
    }

    public static FeaturePlan create(
            Feature feature,
            List<PlacementModifier> modifiers
    ) {
        if (!ConfigManager.getConfig().worldgen().placedFeatures().memoizedDiskPlan()) {
            return UNSUPPORTED;
        }
        if (!(feature instanceof DiskFeature config)) {
            return UNSUPPORTED;
        }
        if (!((Object) config.stateProvider().value() instanceof FastRuleBasedBlockStateProvider provider)) {
            return UNSUPPORTED;
        }
        if (!hasOnlyVanillaPlacement(modifiers)) {
            return UNSUPPORTED;
        }
        Vec3i[] dependencies = BlockPredicateDependencies.find(config.target());
        if (dependencies == null) {
            return UNSUPPORTED;
        }
        return new FeaturePlan(config, provider, dependencies, hasRepeatingPlacement(modifiers));
    }

    public PredicateMemoizedDiskPlacement open(FastPlacementContext context) {
        if (this.diskConfig == null) {
            return null;
        }
        return PredicateMemoizedDiskPlacement.open(
                context,
                this.diskConfig,
                this.stateProvider,
                this.predicateDependencies,
                this.repeatingPlacement
        );
    }

    private static boolean hasRepeatingPlacement(List<PlacementModifier> modifiers) {
        for (PlacementModifier modifier : modifiers) {
            if (modifier instanceof RepeatingPlacement) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOnlyVanillaPlacement(List<PlacementModifier> modifiers) {
        for (PlacementModifier modifier : modifiers) {
            if (!VANILLA_PLACEMENT_PACKAGE.equals(modifier.getClass().getPackageName())) {
                return false;
            }
        }
        return true;
    }
}
