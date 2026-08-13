package com.moepus.byepregen.mixin.accessor.chunksave;

import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.chunk.storage.IOWorker$PendingStore", remap = false)
public interface IOWorkerPendingStoreAccessor {
    @Accessor("result")
    CompletableFuture<Void> byepregen$result();
}
