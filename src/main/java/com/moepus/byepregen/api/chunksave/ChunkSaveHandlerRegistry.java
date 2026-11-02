package com.moepus.byepregen.api.chunksave;

import com.mojang.logging.LogUtils;
import com.moepus.byepregen.chunksave.compat.ChunkSaveHookGate;
import com.moepus.byepregen.config.ConfigManager;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.slf4j.Logger;

/**
 * Explicit SPI for custom root fields that the raw GC-free chunk serializer does not write.
 *
 * <p>Handlers run in id order so the produced byte stream does not depend on registration order.
 * Register during mod construction. Registering an id twice is harmless: the first handler wins.</p>
 */
public final class ChunkSaveHandlerRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Object LOCK = new Object();
    private static final NavigableMap<ResourceLocation, ChunkSaveHandler> HANDLERS = new TreeMap<>();
    private static volatile List<ChunkSaveHandler> SNAPSHOT = List.of();

    private ChunkSaveHandlerRegistry() {
    }

    /**
     * Whether registered handlers are called at all.
     *
     * <p>Handlers only run on the raw GC-free save path. This returns {@code false} when the
     * chunk-saving config disables that path, when a save hook forces ByePregen back to vanilla
     * serialization, or before the config has been initialized. Even when this returns
     * {@code true}, individual chunks can still be written by the vanilla serializer (for example
     * chunks that carry block entities), so handlers must not assume they run for every save.</p>
     *
     * <p>The result reflects the state at call time; the save hook gate is decided on first use,
     * so call this after mod loading has finished, not from mod construction.</p>
     */
    public static boolean isActive() {
        boolean configAllowsRawSave;
        try {
            configAllowsRawSave = ConfigManager.getConfig().chunkSaving().gcFreeWorldgen();
        } catch (IllegalStateException notInitializedYet) {
            return false;
        }
        return configAllowsRawSave && ChunkSaveHookGate.CAN_USE_RAW_SAVE;
    }

    /**
     * Registers a handler for {@code id}. Registering an already registered id is harmless: the
     * first handler is kept and a different one is reported once.
     */
    public static void register(ResourceLocation id, ChunkSaveHandler handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        synchronized (LOCK) {
            ChunkSaveHandler existing = HANDLERS.putIfAbsent(id, handler);
            if (existing != null) {
                if (existing != handler) {
                    LOGGER.warn("Chunk save handler {} is already registered; keeping the first one", id);
                }
                return;
            }
            SNAPSHOT = List.copyOf(HANDLERS.values());
        }
    }

    /**
     * Removes a handler. Intended for tests and reload tooling; not for use while chunks are being
     * saved.
     *
     * @return whether {@code id} was registered
     */
    public static boolean unregister(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        synchronized (LOCK) {
            if (HANDLERS.remove(id) == null) {
                return false;
            }
            SNAPSHOT = List.copyOf(HANDLERS.values());
            return true;
        }
    }

    /**
     * Internal entry point used by the raw serializer. Mods should not call this.
     */
    public static void write(ServerLevel level, ChunkAccess chunk, ChunkSaveWriter writer) {
        for (var handler : SNAPSHOT) {
            handler.write(level, chunk, writer);
        }
    }
}
