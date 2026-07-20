package com.moepus.byepregen.compat;

import com.mojang.logging.LogUtils;
import com.moepus.byepregen.ConfigParser;
import com.moepus.byepregen.gcfree.GcFreeChunkSerializer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.slf4j.Logger;

public final class GcFreeCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String C2ME_SERIALIZER_ACCESS =
            "com.ishland.c2me.base.common.registry.SerializerAccess";
    private static final String C2ME_SERIALIZER =
            "com.ishland.c2me.base.common.registry.SerializerAccess$Serializer";
    private static final String C2ME_EITHER =
            "com.ibm.asyncutil.util.Either";

    private GcFreeCompat() {
    }

    public static void register() {
        if (!ConfigParser.getConfig().enableGcFreeWorldgenSave) {
            return;
        }
        LOGGER.info("ByePregen GC-free worldgen save is enabled");
        registerC2MESerializer();
    }

    private static void registerC2MESerializer() {
        try {
            ClassLoader loader = GcFreeCompat.class.getClassLoader();
            Class<?> serializerAccessClass = Class.forName(C2ME_SERIALIZER_ACCESS, false, loader);
            Class<?> serializerClass = Class.forName(C2ME_SERIALIZER, false, loader);
            Class<?> eitherClass = Class.forName(C2ME_EITHER, false, loader);
            Method getSerializer = serializerAccessClass.getMethod("getSerializer");
            Method registerSerializer = serializerAccessClass.getMethod("registerSerializer", serializerClass);
            Method eitherRight = eitherClass.getMethod("right", Object.class);
            Object fallbackSerializer = getSerializer.invoke(null);
            InvocationHandler handler = (proxy, method, args) -> invokeSerializer(
                    proxy, fallbackSerializer, eitherRight, method, args);
            Object serializer = Proxy.newProxyInstance(loader, new Class<?>[]{serializerClass}, handler);
            registerSerializer.invoke(null, serializer);
            LOGGER.info("Registered ByePregen GC-free worldgen chunk serializer with C2ME");
        } catch (ClassNotFoundException ignored) {
            // C2ME is optional; the vanilla Forge save hook remains active when it is absent.
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            LOGGER.warn("Failed to register ByePregen GC-free worldgen chunk serializer with C2ME", exception);
        }
    }

    private static Object invokeSerializer(
            Object proxy, Object fallbackSerializer, Method eitherRight, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return invokeObjectMethod(proxy, method, args);
        }
        if (!"serialize".equals(method.getName())
                || args == null
                || args.length < 2
                || !(args[0] instanceof ServerLevel level)
                || !(args[1] instanceof ChunkAccess chunk)) {
            return invokeFallback(fallbackSerializer, method, args);
        }
        if (!GcFreeChunkSerializer.shouldUseGcFree(chunk)) {
            return invokeFallback(fallbackSerializer, method, args);
        }
        return eitherRight.invoke(null, GcFreeChunkSerializer.serializeRaw(level, chunk));
    }

    private static Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "ByePregen C2ME GC-free serializer";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> args != null && args.length == 1 && args[0] == proxy;
            default -> throw new UnsupportedOperationException(method.toString());
        };
    }

    private static Object invokeFallback(Object fallbackSerializer, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(fallbackSerializer, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
