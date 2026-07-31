package com.moepus.byepregen.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.moepus.byepregen.yalight.YAImmediateChunkAccess;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import javax.annotation.Nullable;

@Mixin(value = ServerChunkCache.class, priority = 1050, remap = false)
public abstract class ServerChunkCacheGetChunkMixin implements YAImmediateChunkAccess {
    @Shadow
    @Final
    Thread mainThread;

    @Shadow
    @Final
    public ServerLevel level;

    @Shadow
    @Final
    private long[] lastChunkPos;

    @Shadow
    @Final
    private ChunkStatus[] lastChunkStatus;

    @Shadow
    @Final
    private ChunkAccess[] lastChunk;

    @Shadow
    protected abstract ChunkHolder getVisibleChunkIfPresent(long chunkPos);

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
    private LevelChunk bpg$testReadyLevelChunk(
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
    private LevelChunk bpg$returnReadyLevelChunk(
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

    @Override
    @Nullable
    public ChunkAccess byepregen$getAnyChunkNow(int chunkX, int chunkZ) {
        long chunkPos = ChunkPos.asLong(chunkX, chunkZ);
        if (Thread.currentThread() != this.mainThread) {
            ChunkHolder holder = this.getVisibleChunkIfPresent(chunkPos);
            return holder == null ? null : holder.getLatestChunk();
        }

        LevelChunk cached = this.bpg$getCachedFullChunk(chunkPos);
        if (cached != null) {
            return cached;
        }
        ChunkHolder holder = this.getVisibleChunkIfPresent(chunkPos);
        if (holder == null) {
            return null;
        }
        if (holder.currentlyLoading != null) {
            return holder.currentlyLoading;
        }
        ChunkAccess chunk = holder.getChunkIfPresent(ChunkStatus.FULL);
        if (chunk instanceof LevelChunk levelChunk) {
            this.storeInCache(chunkPos, levelChunk, ChunkStatus.FULL);
        }
        return chunk;
    }

    @Unique
    @Nullable
    private LevelChunk bpg$getCachedFullChunk(long chunkPos) {
        if (chunkPos == this.lastChunkPos[0] && this.lastChunkStatus[0] == ChunkStatus.FULL) {
            return this.lastChunk[0] instanceof LevelChunk chunk ? chunk : null;
        }
        if (chunkPos == this.lastChunkPos[1] && this.lastChunkStatus[1] == ChunkStatus.FULL) {
            return this.lastChunk[1] instanceof LevelChunk chunk ? chunk : null;
        }
        if (chunkPos == this.lastChunkPos[2] && this.lastChunkStatus[2] == ChunkStatus.FULL) {
            return this.lastChunk[2] instanceof LevelChunk chunk ? chunk : null;
        }
        if (chunkPos == this.lastChunkPos[3] && this.lastChunkStatus[3] == ChunkStatus.FULL) {
            return this.lastChunk[3] instanceof LevelChunk chunk ? chunk : null;
        }
        return null;
    }
}
