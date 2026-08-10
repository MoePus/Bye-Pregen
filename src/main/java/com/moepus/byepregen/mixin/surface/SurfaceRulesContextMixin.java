package com.moepus.byepregen.mixin.surface;

import com.moepus.byepregen.worldgen.surface.SurfaceContextAccess;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$Context")
public interface SurfaceRulesContextMixin extends SurfaceContextAccess {
    @Override
    @Accessor("blockX")
    int byepregen$blockX();

    @Override
    @Accessor("blockY")
    int byepregen$blockY();

    @Override
    @Accessor("blockZ")
    int byepregen$blockZ();

    @Override
    @Accessor("surfaceDepth")
    int byepregen$surfaceDepth();

    @Override
    @Accessor("waterHeight")
    int byepregen$waterHeight();

    @Override
    @Accessor("stoneDepthAbove")
    int byepregen$stoneDepthAbove();

    @Override
    @Accessor("stoneDepthBelow")
    int byepregen$stoneDepthBelow();

    @Override
    @Accessor("lastUpdateXZ")
    long byepregen$lastUpdateXZ();

    @Override
    @Accessor("lastUpdateY")
    long byepregen$lastUpdateY();

    @Override
    @Accessor("biome")
    Supplier<Holder<Biome>> byepregen$biomeSupplier();

    @Override
    @Accessor("randomState")
    RandomState byepregen$randomState();

    @Override
    @Accessor("system")
    SurfaceSystem byepregen$surfaceSystem();

    @Override
    @Accessor("context")
    WorldGenerationContext byepregen$worldGenerationContext();

    @Override
    @Invoker("getSurfaceSecondary")
    double byepregen$getSurfaceSecondary();

    @Override
    @Invoker("getMinSurfaceLevel")
    int byepregen$getMinSurfaceLevel();
}
