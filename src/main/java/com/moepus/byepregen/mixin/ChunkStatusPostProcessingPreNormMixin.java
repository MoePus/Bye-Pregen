package com.moepus.byepregen.mixin;

import com.mojang.datafixers.util.Either;
import com.moepus.byepregen.PostProcess.PostProcessGenerationOptimizer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkStatus.class)
public abstract class ChunkStatusPostProcessingPreNormMixin {
    @Inject(
            method = "generate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkStatus$GenerationTask;doWork(Lnet/minecraft/world/level/chunk/ChunkStatus;Ljava/util/concurrent/Executor;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/server/level/ThreadedLevelLightEngine;Ljava/util/function/Function;Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private void bpg$preNormalizeFullChunkPostProcessing(
            Executor executor,
            ServerLevel level,
            ChunkGenerator generator,
            StructureTemplateManager structureTemplateManager,
            ThreadedLevelLightEngine lightEngine,
            Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> fullChunkConverter,
            List<ChunkAccess> chunks,
            CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir
    ) {
        if ((Object) this != ChunkStatus.FULL) {
            return;
        }

        ChunkAccess chunk = chunks.get(chunks.size() / 2);
        PostProcessGenerationOptimizer.preNormalizeAndFilterChunkLocalPostProcessingLists(chunk, chunk.getPostProcessing());
    }
}
