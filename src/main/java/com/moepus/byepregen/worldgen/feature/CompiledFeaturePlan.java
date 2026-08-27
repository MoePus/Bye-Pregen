package com.moepus.byepregen.worldgen.feature;

import javax.annotation.Nullable;

public sealed interface CompiledFeaturePlan permits DiskFeaturePlan, UnsupportedFeaturePlan {
    @Nullable
    FastFeaturePlacement open(FastPlacementContext context);
}
