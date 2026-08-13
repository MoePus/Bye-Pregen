package com.moepus.byepregen.compat;

import com.ibm.asyncutil.util.Either;
import com.ishland.c2me.base.common.registry.SerializerAccess;
import com.mojang.logging.LogUtils;
import com.moepus.byepregen.config.ConfigParser;
import com.moepus.byepregen.gcfree.GcFreeChunkSerializer;
import com.moepus.byepregen.integration.runtime.ModEnvironment;
import org.slf4j.Logger;

public final class GcFreeCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String C2ME_SERIALIZER_ACCESS =
            "com.ishland.c2me.base.common.registry.SerializerAccess";

    private GcFreeCompat() {
    }

    public static void register() {
        if (!ConfigParser.getConfig().enableGcFreeWorldgenSave) {
            return;
        }
        if (ModEnvironment.isClassAvailable(C2ME_SERIALIZER_ACCESS)) {
            C2ME.register();
        }
    }

    public static final class C2ME {
        private C2ME() {
        }

        static void register() {
            try {
                SerializerAccess.registerSerializer((level, chunk, preferNbtCompound) -> {
                    if (!GcFreeChunkSerializer.shouldUseGcFree(chunk)) {
                        return SerializerAccess.VANILLA.serialize(level, chunk, preferNbtCompound);
                    }
                    return Either.right(GcFreeChunkSerializer.serializeRaw(level, chunk));
                });
                LOGGER.info("Registered ByePregen GC-free worldgen chunk serializer with C2ME");
            } catch (RuntimeException | LinkageError e) {
                LOGGER.warn("Failed to register ByePregen GC-free worldgen chunk serializer with C2ME", e);
            }
        }
    }
}
