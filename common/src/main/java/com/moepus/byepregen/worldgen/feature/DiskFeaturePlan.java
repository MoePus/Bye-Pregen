package com.moepus.byepregen.worldgen.feature;

import javax.annotation.Nullable;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.feature.DiskFeature;

public final class DiskFeaturePlan implements CompiledFeaturePlan {
    private final DiskFeature config;
    private final Vec3i[] predicateDependencies;
    private final boolean mayProduceMultipleOrigins;

    DiskFeaturePlan(
            DiskFeature config,
            Vec3i[] predicateDependencies,
            boolean mayProduceMultipleOrigins
    ) {
        this.config = config;
        this.predicateDependencies = predicateDependencies;
        this.mayProduceMultipleOrigins = mayProduceMultipleOrigins;
    }

    @Override
    @Nullable
    public FastFeaturePlacement open(FastPlacementContext context) {
        return PredicateMemoizedDiskPlacement.open(
                context,
                this.config,
                (FastRuleBasedBlockStateProvider) (Object) this.config.stateProvider().value(),
                this.predicateDependencies,
                this.mayProduceMultipleOrigins
        );
    }
}
