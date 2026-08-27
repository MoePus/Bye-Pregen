package com.moepus.byepregen.integration.forge;

import com.mojang.datafixers.util.Either;
import com.moepus.byepregen.mixin.accessor.server.chunk.ChunkHolderForgeAccessor;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

public final class ChunkHolderCompat {
    private ChunkHolderCompat() {
    }

    public static ChunkAccess getIfPresent(ChunkHolder holder, ChunkStatus status) {
        LevelChunk loading = ((ChunkHolderForgeAccessor) holder).byepregen$getCurrentlyLoading();
        if (loading != null) {
            return loading;
        }
        Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> result =
                holder.getFutureIfPresent(status).getNow(null);
        return result == null ? null : result.left().orElse(null);
    }

    public static ChunkAccess getLatest(ChunkHolder holder) {
        LevelChunk loading = ((ChunkHolderForgeAccessor) holder).byepregen$getCurrentlyLoading();
        return loading != null ? loading : holder.getLastAvailable();
    }
}
