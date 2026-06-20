package com.moepus.byepregen.mixin.compat;

import com.moepus.byepregen.PostProcess.PostProcessGenerationOptimizer;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.ishland.c2me.rewrites.chunksystem.common.statuses.ServerBlockTicking", remap = false)
public abstract class C2MEServerBlockTickingMixin {
    @Inject(method = "upgradeToThis(Lcom/ishland/c2me/rewrites/chunksystem/common/ChunkLoadingContext;Lcom/ishland/flowsched/scheduler/Cancellable;)Lio/reactivex/rxjava3/core/Completable;", at = @At("HEAD"), require = 0, remap = false)
    private void byepregen$preNormalizePostProcessingLists(
            Object context,
            Object cancellable,
            CallbackInfoReturnable<?> cir
    ) {
        ChunkAccess chunk = byepregen$getChunk(context);
        if (chunk != null) {
            PostProcessGenerationOptimizer.preNormalizeAndFilterChunkLocalPostProcessingLists(chunk, chunk.getPostProcessing());
        }
    }

    @Redirect(
            method = "upgradeToThis(Lcom/ishland/c2me/rewrites/chunksystem/common/ChunkLoadingContext;Lcom/ishland/flowsched/scheduler/Cancellable;)Lio/reactivex/rxjava3/core/Completable;",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/ishland/c2me/rewrites/chunksystem/common/Config;filterFluidPostProcessing:Z",
                    opcode = Opcodes.GETSTATIC
            ),
            require = 0
    )
    private boolean c6c$disableC2MEFluidPostProcessingFilter() {
        return false;
    }

    @Unique
    private ChunkAccess byepregen$getChunk(Object context) {
        try {
            Object holder = context.getClass().getMethod("holder").invoke(context);
            Object item = holder.getClass().getMethod("getItem").invoke(holder);
            Object generationChunk = item.getClass().getMethod("get").invoke(item);
            Object chunk = generationChunk.getClass().getMethod("chunk").invoke(generationChunk);
            return chunk instanceof ChunkAccess chunkAccess ? chunkAccess : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
