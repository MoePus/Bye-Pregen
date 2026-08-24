package com.moepus.byepregen.integration.c2me;

import com.ishland.c2me.base.common.theinterface.IDirectStorage;
import com.moepus.byepregen.chunksave.storage.RawChunkData;
import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.CompletableFuture;

public final class C2MEDirectStorageCompat {
    private C2MEDirectStorageCompat() {
    }

    public static CompletableFuture<Void> setRawChunkData(Object worker, ChunkPos pos, RawChunkData data) {
        CompletableFuture<byte[]> completedData = CompletableFuture.completedFuture(data.toByteArray());
        return ((IDirectStorage) worker).setRawChunkData(pos, completedData);
    }
}
