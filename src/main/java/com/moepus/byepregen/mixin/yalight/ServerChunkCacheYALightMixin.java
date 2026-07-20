package com.moepus.byepregen.mixin.yalight;

import com.mojang.datafixers.util.Either;
import com.moepus.byepregen.yalight.YAImmediateChunkAccess;
import javax.annotation.Nullable;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheYALightMixin implements YAImmediateChunkAccess {
    @Shadow
    @Final
    Thread mainThread;

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
    @Nullable
    private ChunkHolder getVisibleChunkIfPresent(long chunkPos) {
        throw new AssertionError();
    }

    @Override
    @Nullable
    public ChunkAccess byepregen$getAnyChunkNow(int chunkX, int chunkZ) {
        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        if (Thread.currentThread() == this.mainThread) {
            LevelChunk cached = this.byepregen$getCachedFullChunk(chunkKey);
            if (cached != null) {
                return cached;
            }
        }

        ChunkHolder holder = this.getVisibleChunkIfPresent(chunkKey);
        if (holder == null) {
            return null;
        }

        LevelChunk currentlyLoading = ((ChunkHolderYALightAccessor) holder).byepregen$getCurrentlyLoading();
        if (currentlyLoading != null) {
            return currentlyLoading;
        }

        Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> result =
                holder.getFutureIfPresent(ChunkStatus.FULL).getNow(null);
        if (result != null) {
            ChunkAccess chunk = result.left().orElse(null);
            if (chunk != null) {
                return chunk;
            }
        }

        ChunkAccess chunk = holder.getLastAvailable();
        return chunk instanceof ImposterProtoChunk imposter ? imposter.getWrapped() : chunk;
    }

    @Unique
    @Nullable
    private LevelChunk byepregen$getCachedFullChunk(long chunkKey) {
        for (int i = 0; i < this.lastChunkPos.length; ++i) {
            if (chunkKey == this.lastChunkPos[i] && this.lastChunkStatus[i] == ChunkStatus.FULL) {
                return this.lastChunk[i] instanceof LevelChunk chunk ? chunk : null;
            }
        }
        return null;
    }
}
