package com.moepus.byepregen.test;

import java.lang.reflect.InvocationTargetException;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;

final class NoTickSystemBridge {
    private static final String CLASS_NAME = "com.ishland.c2me.notickvd.common.NoTickSystem";

    private final Object delegate;
    private final Class<?> type;

    NoTickSystemBridge(ChunkMap chunkMap) {
        try {
            this.type = Class.forName(CLASS_NAME);
            this.delegate = this.type.getConstructor(ChunkMap.class).newInstance(chunkMap);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("C2ME NoTickSystem is unavailable", exception);
        }
    }

    void setViewDistance(int radius) {
        this.invoke("setNoTickViewDistance", int.class, radius);
    }

    void addPlayerSource(ChunkPos center) {
        this.invoke("addPlayerSource", ChunkPos.class, center);
    }

    void beforeTicketTicks() {
        this.invoke("beforeTicketTicks");
    }

    void afterTicketTicks() {
        this.invoke("afterTicketTicks");
    }

    void tick() {
        this.invoke("tick");
    }

    void close() {
        this.invoke("close");
    }

    private void invoke(String name) {
        try {
            this.type.getMethod(name).invoke(this.delegate);
        } catch (ReflectiveOperationException exception) {
            throw invocationFailure(name, exception);
        }
    }

    private void invoke(String name, Class<?> parameterType, Object argument) {
        try {
            this.type.getMethod(name, parameterType).invoke(this.delegate, argument);
        } catch (ReflectiveOperationException exception) {
            throw invocationFailure(name, exception);
        }
    }

    private static RuntimeException invocationFailure(String name, ReflectiveOperationException exception) {
        Throwable cause = exception instanceof InvocationTargetException invocation
                ? invocation.getCause()
                : exception;
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("Failed to invoke C2ME NoTickSystem." + name, cause);
    }
}
