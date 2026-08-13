package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.yalight.YAImmediateChunkAccess;
import javax.annotation.Nullable;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@MixinGate(feature = MixinFeature.YA_LIGHT)
@Mixin(value = ServerChunkCache.class, priority = 1050, remap = false)
public abstract class ServerChunkCacheYAImmediateAccessMixin implements YAImmediateChunkAccess {
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
    protected abstract ChunkHolder getVisibleChunkIfPresent(long chunkPos);

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

        LevelChunk cached = this.byepregen$getCachedFullChunk(chunkPos);
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
    private LevelChunk byepregen$getCachedFullChunk(long chunkPos) {
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
