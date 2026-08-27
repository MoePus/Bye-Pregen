package com.moepus.byepregen.worldgen.feature;

import javax.annotation.Nullable;

public final class UnsupportedFeaturePlan implements CompiledFeaturePlan {
    static final UnsupportedFeaturePlan INSTANCE = new UnsupportedFeaturePlan();

    private UnsupportedFeaturePlan() {
    }

    @Override
    @Nullable
    public FastFeaturePlacement open(FastPlacementContext context) {
        return null;
    }
}
