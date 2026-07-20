package com.moepus.byepregen.mixin;

import com.moepus.byepregen.PostProcess.PostProcessGenerationOptimizer;
import com.moepus.byepregen.MixinGate;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;
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

@MixinGate(conflictingMods = "c2me_rewrites_chunk_system")
@Mixin(ChunkStatus.class)
public abstract class ChunkStatusPostProcessingPreNormMixin {
    @Inject(
            method = "generate",
            at = @At(value = "HEAD")
    )
    private void bpg$preNormalizeFullChunkPostProcessing(
            Executor executor,
            ServerLevel level,
            ChunkGenerator generator,
            StructureTemplateManager structureTemplateManager,
            ThreadedLevelLightEngine lightEngine,
            Function<?, ?> function,
            List<ChunkAccess> chunks,
            CallbackInfoReturnable<?> cir
    ) {
        if ((Object) this != ChunkStatus.FULL) {
            return;
        }
        ChunkAccess chunk = chunks.get(chunks.size() / 2);
        PostProcessGenerationOptimizer.preNormalizeAndFilterChunkLocalPostProcessingLists(
                chunk, chunk.getPostProcessing());
    }
}
