package com.moepus.byepregen.mixin.surface;

import com.moepus.byepregen.worldgen.surface.SurfaceContextAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$Context")
public abstract class SurfaceRulesContextMixin implements SurfaceContextAccess {
    @Shadow
    long lastUpdateY;

    @Unique
    private final BlockPos.MutableBlockPos byepregen$stoneDepthPosition =
            new BlockPos.MutableBlockPos();

    @Unique
    private long byepregen$stoneDepthUpdateY = Long.MIN_VALUE;

    @Unique
    private boolean byepregen$stoneDepthBelowAtMostOne;

    @Override
    @Accessor("blockX")
    public abstract int byepregen$blockX();

    @Override
    @Accessor("blockY")
    public abstract int byepregen$blockY();

    @Override
    @Accessor("blockZ")
    public abstract int byepregen$blockZ();

    @Override
    @Accessor("surfaceDepth")
    public abstract int byepregen$surfaceDepth();

    @Override
    @Accessor("waterHeight")
    public abstract int byepregen$waterHeight();

    @Override
    @Accessor("stoneDepthAbove")
    public abstract int byepregen$stoneDepthAbove();

    @Override
    @Accessor("stoneDepthBelow")
    public abstract int byepregen$stoneDepthBelow();

    @Override
    @Accessor("lastUpdateXZ")
    public abstract long byepregen$lastUpdateXZ();

    @Override
    @Accessor("randomState")
    public abstract RandomState byepregen$randomState();

    @Override
    @Accessor("system")
    public abstract SurfaceSystem byepregen$surfaceSystem();

    @Override
    @Accessor("context")
    public abstract WorldGenerationContext byepregen$worldGenerationContext();

    @Accessor("chunk")
    public abstract ChunkAccess byepregen$chunk();

    @Override
    @Invoker("getSurfaceSecondary")
    public abstract double byepregen$getSurfaceSecondary();

    @Override
    @Invoker("getMinSurfaceLevel")
    public abstract int byepregen$getMinSurfaceLevel();

    @Override
    @Unique
    public boolean byepregen$isStoneDepthBelowAtMostOne() {
        if (this.byepregen$stoneDepthUpdateY != this.lastUpdateY) {
            BlockState state = this.byepregen$chunk().getBlockState(
                    this.byepregen$stoneDepthPosition.set(
                            this.byepregen$blockX(),
                            this.byepregen$blockY() - 1,
                            this.byepregen$blockZ()
                    )
            );
            this.byepregen$stoneDepthBelowAtMostOne =
                    state.isAir() || !state.getFluidState().isEmpty();
            this.byepregen$stoneDepthUpdateY = this.lastUpdateY;
        }
        return this.byepregen$stoneDepthBelowAtMostOne;
    }
}
