package com.moepus.byepregen.mixin.accessor.chunksave;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@MixinGate(feature = MixinFeature.GC_FREE_CHUNK_SAVE)
@Mixin(targets = "net.minecraft.world.level.chunk.storage.IOWorker$PendingStore", remap = false)
public interface IOWorkerPendingStoreAccessor {
    @Accessor("result")
    CompletableFuture<Void> byepregen$result();
}
