package com.moepus.byepregen.compat;

import com.moepus.byepregen.gcfree.RawChunkData;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.ChunkPos;

public final class C2MEDirectStorageCompat {
    private static final String DIRECT_STORAGE =
            "com.ishland.c2me.base.common.theinterface.IDirectStorage";
    private static final Class<?> DIRECT_STORAGE_CLASS = findClass(DIRECT_STORAGE);
    private static final Method SET_RAW_CHUNK_DATA = findSetRawChunkData(DIRECT_STORAGE_CLASS);

    private C2MEDirectStorageCompat() {
    }

    public static boolean isDirectStorage(Object worker) {
        return DIRECT_STORAGE_CLASS != null && DIRECT_STORAGE_CLASS.isInstance(worker) && SET_RAW_CHUNK_DATA != null;
    }

    @SuppressWarnings("unchecked")
    public static CompletableFuture<Void> setRawChunkData(Object worker, ChunkPos pos, RawChunkData data) {
        try {
            return (CompletableFuture<Void>) SET_RAW_CHUNK_DATA.invoke(worker, pos, data.toByteArray());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(exception);
            return failed;
        }
    }

    private static Method findSetRawChunkData(Class<?> directStorageClass) {
        if (directStorageClass == null) {
            return null;
        }
        try {
            return directStorageClass.getMethod("setRawChunkData", ChunkPos.class, byte[].class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name, false, C2MEDirectStorageCompat.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
