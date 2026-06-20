package com.moepus.byepregen.compat;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class C2MEChunkAccess {
    private static final String CONTEXT_CLASS =
            "com.ishland.c2me.rewrites.chunksystem.common.ChunkLoadingContext";
    private static final String ITEM_HOLDER_CLASS =
            "com.ishland.flowsched.scheduler.ItemHolder";
    private static final String CHUNK_STATE_CLASS =
            "com.ishland.c2me.rewrites.chunksystem.common.ChunkState";

    private static final VarHandle HOLDER_HANDLE;
    private static final VarHandle ITEM_HANDLE;
    private static final VarHandle CHUNK_HANDLE;
    private static final boolean AVAILABLE;

    static {
        VarHandle holderHandle = null;
        VarHandle itemHandle = null;
        VarHandle chunkHandle = null;
        boolean available = false;

        try {
            ClassLoader loader = classLoader();
            holderHandle = findVarHandle(Class.forName(CONTEXT_CLASS, false, loader), "holder");
            itemHandle = findVarHandle(Class.forName(ITEM_HOLDER_CLASS, false, loader), "item");
            chunkHandle = findVarHandle(Class.forName(CHUNK_STATE_CLASS, false, loader), "chunk");
            available = true;
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
        }

        HOLDER_HANDLE = holderHandle;
        ITEM_HANDLE = itemHandle;
        CHUNK_HANDLE = chunkHandle;
        AVAILABLE = available;
    }

    private C2MEChunkAccess() {
    }

    public static ChunkAccess getChunk(Object context) {
        if (!AVAILABLE) {
            return null;
        }

        try {
            Object holder = HOLDER_HANDLE.get(context);
            AtomicReference<?> item = holder == null ? null : (AtomicReference<?>) ITEM_HANDLE.get(holder);
            Object chunkState = item == null ? null : item.get();
            Object chunk = chunkState == null ? null : CHUNK_HANDLE.get(chunkState);
            return chunk instanceof ChunkAccess chunkAccess ? chunkAccess : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static ClassLoader classLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader != null ? loader : C2MEChunkAccess.class.getClassLoader();
    }

    private static VarHandle findVarHandle(Class<?> owner, String fieldName)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(fieldName);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
        return lookup.findVarHandle(owner, fieldName, field.getType());
    }
}
