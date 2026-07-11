package com.moepus.byepregen.mixin;

import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.PostProcess.PostProcessGenerationOptimizer;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@MixinGate(conflictingMods = "c2me_rewrites_chunk_system")
@Mixin(ChunkStatusTasks.class)
public abstract class ChunkStatusPostProcessingPreNormMixin {
    @Inject(method = "full", at = @At("HEAD"))
    private static void bpg$preNormalizeFullChunkPostProcessing(
            WorldGenContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir
    ) {
        PostProcessGenerationOptimizer.preNormalizeAndFilterChunkLocalPostProcessingLists(chunk, chunk.getPostProcessing());
    }
}
