package com.moepus.byepregen.mixin.postprocess.compat.c2me;

import com.ishland.c2me.rewrites.chunksystem.common.ChunkLoadingContext;
import com.ishland.c2me.rewrites.chunksystem.common.statuses.ServerBlockTicking;
import com.ishland.flowsched.scheduler.Cancellable;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.postprocess.PostProcessGenerationOptimizer;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.objectweb.asm.Opcodes;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@MixinGate(requiredMods = "c2me_rewrites_chunk_system")
@Mixin(value = ServerBlockTicking.class, remap = false)
public abstract class C2MEServerBlockTickingMixin {
    @InjectLite(method = "upgradeToThis(Lcom/ishland/c2me/rewrites/chunksystem/common/ChunkLoadingContext;Lcom/ishland/flowsched/scheduler/Cancellable;)Lio/reactivex/rxjava3/core/Completable;", at = @At("HEAD"), require = 0, remap = false)
    private void byepregen$preNormalizePostProcessingLists(
            ChunkLoadingContext context,
            Cancellable cancellable
    ) {
        ChunkAccess chunk = context.holder().getItem().get().chunk();
        PostProcessGenerationOptimizer.preNormalizeAndFilterChunkLocalPostProcessingLists(chunk, chunk.getPostProcessing());
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
    private boolean byepregen$disableC2MEFluidPostProcessingFilter() {
        return false;
    }
}
