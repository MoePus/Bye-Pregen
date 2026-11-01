package com.moepus.byepregen.api.chunksave;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Small explicit SPI for custom fields omitted by the raw GC-free chunk serializer. */
public final class ChunkSaveHandlerRegistry {
    private static final Object LOCK = new Object();
    private static final Map<ResourceLocation, ChunkSaveHandler> HANDLERS = new LinkedHashMap<>();
    private static volatile List<ChunkSaveHandler> SNAPSHOT = List.of();

    private ChunkSaveHandlerRegistry() {
    }

    public static void register(ResourceLocation id, ChunkSaveHandler handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        synchronized (LOCK) {
            if (HANDLERS.putIfAbsent(id, handler) != null) {
                throw new IllegalStateException("Chunk save handler already registered: " + id);
            }
            var ordered = new ArrayList<>(HANDLERS.entrySet());
            ordered.sort(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)));
            SNAPSHOT = ordered.stream().map(Map.Entry::getValue).toList();
        }
    }

    public static void write(ServerLevel level, ChunkAccess chunk, ChunkSaveWriter writer) {
        for (var handler : SNAPSHOT) {
            handler.write(level, chunk, writer);
        }
    }
}
