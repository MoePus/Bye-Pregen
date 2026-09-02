package com.moepus.byepregen.integration.forge;

import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;

public final class ChunkHolderCompat {
    private ChunkHolderCompat() {
    }

    public static ChunkAccess getIfPresent(ChunkHolder holder, ChunkStatus status) {
        Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> result =
                holder.getFutureIfPresent(status).getNow(null);
        return result == null ? null : result.left().orElse(null);
    }

    public static ChunkAccess getLatest(ChunkHolder holder) {
        return holder.getLastAvailable();
    }
}
