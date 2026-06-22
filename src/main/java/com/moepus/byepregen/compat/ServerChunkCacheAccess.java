package com.moepus.byepregen.compat;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.util.thread.BlockableEventLoop;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public final class ServerChunkCacheAccess {
    private static final VarHandle MAIN_THREAD_PROCESSOR = getVarHandle("mainThreadProcessor");

    private ServerChunkCacheAccess() {
    }

    @SuppressWarnings("unchecked")
    public static BlockableEventLoop<Runnable> mainThreadProcessor(ServerChunkCache cache) {
        return (BlockableEventLoop<Runnable>) MAIN_THREAD_PROCESSOR.get(cache);
    }

    private static VarHandle getVarHandle(String name) {
        try {
            Field field = ServerChunkCache.class.getDeclaredField(name);
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(ServerChunkCache.class, MethodHandles.lookup());
            return lookup.findVarHandle(ServerChunkCache.class, name, field.getType());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to find ServerChunkCache." + name, e);
        }
    }
}
