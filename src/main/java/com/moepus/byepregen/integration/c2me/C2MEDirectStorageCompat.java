package com.moepus.byepregen.integration.c2me;

import com.moepus.byepregen.chunksave.storage.RawChunkData;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.ChunkPos;

public final class C2MEDirectStorageCompat {
    private C2MEDirectStorageCompat() {
    }

    @SuppressWarnings("unchecked")
    public static CompletableFuture<Void> setRawChunkData(
            Object worker,
            ChunkPos pos,
            RawChunkData data
    ) {
        try {
            Method method = worker.getClass().getMethod(
                    "setRawChunkData", ChunkPos.class, byte[].class);
            return (CompletableFuture<Void>) method.invoke(worker, pos, data.toByteArray());
        } catch (InvocationTargetException exception) {
            return CompletableFuture.failedFuture(exception.getCause());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}
