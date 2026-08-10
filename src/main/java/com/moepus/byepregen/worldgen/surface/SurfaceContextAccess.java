package com.moepus.byepregen.worldgen.surface;

import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
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

    long byepregen$lastUpdateXZ();

    long byepregen$lastUpdateY();

    Supplier<Holder<Biome>> byepregen$biomeSupplier();

    RandomState byepregen$randomState();

    SurfaceSystem byepregen$surfaceSystem();

    WorldGenerationContext byepregen$worldGenerationContext();

    double byepregen$getSurfaceSecondary();

    int byepregen$getMinSurfaceLevel();
}
