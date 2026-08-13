package com.moepus.byepregen.mixin;

import com.moepus.byepregen.PostProcess.PostProcessGenerationOptimizer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.jetbrains.annotations.Nullable;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(value = LevelChunk.class, remap = false)
public abstract class LevelChunkPostProcessMixin extends ChunkAccess {
    public LevelChunkPostProcessMixin(ChunkPos p_187621_, UpgradeData p_187622_, LevelHeightAccessor p_187623_, Registry<Biome> p_187624_, long p_187625_, @Nullable LevelChunkSection[] p_187626_, @Nullable BlendingData p_187627_) {
        super(p_187621_, p_187622_, p_187623_, p_187624_, p_187625_, p_187626_, p_187627_);
    }

    @InjectLite(method = "postProcessGeneration", at = @At("HEAD"))
    private void c6c$preprocessPostProcessingLists() {
        PostProcessGenerationOptimizer.preprocessPostProcessingLists((LevelChunk) (Object) this, this.postProcessing);
    }

    @Redirect(
            method = "postProcessGeneration",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Block;updateFromNeighbourShapes(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
            )
    )
    private BlockState c6c$skipNoOpNeighbourShapeUpdates(BlockState state, LevelAccessor level, BlockPos pos) {
        return PostProcessGenerationOptimizer.updateFromNeighbourShapes(state, level, pos);
    }
}
