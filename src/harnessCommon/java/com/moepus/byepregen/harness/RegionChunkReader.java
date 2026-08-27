package com.moepus.byepregen.harness;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;

public final class RegionChunkReader {
    public static Map<String, Path> discover(Path worldDirectory, int maxDepth) throws IOException {
        Map<String, Path> regions = new HashMap<>();
        try (Stream<Path> paths = Files.walk(worldDirectory, maxDepth)) {
            paths.filter(Files::isRegularFile)
                    .filter(RegionChunkReader::isRegionPath)
                    .forEach(path -> regions.put(relativeKey(worldDirectory, path), path));
        }
        return regions;
    }

    public static List<Path> list(Path regionDirectory) throws IOException {
        try (Stream<Path> paths = Files.list(regionDirectory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> RegionCoordinates.isRegionFileName(path.getFileName().toString()))
                    .sorted()
                    .toList();
        }
    }

    public void forEachChunk(Path regionPath, ChunkConsumer consumer) throws IOException {
        this.forEachChunk(regionPath, null, consumer);
    }

    public void forEachChunk(
            Path regionPath,
            ChunkBounds bounds,
            ChunkConsumer consumer
    ) throws IOException {
        if (regionPath == null || !Files.isRegularFile(regionPath)) {
            return;
        }
        RegionCoordinates region = RegionCoordinates.parse(regionPath.getFileName().toString());
        try (RegionFile file = new RegionFile(regionPath, regionPath.getParent(), false)) {
            this.readChunks(file, new ReadRequest(region, bounds, consumer));
        }
    }

    private void readChunks(RegionFile file, ReadRequest request) throws IOException {
        for (int localZ = 0; localZ < RegionCoordinates.CHUNKS_PER_AXIS; localZ++) {
            for (int localX = 0; localX < RegionCoordinates.CHUNKS_PER_AXIS; localX++) {
                ChunkKey key = request.region().chunkAt(localX, localZ);
                if (request.bounds() == null || request.bounds().contains(key.x(), key.z())) {
                    this.readChunk(file, key, request.consumer());
                }
            }
        }
    }

    private void readChunk(RegionFile file, ChunkKey key, ChunkConsumer consumer) throws IOException {
        DataInputStream input = file.getChunkDataInputStream(new ChunkPos(key.x(), key.z()));
        if (input == null) {
            return;
        }
        try (input) {
            CompoundTag chunk = NbtIo.read(input, NbtAccounter.UNLIMITED);
            consumer.accept(key, chunk);
        }
    }

    private static boolean isRegionPath(Path path) {
        return path.getFileName().toString().endsWith(".mca")
                && path.getParent() != null
                && "region".equals(path.getParent().getFileName().toString());
    }

    private static String relativeKey(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    @FunctionalInterface
    public interface ChunkConsumer {
        void accept(ChunkKey key, CompoundTag chunk) throws IOException;
    }

    private record ReadRequest(
            RegionCoordinates region,
            ChunkBounds bounds,
            ChunkConsumer consumer
    ) {
    }
}
