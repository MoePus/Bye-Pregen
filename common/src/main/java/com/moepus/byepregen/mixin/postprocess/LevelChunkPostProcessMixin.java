package com.moepus.byepregen.mixin.postprocess;

import com.moepus.byepregen.worldgen.postprocess.PostProcessGenerationOptimizer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelChunk.class)
public abstract class LevelChunkPostProcessMixin {
    /** Orders and deduplicates the pending positions before the native walk below runs. */
    @InjectLite(method = "postProcessGeneration", at = @At("HEAD"))
    private void byepregen$sortPostProcessingLists(ServerLevel level) {
        PostProcessGenerationOptimizer.preprocessPostProcessingLists((LevelChunk) (Object) this,
                ((ChunkAccess) (Object) this).getPostProcessing());
    }

    @Redirect(method = "postProcessGeneration", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;updateFromNeighbourShapes(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState byepregen$reduceNeighborDispatch(BlockState state, LevelAccessor level, BlockPos pos) {
        return PostProcessGenerationOptimizer.updateFromNeighbourShapes(state, level, pos);
    }
}
