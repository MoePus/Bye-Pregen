package com.moepus.byepregen.test;

import com.moepus.byepregen.harness.ChunkBounds;
import com.moepus.byepregen.harness.ChunkKey;
import com.moepus.byepregen.harness.HarnessProperties;
import com.moepus.byepregen.harness.RegionChunkReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

public final class SurfaceWorldHash {
    private static final RegionStorageInfo REGION_INFO = new RegionStorageInfo(
            "surface-world-hash", Level.OVERWORLD, "chunk"
    );
    private static final RegionChunkReader REGION_READER = new RegionChunkReader(REGION_INFO);
    private static final int MAX_REPORTED_MISMATCHES = 50;

    private SurfaceWorldHash() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: SurfaceWorldHash <world-dir> [other-world-dir]");
            System.exit(2);
        }
        ChunkBounds bounds = ChunkBounds.fromSystemProperties("byepregen.surfaceHash");
        boolean requireCompleteBounds = HarnessProperties.getBoolean(
                "byepregen.surfaceHash.requireCompleteBounds", false
        );
        WorldHashes first = hashWorld(
                Path.of(args[0]).toAbsolutePath().normalize(), bounds, requireCompleteBounds
        );
        first.print("first");
        if (args.length == 1) {
            return;
        }
        WorldHashes second = hashWorld(
                Path.of(args[1]).toAbsolutePath().normalize(), bounds, requireCompleteBounds
        );
        second.print("second");
        compare(first, second);
    }

    private static WorldHashes hashWorld(
            Path world,
            ChunkBounds bounds,
            boolean requireCompleteBounds
    ) throws IOException {
        Path regionDirectory = world.resolve("region");
        if (!Files.isDirectory(regionDirectory)) {
            throw new IOException("Overworld region directory does not exist: " + regionDirectory);
        }
        TreeMap<ChunkKey, ChunkHashes> chunks = new TreeMap<>();
        for (Path region : RegionChunkReader.list(regionDirectory)) {
            readRegion(region, chunks, bounds);
        }
        if (chunks.isEmpty()) {
            throw new IOException("No chunks found under " + regionDirectory);
        }
        if (requireCompleteBounds && chunks.size() != bounds.expectedChunks()) {
            throw new IOException("Incomplete bounded chunk coverage under " + regionDirectory
                    + ": expected=" + bounds.expectedChunks() + " actual=" + chunks.size());
        }
        return new WorldHashes(world, chunks, aggregate(chunks));
    }

    private static void readRegion(
            Path regionPath,
            Map<ChunkKey, ChunkHashes> chunks,
            ChunkBounds bounds
    ) throws IOException {
        REGION_READER.forEachChunk(regionPath, bounds, (key, chunk) -> {
            ChunkHashes previous = chunks.put(key, hashChunk(chunk));
            if (previous != null) {
                throw new IOException("Duplicate chunk " + surfaceKey(key));
            }
        });
    }

    private static ChunkHashes hashChunk(CompoundTag chunk) throws IOException {
        CompoundTag heightmaps = chunk.getCompound("Heightmaps");
        return new ChunkHashes(
                hashTag(blockStates(chunk)),
                hashTag(heightmaps),
                heightmapEntries(heightmaps),
                hashTag(chunk.getList("PostProcessing", Tag.TAG_LIST)),
                chunk.getString("Status")
        );
    }

    private static Map<String, String> heightmapEntries(CompoundTag heightmaps) throws IOException {
        TreeMap<String, String> entries = new TreeMap<>();
        for (String key : heightmaps.getAllKeys()) {
            entries.put(key, HexFormat.of().formatHex(hashTag(heightmaps.get(key))));
        }
        return Collections.unmodifiableMap(entries);
    }

    private static ListTag blockStates(CompoundTag chunk) {
        ListTag result = new ListTag();
        ListTag sections = chunk.getList("sections", Tag.TAG_COMPOUND);
        for (int index = 0; index < sections.size(); index++) {
            CompoundTag source = sections.getCompound(index);
            if (!source.contains("block_states", Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag section = new CompoundTag();
            section.putByte("Y", source.getByte("Y"));
            section.put("block_states", source.getCompound("block_states").copy());
            result.add(section);
        }
        return result;
    }

    private static byte[] hashTag(Tag tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            writeCanonical(tag, output);
        }
        return sha256(bytes.toByteArray());
    }

    private static void writeCanonical(Tag tag, DataOutputStream output) throws IOException {
        output.writeByte(tag.getId());
        if (tag instanceof CompoundTag compound) {
            ArrayList<String> keys = new ArrayList<>(compound.getAllKeys());
            Collections.sort(keys);
            output.writeInt(keys.size());
            for (String key : keys) {
                output.writeUTF(key);
                writeCanonical(compound.get(key), output);
            }
            return;
        }
        if (tag instanceof ListTag list) {
            output.writeInt(list.size());
            for (Tag child : list) {
                writeCanonical(child, output);
            }
            return;
        }
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(payload)) {
            NbtIo.writeAnyTag(tag, data);
        }
        output.writeInt(payload.size());
        payload.writeTo(output);
    }

    private static AggregateHashes aggregate(Map<ChunkKey, ChunkHashes> chunks) {
        MessageDigest blocks = digest();
        MessageDigest heightmaps = digest();
        MessageDigest postProcessing = digest();
        for (Map.Entry<ChunkKey, ChunkHashes> entry : chunks.entrySet()) {
            byte[] position = ByteBuffer.allocate(Integer.BYTES * 2)
                    .putInt(entry.getKey().x())
                    .putInt(entry.getKey().z())
                    .array();
            update(blocks, position, entry.getValue().blocks());
            update(heightmaps, position, entry.getValue().heightmaps());
            update(postProcessing, position, entry.getValue().postProcessing());
        }
        return new AggregateHashes(
                blocks.digest(), heightmaps.digest(), postProcessing.digest()
        );
    }

    private static void compare(WorldHashes first, WorldHashes second) {
        int mismatches = 0;
        TreeMap<ChunkKey, ChunkHashes> all = new TreeMap<>(first.chunks());
        all.putAll(second.chunks());
        for (ChunkKey key : all.keySet()) {
            ChunkHashes expected = first.chunks().get(key);
            ChunkHashes actual = second.chunks().get(key);
            if (expected != null && expected.equals(actual)) {
                continue;
            }
            if (mismatches++ < MAX_REPORTED_MISMATCHES) {
                System.err.println("Surface world mismatch chunk=" + surfaceKey(key)
                        + " first=" + describe(expected)
                        + " second=" + describe(actual)
                        + describeHeightmapDifference(expected, actual));
            }
        }
        if (mismatches != 0) {
            throw new AssertionError("Surface world hashes differ in " + mismatches + " chunks");
        }
        System.out.println("Surface world hashes match across " + first.chunks().size() + " chunks");
    }

    private static String describe(ChunkHashes hashes) {
        return hashes == null ? "missing" : hashes.hex();
    }

    private static String surfaceKey(ChunkKey key) {
        return "ChunkKey[x=" + key.x() + ", z=" + key.z() + "]";
    }

    private static String describeHeightmapDifference(ChunkHashes first, ChunkHashes second) {
        if (first == null || second == null || Arrays.equals(first.heightmaps(), second.heightmaps())) {
            return "";
        }
        TreeMap<String, String> differences = new TreeMap<>();
        TreeMap<String, String> all = new TreeMap<>(first.heightmapEntries());
        all.putAll(second.heightmapEntries());
        for (String key : all.keySet()) {
            String firstHash = first.heightmapEntries().get(key);
            String secondHash = second.heightmapEntries().get(key);
            if (!Objects.equals(firstHash, secondHash)) {
                differences.put(key, firstHash + " -> " + secondHash);
            }
        }
        return " heightmapDiff=" + differences;
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is unavailable", exception);
        }
    }

    private static byte[] sha256(byte[] value) {
        return digest().digest(value);
    }

    private static void update(MessageDigest digest, byte[] position, byte[] value) {
        digest.update(position);
        digest.update(value);
    }

    private record ChunkHashes(
            byte[] blocks,
            byte[] heightmaps,
            Map<String, String> heightmapEntries,
            byte[] postProcessing,
            String status
    ) {
        @Override
        public boolean equals(Object other) {
            return other instanceof ChunkHashes hashes
                    && Arrays.equals(this.blocks, hashes.blocks)
                    && Arrays.equals(this.heightmaps, hashes.heightmaps)
                    && Arrays.equals(this.postProcessing, hashes.postProcessing)
                    && Objects.equals(this.status, hashes.status);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(this.blocks);
            result = 31 * result + Arrays.hashCode(this.heightmaps);
            result = 31 * result + Arrays.hashCode(this.postProcessing);
            return 31 * result + Objects.hashCode(this.status);
        }

        private String hex() {
            return "status=" + this.status
                    + ",blocks=" + HexFormat.of().formatHex(this.blocks)
                    + ",heightmaps=" + HexFormat.of().formatHex(this.heightmaps)
                    + ",postProcessing=" + HexFormat.of().formatHex(this.postProcessing);
        }
    }

    private record AggregateHashes(byte[] blocks, byte[] heightmaps, byte[] postProcessing) {
        private String hex() {
            return "blocks=" + HexFormat.of().formatHex(this.blocks)
                    + " heightmaps=" + HexFormat.of().formatHex(this.heightmaps)
                    + " postProcessing=" + HexFormat.of().formatHex(this.postProcessing);
        }
    }

    private record WorldHashes(
            Path world,
            TreeMap<ChunkKey, ChunkHashes> chunks,
            AggregateHashes aggregate
    ) {
        private void print(String label) {
            System.out.println(label + " world=" + this.world
                    + " chunks=" + this.chunks.size()
                    + " " + this.aggregate.hex());
        }
    }

}
