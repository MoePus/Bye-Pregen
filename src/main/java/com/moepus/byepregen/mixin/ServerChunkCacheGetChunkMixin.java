package com.moepus.byepregen.mixin;

import com.moepus.byepregen.compat.C2MECompat;
import com.moepus.byepregen.compat.ServerChunkCacheAccess;
import com.moepus.byepregen.jfr.ByepregenJfrEvents;
import com.moepus.byepregen.mixin.accessor.ServerChunkCacheAccessor;
import com.moepus.byepregen.yalight.YAImmediateChunkAccess;
import net.minecraft.Util;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

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
    public ChunkMap chunkMap;

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
    public abstract CompletableFuture<ChunkResult<ChunkAccess>> getChunkFuture(
            int chunkX, int chunkZ, ChunkStatus status, boolean requireChunk);

    @Shadow
    private void storeInCache(long chunkPos, ChunkAccess chunk, ChunkStatus status) {
        throw new AssertionError();
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

    /**
     * @author MoePus
     * @reason Avoid C2ME's whole-method getChunk WrapMethod on the main-thread cache-hit path.
     */
    @Nullable
    @Overwrite
    public ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus status, boolean requireChunk) {
        long chunkPos = ChunkPos.asLong(chunkX, chunkZ);
        if (Thread.currentThread() != this.mainThread) {
            return this.bpg$getChunkOffThread(chunkX, chunkZ, status, requireChunk, chunkPos);
        }

        long[] cachedPositions = this.lastChunkPos;
        ChunkStatus[] cachedStatuses = this.lastChunkStatus;
        if (chunkPos == cachedPositions[0] && status == cachedStatuses[0]) {
            ChunkAccess chunk = this.lastChunk[0];
            if (chunk != null || !requireChunk) return chunk;
        }
        if (chunkPos == cachedPositions[1] && status == cachedStatuses[1]) {
            ChunkAccess chunk = this.lastChunk[1];
            if (chunk != null || !requireChunk) return chunk;
        }
        if (chunkPos == cachedPositions[2] && status == cachedStatuses[2]) {
            ChunkAccess chunk = this.lastChunk[2];
            if (chunk != null || !requireChunk) return chunk;
        }
        if (chunkPos == cachedPositions[3] && status == cachedStatuses[3]) {
            ChunkAccess chunk = this.lastChunk[3];
            if (chunk != null || !requireChunk) return chunk;
        }

        ChunkHolder holder = this.getVisibleChunkIfPresent(chunkPos);
        if (holder != null) {
            if (holder.currentlyLoading != null)
                return holder.currentlyLoading;
            ChunkAccess chunk = holder.getChunkIfPresent(status);
            if (chunk instanceof LevelChunk levelChunk) {
                this.storeInCache(chunkPos, levelChunk, status);
                return levelChunk;
            }
        }

        return this.bpg$getChunkOnMainThread(chunkX, chunkZ, status, requireChunk, chunkPos);
    }

    @Unique
    private ChunkAccess bpg$getReadyChunkOffThread(long chunkPos, ChunkStatus status) {
        ChunkHolder holder = this.getVisibleChunkIfPresent(chunkPos);
        if (holder == null || holder.getTicketLevel() > ChunkLevel.byStatus(status)) {
            return null;
        }

        if (holder.currentlyLoading != null) {
            return holder.currentlyLoading;
        }
        ChunkAccess chunk = holder.getChunkIfPresent(status);
        return chunk instanceof ImposterProtoChunk readOnlyChunk ? readOnlyChunk.getWrapped() : chunk;
    }

    @Unique
    private ChunkAccess bpg$getChunkOffThread(
            int chunkX, int chunkZ, ChunkStatus status, boolean requireChunk, long chunkPos) {
        ChunkAccess readyChunk = this.bpg$getReadyChunkOffThread(chunkPos, status);
        return readyChunk != null
                ? readyChunk
                : this.bpg$joinChunkFuture(
                        this.getChunkFuture(chunkX, chunkZ, status, requireChunk),
                        chunkPos,
                        status,
                        false,
                        requireChunk);
    }

    @Unique
    private ChunkAccess bpg$getChunkOnMainThread(
            int chunkX, int chunkZ, ChunkStatus status, boolean requireChunk, long chunkPos) {
        ServerChunkCacheAccessor accessor = (ServerChunkCacheAccessor) this;
        CompletableFuture<ChunkResult<ChunkAccess>> future =
                accessor.byepregen$getChunkFutureMainThread(chunkX, chunkZ, status, requireChunk);
        if (!future.isDone()) {
            this.bpg$managedBlockWithSyncLoad(future, chunkX, chunkZ, status, requireChunk);
        }
        return this.bpg$joinChunkFuture(future, chunkPos, status, true, requireChunk);
    }

    @Unique
    private void bpg$managedBlockWithSyncLoad(
            CompletableFuture<ChunkResult<ChunkAccess>> future,
            int chunkX,
            int chunkZ,
            ChunkStatus status,
            boolean requireChunk) {
        ServerChunkCache cache = (ServerChunkCache) (Object) this;
        boolean c2me = C2MECompat.isC2MEInstalled();
        ByepregenJfrEvents.SyncChunkWaitEvent event = ByepregenJfrEvents.beginSyncChunkWait(
                chunkX,
                chunkZ,
                status.toString(),
                requireChunk,
                c2me);
        try {
            if (!c2me) {
                ServerChunkCacheAccess.mainThreadProcessor(cache).managedBlock(future::isDone);
                return;
            }

            C2MECompat.managedBlockWithSyncLoad(
                    cache,
                    this.chunkMap,
                    future,
                    chunkX,
                    chunkZ);
        } finally {
            ByepregenJfrEvents.commit(event, future.isDone());
        }
    }

    @Unique
    private ChunkAccess bpg$joinChunkFuture(
            CompletableFuture<ChunkResult<ChunkAccess>> future,
            long chunkPos,
            ChunkStatus status,
            boolean cacheResult,
            boolean requireChunk) {
        ChunkResult<ChunkAccess> result = future.join();
        ChunkAccess chunk = result.orElse(null);
        if (chunk == null && requireChunk) {
            throw (IllegalStateException) Util.pauseInIde(
                    new IllegalStateException("Chunk not there when requested: " + result.getError()));
        }
        if (cacheResult) {
            this.storeInCache(chunkPos, chunk, status);
        }
        return chunk;
    }
}
