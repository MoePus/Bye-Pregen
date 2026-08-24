package com.moepus.byepregen.mixin.postprocess;

import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.postprocess.PostProcessGenerationOptimizer;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@MixinGate(conflictingMods = "c2me")
@Mixin(ChunkStatusTasks.class)
public abstract class ChunkStatusPostProcessingPreNormMixin {
    @InjectLite(method = "full", at = @At("HEAD"))
    private static void byepregen$preNormalizeFullChunkPostProcessing(
            WorldGenContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk
    ) {
        PostProcessGenerationOptimizer.preNormalizeAndFilterChunkLocalPostProcessingLists(chunk, chunk.getPostProcessing());
    }
}
