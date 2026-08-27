package com.moepus.byepregen.test;

import com.moepus.byepregen.harness.ChunkKey;
import com.moepus.byepregen.harness.RegionChunkReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class LightWorldReader {
    private static final int MAX_DISCOVERY_DEPTH = 6;
    private static final RegionChunkReader READER = new RegionChunkReader();

    private LightWorldReader() {
    }

    static Map<String, Path> regionFiles(Path worldDirectory) throws IOException {
        return RegionChunkReader.discover(worldDirectory, MAX_DISCOVERY_DEPTH);
    }

    static Map<ChunkKey, LightChunk> readWorld(Map<String, Path> regions) throws IOException {
        Map<ChunkKey, LightChunk> chunks = new HashMap<>();
        for (Path region : regions.values()) {
            chunks.putAll(readRegion(region));
        }
        return chunks;
    }

    static Map<ChunkKey, LightChunk> readRegion(Path regionPath) throws IOException {
        Map<ChunkKey, LightChunk> chunks = new HashMap<>();
        READER.forEachChunk(regionPath, (key, chunk) -> chunks.put(key, LightChunk.from(chunk)));
        return chunks;
    }
}
