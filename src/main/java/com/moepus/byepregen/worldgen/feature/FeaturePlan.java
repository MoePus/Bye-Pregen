package com.moepus.byepregen.worldgen.feature;

import com.moepus.byepregen.config.ConfigParser;
import java.util.List;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RepeatingPlacement;

public final class FeaturePlan {
    private static final String VANILLA_PLACEMENT_PACKAGE =
            "net.minecraft.world.level.levelgen.placement";
    private static final FeaturePlan UNSUPPORTED = new FeaturePlan(null, null, false);

    private final DiskConfiguration diskConfig;
    private final Vec3i[] predicateDependencies;
    private final boolean repeatingPlacement;

    private FeaturePlan(
            DiskConfiguration diskConfig,
            Vec3i[] predicateDependencies,
            boolean repeatingPlacement
    ) {
        this.diskConfig = diskConfig;
        this.predicateDependencies = predicateDependencies;
        this.repeatingPlacement = repeatingPlacement;
    }

    public static FeaturePlan create(
            ConfiguredFeature<?, ?> feature,
            List<PlacementModifier> modifiers
    ) {
        if (!ConfigParser.getConfig().enableMemoizedDiskPlan) {
            return UNSUPPORTED;
        }
        if (feature.feature() != Feature.DISK || !(feature.config() instanceof DiskConfiguration config)) {
            return UNSUPPORTED;
        }
        if (!((Object) config.stateProvider() instanceof FastRuleBasedBlockStateProvider)) {
            return UNSUPPORTED;
        }
        if (!hasOnlyVanillaPlacement(modifiers)) {
            return UNSUPPORTED;
        }
        Vec3i[] dependencies = BlockPredicateDependencies.find(config.target());
        if (dependencies == null) {
            return UNSUPPORTED;
        }
        return new FeaturePlan(config, dependencies, hasRepeatingPlacement(modifiers));
    }

    public PredicateMemoizedDiskPlacement open(FastPlacementContext context) {
        if (this.diskConfig == null) {
            return null;
        }
        return PredicateMemoizedDiskPlacement.open(
                context,
                this.diskConfig,
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
