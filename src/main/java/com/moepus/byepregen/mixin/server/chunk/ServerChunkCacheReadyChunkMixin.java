package com.moepus.byepregen.mixin.server.chunk;

import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.integration.forge.ChunkHolderCompat;
import javax.annotation.Nullable;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@MixinGate(conflictingMods = {"harium", "lithium"})
@Mixin(value = ServerChunkCache.class, priority = 1050)
public abstract class ServerChunkCacheReadyChunkMixin {
    @Shadow
    @Nullable
    private ChunkHolder getVisibleChunkIfPresent(long chunkPos) {
        throw new AssertionError();
    }

    @Shadow
    private void storeInCache(long chunkPos, ChunkAccess chunk, ChunkStatus status) {
        throw new AssertionError();
    }

    @Inject(
            method = "getChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerChunkCache;getChunkFutureMainThread("
                            + "IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Ljava/util/concurrent/CompletableFuture;"
            ),
            cancellable = true
    )
    private void byepregen$returnReadyChunk(
            int chunkX,
            int chunkZ,
            ChunkStatus status,
            boolean load,
            CallbackInfoReturnable<ChunkAccess> cir
    ) {
        long chunkPos = ChunkPos.asLong(chunkX, chunkZ);
        ChunkHolder holder = this.getVisibleChunkIfPresent(chunkPos);
        ChunkAccess chunk = holder == null ? null : ChunkHolderCompat.getIfPresent(holder, status);
        if (chunk != null) {
            this.storeInCache(chunkPos, chunk, status);
            cir.setReturnValue(chunk);
        }
    }
}
