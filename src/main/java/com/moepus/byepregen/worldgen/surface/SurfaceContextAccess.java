package com.moepus.byepregen.worldgen.surface;

import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

public interface SurfaceContextAccess {
    int byepregen$blockX();

    int byepregen$blockY();

    int byepregen$blockZ();

    int byepregen$surfaceDepth();

    int byepregen$waterHeight();

    int byepregen$stoneDepthAbove();

    int byepregen$stoneDepthBelow();

    boolean byepregen$isStoneDepthBelowAtMostOne();

    long byepregen$lastUpdateXZ();

    RandomState byepregen$randomState();

    SurfaceSystem byepregen$surfaceSystem();

    WorldGenerationContext byepregen$worldGenerationContext();

    double byepregen$getSurfaceSecondary();

    int byepregen$getMinSurfaceLevel();
}
