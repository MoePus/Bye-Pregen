package com.moepus.byepregen.integration.c2me;

import com.mojang.logging.LogUtils;
import com.moepus.byepregen.chunksave.serialize.GcFreeChunkSerializer;
import com.moepus.byepregen.config.ConfigManager;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.slf4j.Logger;

public final class GcFreeCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String[] SERIALIZER_ACCESS_CLASSES = {
            "com.ishland.c2me.base.common.registry.SerializerAccess",
            "com.misanthropy.fastchunkgen.base.common.registry.SerializerAccess"
    };
    private static final boolean LOG_TEST_USAGE = Boolean.getBoolean("byepregen.testWorldGen");
    private static final AtomicBoolean TEST_USAGE_LOGGED = new AtomicBoolean();

    private GcFreeCompat() {
    }

    public static void register() {
        if (!ConfigManager.getConfig().chunkSaving().gcFreeWorldgen()) {
            return;
        }
        for (String className : SERIALIZER_ACCESS_CLASSES) {
            register(className);
        }
    }

    private static void register(String className) {
        try {
            Class<?> access = Class.forName(className, true, GcFreeCompat.class.getClassLoader());
            Method getSerializer = access.getMethod("getSerializer");
            Method registerSerializer = access.getMethod(
                    "registerSerializer", getSerializer.getReturnType());
            Object delegate = getSerializer.invoke(null);
            Class<?> serializerType = getSerializer.getReturnType();
            Method serialize = serializerType.getMethod(
                    "serialize", ServerLevel.class, ChunkAccess.class);
            Method right = serialize.getReturnType().getMethod("right", Object.class);
            Object proxy = Proxy.newProxyInstance(
                    serializerType.getClassLoader(),
                    new Class<?>[]{serializerType},
                    (instance, method, arguments) -> invoke(instance, method, arguments, delegate, right)
            );
            registerSerializer.invoke(null, proxy);
            LOGGER.info("Registered ByePregen GC-free serializer with {}", className);
        } catch (ClassNotFoundException ignored) {
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            LOGGER.warn("Failed to register ByePregen GC-free serializer with {}", className, exception);
        }
    }

    private static Object invoke(
            Object proxy,
            Method method,
            Object[] arguments,
            Object delegate,
            Method right
    ) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "toString" -> "ByePregenGcFreeSerializer";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> throw new UnsupportedOperationException(method.toString());
            };
        }
        ChunkAccess chunk = (ChunkAccess) arguments[1];
        if (GcFreeChunkSerializer.shouldUseGcFree(chunk)) {
            if (LOG_TEST_USAGE && TEST_USAGE_LOGGED.compareAndSet(false, true)) {
                LOGGER.info("ByePregen GC-free compatibility serializer wrote raw chunk data");
            }
            byte[] bytes = GcFreeChunkSerializer.serializeRaw((ServerLevel) arguments[0], chunk);
            return right.invoke(null, bytes);
        }
        try {
            return method.invoke(delegate, arguments);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
