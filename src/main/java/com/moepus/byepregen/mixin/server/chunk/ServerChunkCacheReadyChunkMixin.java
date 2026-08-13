package com.moepus.byepregen.mixin.server.chunk;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ServerChunkCache.class, priority = 1050, remap = false)
public abstract class ServerChunkCacheReadyChunkMixin {
    @Shadow
    private void storeInCache(long chunkPos, ChunkAccess chunk, ChunkStatus status) {
        throw new AssertionError();
    }

    @ModifyExpressionValue(
            method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)"
                    + "Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/server/level/ChunkHolder;currentlyLoading:"
                            + "Lnet/minecraft/world/level/chunk/LevelChunk;",
                    ordinal = 0,
                    opcode = Opcodes.GETFIELD),
            require = 1,
            allow = 1
    )
    private LevelChunk byepregen$testReadyLevelChunk(
            LevelChunk currentlyLoading,
            int chunkX,
            int chunkZ,
            ChunkStatus status,
            @Local ChunkHolder holder
    ) {
        if (currentlyLoading != null) {
            return currentlyLoading;
        }
        ChunkAccess chunk = holder.getChunkIfPresent(status);
        return chunk instanceof LevelChunk levelChunk ? levelChunk : null;
    }

    @ModifyExpressionValue(
            method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)"
                    + "Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/server/level/ChunkHolder;currentlyLoading:"
                            + "Lnet/minecraft/world/level/chunk/LevelChunk;",
                    ordinal = 1,
                    opcode = Opcodes.GETFIELD),
            require = 1,
            allow = 1
    )
    private LevelChunk byepregen$returnReadyLevelChunk(
            LevelChunk currentlyLoading,
            int chunkX,
            int chunkZ,
            ChunkStatus status,
            @Local ChunkHolder holder
    ) {
        if (currentlyLoading != null) {
            return currentlyLoading;
        }
        LevelChunk chunk = (LevelChunk) holder.getChunkIfPresent(status);
        this.storeInCache(ChunkPos.asLong(chunkX, chunkZ), chunk, status);
        return chunk;
    }
}
