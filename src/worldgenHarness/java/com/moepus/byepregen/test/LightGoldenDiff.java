package com.moepus.byepregen.test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

public final class LightGoldenDiff {
    private static final int LIGHT_BYTES = 2048;
    private static final int LIGHT_LAYER_BYTES = LIGHT_BYTES / 16;
    private static final byte[] ZERO_LIGHT = new byte[LIGHT_BYTES];
    private static final byte[] FULL_LIGHT = filledLight(0xFF);
    private static final String BLOCK_LIGHT = "BlockLight";
    private static final String SKY_LIGHT = "SkyLight";
    private static final String[] LIGHT_KEYS = {BLOCK_LIGHT, SKY_LIGHT};
    private static final RegionStorageInfo REGION_INFO = new RegionStorageInfo("light-golden-diff", Level.OVERWORLD, "chunk");

    private LightGoldenDiff() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: LightGoldenDiff <expected-world-dir> <actual-world-dir>");
            System.err.println("Example: gradlew diffLightGolden -PbyepregenLightGoldenExpectedWorld=run/light-golden/vanilla/world -PbyepregenLightGoldenActualWorld=run/light-golden/ya/world");
            System.exit(2);
        }

        Path expectedWorld = Paths.get(args[0]).toAbsolutePath().normalize();
        Path actualWorld = Paths.get(args[1]).toAbsolutePath().normalize();
        DiffResult result = compareWorlds(expectedWorld, actualWorld, DiffOptions.fromProperties());
        result.print(expectedWorld, actualWorld);
        if (result.hasFailures()) {
            System.exit(1);
        }
    }

    private static DiffResult compareWorlds(
            Path expectedWorld,
            Path actualWorld,
            DiffOptions options
    ) throws IOException {
        if (!Files.isDirectory(expectedWorld)) {
            throw new IOException("Expected world directory does not exist: " + expectedWorld);
        }
        if (!Files.isDirectory(actualWorld)) {
            throw new IOException("Actual world directory does not exist: " + actualWorld);
        }

        Map<String, Path> expectedRegions = regionFiles(expectedWorld);
        Map<String, Path> actualRegions = regionFiles(actualWorld);
        if (expectedRegions.isEmpty()) {
            throw new IOException("Expected world has no region files: " + expectedWorld);
        }
        if (actualRegions.isEmpty()) {
            throw new IOException("Actual world has no region files: " + actualWorld);
        }

        Map<ChunkKey, ChunkLights> expectedWorldChunks = readWorld(expectedRegions);
        Map<ChunkKey, ChunkLights> actualWorldChunks = readWorld(actualRegions);
        DiffResult result = new DiffResult(options);
        ChunkBounds chunkBounds = options.chunkBounds();
        TreeSet<String> regionKeys = new TreeSet<>();
        regionKeys.addAll(expectedRegions.keySet());
        regionKeys.addAll(actualRegions.keySet());

        for (String regionKey : regionKeys) {
            Map<ChunkKey, ChunkLights> expectedChunks = readRegion(expectedRegions.get(regionKey));
            Map<ChunkKey, ChunkLights> actualChunks = readRegion(actualRegions.get(regionKey));
            result.regionsCompared++;

            TreeSet<ChunkKey> chunkKeys = new TreeSet<>();
            chunkKeys.addAll(expectedChunks.keySet());
            chunkKeys.addAll(actualChunks.keySet());
            for (ChunkKey chunkKey : chunkKeys) {
                if (!chunkBounds.contains(chunkKey)) {
                    result.skippedOutOfBoundsChunks++;
                    continue;
                }
                result.chunksInBounds++;
                ChunkLights expected = expectedChunks.get(chunkKey);
                ChunkLights actual = actualChunks.get(chunkKey);
                if (expected == null || actual == null) {
                    result.missingChunks++;
                    result.addIssue(regionKey + " chunk " + chunkKey + " missing in " + (expected == null ? "expected" : "actual") + " world");
                    continue;
                }

                result.chunksCompared++;
                if (!sameTerrainNeighborhood(chunkKey, expectedWorldChunks, actualWorldChunks, result)) {
                    result.skippedTerrainChunks++;
                    continue;
                }
                if (!expected.lightCorrect || !actual.lightCorrect) {
                    if (expected.lightCorrect != actual.lightCorrect) {
                        result.lightCorrectMismatches++;
                        result.addIssue(regionKey + " chunk " + chunkKey
                                + " has different isLightOn"
                                + " expected=" + expected.lightCorrect
                                + " actual=" + actual.lightCorrect
                                + " expectedStatus=" + expected.status
                                + " actualStatus=" + actual.status);
                    } else {
                        result.skippedUnlitChunks++;
                    }
                    continue;
                }
                if (!lightCorrectNeighborhood(chunkKey, expectedWorldChunks, actualWorldChunks)) {
                    result.skippedUnlitNeighborhoodChunks++;
                    continue;
                }
                result.eligibleChunks++;
                int layersBefore = result.layersCompared;
                compareChunk(regionKey, chunkKey, expected, actual, expectedWorldChunks, actualWorldChunks, result);
                if (result.layersCompared != layersBefore) {
                    result.chunksWithComparedLayers++;
                }
            }
        }

        return result;
    }

    private static boolean sameTerrainNeighborhood(
            ChunkKey center,
            Map<ChunkKey, ChunkLights> expectedWorldChunks,
            Map<ChunkKey, ChunkLights> actualWorldChunks,
            DiffResult result) {
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                ChunkKey key = new ChunkKey(center.x + dx, center.z + dz);
                ChunkLights expected = expectedWorldChunks.get(key);
                ChunkLights actual = actualWorldChunks.get(key);
                if (expected == null || actual == null) {
                    if (expected != actual) {
                        return false;
                    }
                    continue;
                }
                String difference = expected.terrainDifference(actual);
                if (difference != null) {
                    result.recordTerrainDifference(center, key, difference);
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean lightCorrectNeighborhood(
            ChunkKey center,
            Map<ChunkKey, ChunkLights> expectedWorldChunks,
            Map<ChunkKey, ChunkLights> actualWorldChunks) {
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                ChunkKey key = new ChunkKey(center.x + dx, center.z + dz);
                ChunkLights expected = expectedWorldChunks.get(key);
                ChunkLights actual = actualWorldChunks.get(key);
                if (expected == null || actual == null) {
                    if (expected != actual) {
                        return false;
                    }
                    continue;
                }
                if (!expected.lightCorrect || !actual.lightCorrect) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Map<ChunkKey, ChunkLights> readWorld(Map<String, Path> regions) throws IOException {
        Map<ChunkKey, ChunkLights> chunks = new HashMap<>();
        for (Path region : regions.values()) {
            chunks.putAll(readRegion(region));
        }
        return chunks;
    }

    private static Map<String, Path> regionFiles(Path worldDir) throws IOException {
        Map<String, Path> regions = new HashMap<>();
        try (Stream<Path> stream = Files.walk(worldDir, 6)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".mca"))
                    .filter(path -> path.getParent() != null && path.getParent().getFileName().toString().equals("region"))
                    .forEach(path -> regions.put(normalizeRelative(worldDir, path), path));
        }
        return regions;
    }

    private static String normalizeRelative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static Map<ChunkKey, ChunkLights> readRegion(Path regionPath) throws IOException {
        Map<ChunkKey, ChunkLights> chunks = new HashMap<>();
        if (regionPath == null || !Files.isRegularFile(regionPath)) {
            return chunks;
        }

        RegionCoords region = RegionCoords.parse(regionPath.getFileName().toString());
        try (RegionFile regionFile = new RegionFile(REGION_INFO, regionPath, regionPath.getParent(), false)) {
            for (int localZ = 0; localZ < 32; ++localZ) {
                for (int localX = 0; localX < 32; ++localX) {
                    ChunkPos pos = new ChunkPos(region.x * 32 + localX, region.z * 32 + localZ);
                    DataInputStream input = regionFile.getChunkDataInputStream(pos);
                    if (input == null) {
                        continue;
                    }
                    try (input) {
                        CompoundTag chunkTag = NbtIo.read(input, NbtAccounter.unlimitedHeap());
                        chunks.put(new ChunkKey(pos.x, pos.z), ChunkLights.from(chunkTag));
                    }
                }
            }
        }
        return chunks;
    }

    private static void compareChunk(
            String regionKey, ChunkKey chunkKey, ChunkLights expected, ChunkLights actual,
            Map<ChunkKey, ChunkLights> expectedWorldChunks, Map<ChunkKey, ChunkLights> actualWorldChunks,
            DiffResult result) {
        TreeSet<SectionLight> keys = new TreeSet<>();
        keys.addAll(expected.lights.keySet());
        keys.addAll(actual.lights.keySet());

        for (SectionLight key : keys) {
            byte[] expectedBytes = expected.lightOrZero(key);
            byte[] actualBytes = actual.lightOrZero(key);
            boolean expectedPresent = expected.lights.containsKey(key);
            boolean actualPresent = actual.lights.containsKey(key);

            if (!result.missingAsZero && expectedPresent != actualPresent) {
                if (isStorageNoise(key, expectedPresent, actualPresent, expectedBytes, actualBytes)
                        || isSemanticSkyStorageNoise(key, expectedPresent, actualPresent, expectedBytes, actualBytes, expected, actual)) {
                    result.storageNoiseLayers++;
                    continue;
                }
                byte[] reportedExpected = reportedBytes(key, expectedPresent, expectedBytes, expected);
                byte[] reportedActual = reportedBytes(key, actualPresent, actualBytes, actual);
                result.missingLayers++;
                result.addIssue(describeMismatch(
                        regionKey, chunkKey, key, reportedExpected, reportedActual,
                        expected, actual, expectedWorldChunks, actualWorldChunks)
                        + " (" + (expectedPresent ? "actual" : "expected") + " layer missing)");
                continue;
            }

            if (expectedPresent && expectedBytes.length != LIGHT_BYTES) {
                result.invalidLayers++;
                result.addIssue(describeLayer(regionKey, chunkKey, key) + " has invalid expected length " + expectedBytes.length);
            }
            if (actualPresent && actualBytes.length != LIGHT_BYTES) {
                result.invalidLayers++;
                result.addIssue(describeLayer(regionKey, chunkKey, key) + " has invalid actual length " + actualBytes.length);
            }

            result.layersCompared++;
            if (!Arrays.equals(expectedBytes, actualBytes)) {
                result.mismatchedLayers++;
                result.addIssue(describeMismatch(
                        regionKey, chunkKey, key, expectedBytes, actualBytes,
                        expected, actual, expectedWorldChunks, actualWorldChunks));
            }
        }
    }

    private static byte[] reportedBytes(SectionLight key, boolean present, byte[] bytes, ChunkLights chunk) {
        if (present || !SKY_LIGHT.equals(key.layer)) {
            return bytes;
        }
        byte[] semantic = chunk.semanticSkyLayer(key.sectionY);
        return semantic == null ? bytes : semantic;
    }

    private static String describeLayer(String regionKey, ChunkKey chunkKey, SectionLight key) {
        return regionKey + " chunk " + chunkKey + " sectionY=" + key.sectionY + " " + key.layer;
    }

    private static String describeMismatch(
            String regionKey, ChunkKey chunkKey, SectionLight key, byte[] expected, byte[] actual,
            ChunkLights expectedChunk, ChunkLights actualChunk,
            Map<ChunkKey, ChunkLights> expectedWorldChunks, Map<ChunkKey, ChunkLights> actualWorldChunks) {
        NibbleDiff diff = firstDifference(expected, actual);
        if (diff == null) {
            return describeLayer(regionKey, chunkKey, key) + " differs";
        }
        int worldX = (chunkKey.x << 4) + diff.localX();
        int worldY = (key.sectionY << 4) + diff.localY();
        int worldZ = (chunkKey.z << 4) + diff.localZ();
        return describeLayer(regionKey, chunkKey, key)
                + " differs at byte=" + diff.byteIndex
                + " nibble=" + (diff.half == 0 ? "low" : "high")
                + " local=(" + diff.localX() + "," + diff.localY() + "," + diff.localZ() + ")"
                + " world=(" + worldX + "," + worldY + "," + worldZ + ")"
                + " expected=" + diff.expected
                + " actual=" + diff.actual
                + " " + describeContext(chunkKey, key, diff, expectedChunk, actualChunk, expectedWorldChunks, actualWorldChunks);
    }

    private static NibbleDiff firstDifference(byte[] expected, byte[] actual) {
        int min = Math.min(expected.length, actual.length);
        for (int i = 0; i < min; ++i) {
            int expectedByte = expected[i] & 0xFF;
            int actualByte = actual[i] & 0xFF;
            if (expectedByte == actualByte) {
                continue;
            }
            int expectedLow = expectedByte & 15;
            int actualLow = actualByte & 15;
            if (expectedLow != actualLow) {
                return new NibbleDiff(i, 0, expectedLow, actualLow);
            }
            return new NibbleDiff(i, 1, (expectedByte >>> 4) & 15, (actualByte >>> 4) & 15);
        }
        if (expected.length != actual.length) {
            return new NibbleDiff(min, 0, expected.length, actual.length);
        }
        return null;
    }

    private static boolean isStorageNoise(SectionLight key, boolean expectedPresent, boolean actualPresent, byte[] expected, byte[] actual) {
        if (expectedPresent == actualPresent) {
            return false;
        }
        if (SKY_LIGHT.equals(key.layer)) {
            return false;
        }
        byte[] present = expectedPresent ? expected : actual;
        return isFilled(present, 0);
    }

    private static boolean isSemanticSkyStorageNoise(
            SectionLight key, boolean expectedPresent, boolean actualPresent, byte[] expected, byte[] actual,
            ChunkLights expectedChunk, ChunkLights actualChunk) {
        if (!SKY_LIGHT.equals(key.layer) || expectedPresent == actualPresent) {
            return false;
        }
        byte[] semanticExpected = expectedPresent ? expected : expectedChunk.semanticSkyLayer(key.sectionY);
        byte[] semanticActual = actualPresent ? actual : actualChunk.semanticSkyLayer(key.sectionY);
        return isValidLight(semanticExpected) && isValidLight(semanticActual) && Arrays.equals(semanticExpected, semanticActual);
    }

    private static boolean isValidLight(byte[] bytes) {
        return bytes != null && bytes.length == LIGHT_BYTES;
    }

    private static boolean isFilled(byte[] bytes, int value) {
        if (bytes.length != LIGHT_BYTES) {
            return false;
        }
        byte expected = (byte)value;
        for (byte b : bytes) {
            if (b != expected) {
                return false;
            }
        }
        return true;
    }

    private static byte[] filledLight(int value) {
        byte[] bytes = new byte[LIGHT_BYTES];
        Arrays.fill(bytes, (byte)value);
        return bytes;
    }

    private static String describeContext(
            ChunkKey chunkKey, SectionLight key, NibbleDiff diff, ChunkLights expected, ChunkLights actual,
            Map<ChunkKey, ChunkLights> expectedWorldChunks, Map<ChunkKey, ChunkLights> actualWorldChunks) {
        int worldX = (chunkKey.x << 4) + diff.localX();
        int worldY = (key.sectionY << 4) + diff.localY();
        int worldZ = (chunkKey.z << 4) + diff.localZ();
        return "blocks expected" + blockNeighborhood(expectedWorldChunks, worldX, worldY, worldZ)
                + " actual" + blockNeighborhood(actualWorldChunks, worldX, worldY, worldZ)
                + " light expected" + lightNeighborhood(expectedWorldChunks, key.layer, worldX, worldY, worldZ)
                + " actual" + lightNeighborhood(actualWorldChunks, key.layer, worldX, worldY, worldZ)
                + " path expected/actual" + lightPath(expectedWorldChunks, actualWorldChunks, key.layer, worldX, worldY, worldZ)
                + " path actual/expected" + lightPath(actualWorldChunks, expectedWorldChunks, key.layer, worldX, worldY, worldZ);
    }

    private static String blockNeighborhood(Map<ChunkKey, ChunkLights> chunks, int x, int y, int z) {
        return "{C=" + blockAtWorld(chunks, x, y, z)
                + " D=" + blockAtWorld(chunks, x, y - 1, z)
                + " U=" + blockAtWorld(chunks, x, y + 1, z)
                + " N=" + blockAtWorld(chunks, x, y, z - 1)
                + " S=" + blockAtWorld(chunks, x, y, z + 1)
                + " W=" + blockAtWorld(chunks, x - 1, y, z)
                + " E=" + blockAtWorld(chunks, x + 1, y, z)
                + "}";
    }

    private static String blockAtWorld(Map<ChunkKey, ChunkLights> chunks, int x, int y, int z) {
        ChunkLights chunk = chunks.get(new ChunkKey(x >> 4, z >> 4));
        if (chunk == null) {
            return "missing";
        }
        return chunk.blockStateAt(y >> 4, x & 15, y & 15, z & 15);
    }

    private static String lightNeighborhood(Map<ChunkKey, ChunkLights> chunks, String layer, int x, int y, int z) {
        return "{C=" + lightAtWorld(chunks, layer, x, y, z)
                + " D=" + lightAtWorld(chunks, layer, x, y - 1, z)
                + " U=" + lightAtWorld(chunks, layer, x, y + 1, z)
                + " N=" + lightAtWorld(chunks, layer, x, y, z - 1)
                + " S=" + lightAtWorld(chunks, layer, x, y, z + 1)
                + " W=" + lightAtWorld(chunks, layer, x - 1, y, z)
                + " E=" + lightAtWorld(chunks, layer, x + 1, y, z)
                + "}";
    }

    private static String lightAtWorld(Map<ChunkKey, ChunkLights> chunks, String layer, int x, int y, int z) {
        int value = lightValueAtWorld(chunks, layer, x, y, z);
        return value < 0 ? "missing" : Integer.toString(value);
    }

    private static String lightPath(Map<ChunkKey, ChunkLights> expectedChunks, Map<ChunkKey, ChunkLights> actualChunks, String layer, int x, int y, int z) {
        StringBuilder builder = new StringBuilder("[");
        for (int step = 0; step < 20; ++step) {
            int value = lightValueAtWorld(expectedChunks, layer, x, y, z);
            int actualValue = lightValueAtWorld(actualChunks, layer, x, y, z);
            if (step != 0) {
                builder.append(" -> ");
            }
            builder.append('(').append(x).append(',').append(y).append(',').append(z).append(")=")
                    .append(value < 0 ? "missing" : value)
                    .append('/')
                    .append(actualValue < 0 ? "missing" : actualValue)
                    .append(':').append(blockAtWorld(expectedChunks, x, y, z))
                    .append('/')
                    .append(blockAtWorld(actualChunks, x, y, z));
            if (value >= 15 || value < 0) {
                break;
            }
            int bestValue = value;
            int bestX = x;
            int bestY = y;
            int bestZ = z;
            int[][] offsets = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
            for (int[] offset : offsets) {
                int neighborX = x + offset[0];
                int neighborY = y + offset[1];
                int neighborZ = z + offset[2];
                int neighborValue = lightValueAtWorld(expectedChunks, layer, neighborX, neighborY, neighborZ);
                if (neighborValue > bestValue) {
                    bestValue = neighborValue;
                    bestX = neighborX;
                    bestY = neighborY;
                    bestZ = neighborZ;
                }
            }
            if (bestValue <= value) {
                break;
            }
            x = bestX;
            y = bestY;
            z = bestZ;
        }
        return builder.append(']').toString();
    }

    private static int lightValueAtWorld(Map<ChunkKey, ChunkLights> chunks, String layer, int x, int y, int z) {
        ChunkLights chunk = chunks.get(new ChunkKey(x >> 4, z >> 4));
        if (chunk == null) {
            return -1;
        }
        return chunk.lightAt(layer, y >> 4, x & 15, y & 15, z & 15);
    }

    private record RegionCoords(int x, int z) {
        static RegionCoords parse(String fileName) throws IOException {
            if (!fileName.startsWith("r.") || !fileName.endsWith(".mca")) {
                throw new IOException("Invalid region file name: " + fileName);
            }
            String[] parts = fileName.substring(2, fileName.length() - 4).split("\\.");
            if (parts.length != 2) {
                throw new IOException("Invalid region file name: " + fileName);
            }
            return new RegionCoords(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }
    }

    private record ChunkKey(int x, int z) implements Comparable<ChunkKey> {
        @Override
        public int compareTo(ChunkKey other) {
            int zCompare = Integer.compare(this.z, other.z);
            return zCompare != 0 ? zCompare : Integer.compare(this.x, other.x);
        }

        @Override
        public String toString() {
            return "(" + this.x + "," + this.z + ")";
        }
    }

    private record ChunkBounds(int minX, int maxX, int minZ, int maxZ) {
        ChunkBounds {
            if (minX > maxX || minZ > maxZ) {
                throw new IllegalArgumentException("Invalid chunk bounds: x=" + minX + ".." + maxX
                        + ", z=" + minZ + ".." + maxZ);
            }
        }

        static ChunkBounds fromProperties() {
            int minX = Integer.getInteger("byepregen.lightGolden.minChunkX", Integer.MIN_VALUE);
            int maxX = Integer.getInteger("byepregen.lightGolden.maxChunkX", Integer.MAX_VALUE);
            int minZ = Integer.getInteger("byepregen.lightGolden.minChunkZ", Integer.MIN_VALUE);
            int maxZ = Integer.getInteger("byepregen.lightGolden.maxChunkZ", Integer.MAX_VALUE);
            return new ChunkBounds(minX, maxX, minZ, maxZ);
        }

        boolean contains(ChunkKey key) {
            return key.x >= this.minX && key.x <= this.maxX && key.z >= this.minZ && key.z <= this.maxZ;
        }

        boolean isLimited() {
            return this.minX != Integer.MIN_VALUE
                    || this.maxX != Integer.MAX_VALUE
                    || this.minZ != Integer.MIN_VALUE
                    || this.maxZ != Integer.MAX_VALUE;
        }

        boolean isFullyBounded() {
            return this.minX != Integer.MIN_VALUE
                    && this.maxX != Integer.MAX_VALUE
                    && this.minZ != Integer.MIN_VALUE
                    && this.maxZ != Integer.MAX_VALUE;
        }

        long expectedChunks() {
            if (!this.isFullyBounded()) {
                throw new IllegalStateException("Complete chunk coverage requires finite bounds");
            }
            long width = (long)this.maxX - this.minX + 1L;
            long depth = (long)this.maxZ - this.minZ + 1L;
            return Math.multiplyExact(width, depth);
        }

        String display() {
            return "x=" + this.minX + ".." + this.maxX + ", z=" + this.minZ + ".." + this.maxZ;
        }
    }

    private record DiffOptions(
            int maxIssues,
            int minComparedLayers,
            boolean missingAsZero,
            boolean requireCompleteChunks,
            double minChunkCoverage,
            ChunkBounds chunkBounds
    ) {
        static DiffOptions fromProperties() {
            return new DiffOptions(
                    Integer.getInteger("byepregen.lightGolden.maxMismatches", 50),
                    Integer.getInteger("byepregen.lightGolden.minComparedLayers", 1),
                    Boolean.getBoolean("byepregen.lightGolden.missingAsZero"),
                    Boolean.getBoolean("byepregen.lightGolden.requireCompleteChunks"),
                    Double.parseDouble(System.getProperty("byepregen.lightGolden.minChunkCoverage", "0.8")),
                    ChunkBounds.fromProperties()
            );
        }
    }

    private record SectionLight(int sectionY, String layer) implements Comparable<SectionLight> {
        @Override
        public int compareTo(SectionLight other) {
            int yCompare = Integer.compare(this.sectionY, other.sectionY);
            return yCompare != 0 ? yCompare : this.layer.compareTo(other.layer);
        }
    }

    private static final class ChunkLights {
        private final Map<SectionLight, byte[]> lights = new HashMap<>();
        private final Map<Integer, SectionBlocks> blocks = new HashMap<>();
        private boolean lightCorrect;
        private String status;

        static ChunkLights from(CompoundTag chunkTag) {
            ChunkLights lights = new ChunkLights();
            lights.lightCorrect = chunkTag.getBoolean("isLightOn");
            lights.status = chunkTag.getString("Status");
            ListTag sections = chunkTag.getList("sections", 10);
            for (int i = 0; i < sections.size(); ++i) {
                CompoundTag section = sections.getCompound(i);
                int sectionY = section.getByte("Y");
                if (section.contains("block_states", 10)) {
                    lights.blocks.put(sectionY, SectionBlocks.from(section.getCompound("block_states")));
                }
                for (String lightKey : LIGHT_KEYS) {
                    if (section.contains(lightKey, 7)) {
                        lights.lights.put(new SectionLight(sectionY, lightKey), section.getByteArray(lightKey));
                    }
                }
            }
            return lights;
        }

        byte[] lightOrZero(SectionLight key) {
            byte[] light = this.lights.get(key);
            return light == null ? ZERO_LIGHT : light;
        }

        String blockStateAt(int sectionY, int localX, int localY, int localZ) {
            return this.blockSection(sectionY).stateAt(localX, localY, localZ);
        }

        String terrainDifference(ChunkLights other) {
            TreeSet<Integer> keys = new TreeSet<>();
            keys.addAll(this.blocks.keySet());
            keys.addAll(other.blocks.keySet());
            for (int sectionY : keys) {
                String difference = this.blockSection(sectionY).terrainDifference(other.blockSection(sectionY));
                if (difference != null) {
                    return "sectionY=" + sectionY + " " + difference;
                }
            }
            return null;
        }

        private SectionBlocks blockSection(int sectionY) {
            return this.blocks.getOrDefault(sectionY, SectionBlocks.AIR);
        }

        String lightNeighborhood(SectionLight key, int localX, int localY, int localZ) {
            StringBuilder builder = new StringBuilder();
            builder.append("{C=").append(this.lightAt(key.layer, key.sectionY, localX, localY, localZ));
            appendLocalLight(builder, "D", key.layer, key.sectionY, localX, localY - 1, localZ);
            appendLocalLight(builder, "U", key.layer, key.sectionY, localX, localY + 1, localZ);
            appendLocalLight(builder, "N", key.layer, key.sectionY, localX, localY, localZ - 1);
            appendLocalLight(builder, "S", key.layer, key.sectionY, localX, localY, localZ + 1);
            appendLocalLight(builder, "W", key.layer, key.sectionY, localX - 1, localY, localZ);
            appendLocalLight(builder, "E", key.layer, key.sectionY, localX + 1, localY, localZ);
            return builder.append('}').toString();
        }

        private void appendLocalLight(StringBuilder builder, String name, String layer, int sectionY, int localX, int localY, int localZ) {
            builder.append(' ').append(name).append('=');
            if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15) {
                builder.append("out");
                return;
            }
            while (localY < 0) {
                localY += 16;
                --sectionY;
            }
            while (localY > 15) {
                localY -= 16;
                ++sectionY;
            }
            builder.append(this.lightAt(layer, sectionY, localX, localY, localZ));
        }

        private int lightAt(String layer, int sectionY, int localX, int localY, int localZ) {
            byte[] bytes = SKY_LIGHT.equals(layer)
                    ? this.semanticSkyLayer(sectionY)
                    : this.lightOrZero(new SectionLight(sectionY, layer));
            if (!isValidLight(bytes)) {
                return -1;
            }
            int nibbleIndex = localX | (localZ << 4) | (localY << 8);
            int value = bytes[nibbleIndex >>> 1] & 0xFF;
            return (value >>> ((nibbleIndex & 1) << 2)) & 15;
        }

        private byte[] semanticSkyLayer(int sectionY) {
            byte[] light = this.lights.get(new SectionLight(sectionY, SKY_LIGHT));
            if (light != null) {
                return light;
            }
            byte[] above = this.firstSkyLayerAbove(sectionY);
            return above == null ? FULL_LIGHT : repeatFirstLayer(above);
        }

        private byte[] firstSkyLayerAbove(int sectionY) {
            byte[] best = null;
            int bestY = Integer.MAX_VALUE;
            for (Map.Entry<SectionLight, byte[]> entry : this.lights.entrySet()) {
                SectionLight key = entry.getKey();
                if (SKY_LIGHT.equals(key.layer) && key.sectionY > sectionY && key.sectionY < bestY) {
                    best = entry.getValue();
                    bestY = key.sectionY;
                }
            }
            return best;
        }

        private static byte[] repeatFirstLayer(byte[] bytes) {
            if (!isValidLight(bytes)) {
                return null;
            }
            byte[] repeated = new byte[LIGHT_BYTES];
            for (int layer = 0; layer < 16; ++layer) {
                System.arraycopy(bytes, 0, repeated, layer * LIGHT_LAYER_BYTES, LIGHT_LAYER_BYTES);
            }
            return repeated;
        }
    }

    private static final class SectionBlocks {
        private static final SectionBlocks AIR = new SectionBlocks(new String[]{"minecraft:air"}, null);
        private static final SectionBlocks MISSING = new SectionBlocks(new String[]{"missing-palette"}, null);
        private final String[] palette;
        private final SimpleBitStorage storage;

        private SectionBlocks(String[] palette, SimpleBitStorage storage) {
            this.palette = palette;
            this.storage = storage;
        }

        static SectionBlocks from(CompoundTag tag) {
            ListTag paletteTags = tag.getList("palette", 10);
            if (paletteTags.isEmpty()) {
                return MISSING;
            }
            String[] palette = new String[paletteTags.size()];
            for (int i = 0; i < palette.length; ++i) {
                palette[i] = stateName(paletteTags.getCompound(i));
            }
            if (!tag.contains("data", 12)) {
                return new SectionBlocks(palette, null);
            }
            int bits = Math.max(4, ceilLog2(palette.length));
            try {
                return new SectionBlocks(palette, new SimpleBitStorage(bits, 4096, tag.getLongArray("data")));
            } catch (RuntimeException exception) {
                return new SectionBlocks(new String[]{"invalid-storage:" + exception.getMessage()}, null);
            }
        }

        String stateAt(int localX, int localY, int localZ) {
            return this.stateAt(localX | (localZ << 4) | (localY << 8));
        }

        String terrainDifference(SectionBlocks other) {
            if (this.storage == null && other.storage == null) {
                return this.palette.length == 1 && other.palette.length == 1 && this.palette[0].equals(other.palette[0])
                        ? null : "expected=" + this.stateAt(0) + " actual=" + other.stateAt(0);
            }
            for (int i = 0; i < 4096; ++i) {
                String expected = this.stateAt(i);
                String actual = other.stateAt(i);
                if (!expected.equals(actual)) {
                    int x = i & 15;
                    int z = i >>> 4 & 15;
                    int y = i >>> 8 & 15;
                    return "local=" + x + "," + y + "," + z
                            + " expected=" + expected + " actual=" + actual;
                }
            }
            return null;
        }

        private String stateAt(int storageIndex) {
            int paletteIndex = 0;
            if (this.storage != null) {
                paletteIndex = this.storage.get(storageIndex);
            }
            return paletteIndex >= 0 && paletteIndex < this.palette.length ? this.palette[paletteIndex] : "invalid-palette-id:" + paletteIndex;
        }

        private static String stateName(CompoundTag stateTag) {
            String name = stateTag.getString("Name");
            if (!stateTag.contains("Properties", 10)) {
                return name;
            }
            CompoundTag properties = stateTag.getCompound("Properties");
            TreeSet<String> keys = new TreeSet<>(properties.getAllKeys());
            StringBuilder builder = new StringBuilder(name).append('[');
            boolean first = true;
            for (String key : keys) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(key).append('=').append(properties.getString(key));
            }
            return builder.append(']').toString();
        }
    }

    private static int ceilLog2(int value) {
        int result = 0;
        int target = Math.max(1, value - 1);
        while (target > 0) {
            ++result;
            target >>>= 1;
        }
        return result;
    }

    private record NibbleDiff(int byteIndex, int half, int expected, int actual) {
        int nibbleIndex() {
            return this.byteIndex * 2 + this.half;
        }

        int localX() {
            return this.nibbleIndex() & 15;
        }

        int localZ() {
            return (this.nibbleIndex() >>> 4) & 15;
        }

        int localY() {
            return (this.nibbleIndex() >>> 8) & 15;
        }
    }

    private static final class DiffResult {
        private final int maxIssues;
        private final int minComparedLayers;
        private final boolean missingAsZero;
        private final boolean requireCompleteChunks;
        private final double minChunkCoverage;
        private final ChunkBounds chunkBounds;
        private final java.util.ArrayList<String> issues;
        private int suppressedIssues;
        private int regionsCompared;
        private int chunksCompared;
        private long chunksInBounds;
        private long eligibleChunks;
        private long chunksWithComparedLayers;
        private int skippedOutOfBoundsChunks;
        private int skippedUnlitChunks;
        private int skippedUnlitNeighborhoodChunks;
        private int skippedTerrainChunks;
        private int lightCorrectMismatches;
        private int layersCompared;
        private int missingChunks;
        private int missingLayers;
        private int storageNoiseLayers;
        private int invalidLayers;
        private int mismatchedLayers;
        private String firstTerrainDifference;

        DiffResult(DiffOptions options) {
            this.maxIssues = Math.max(0, options.maxIssues());
            this.minComparedLayers = Math.max(0, options.minComparedLayers());
            this.missingAsZero = options.missingAsZero();
            this.requireCompleteChunks = options.requireCompleteChunks();
            this.minChunkCoverage = options.minChunkCoverage();
            this.chunkBounds = options.chunkBounds();
            this.issues = new java.util.ArrayList<>(Math.min(this.maxIssues, 64));
            if (this.requireCompleteChunks && !this.chunkBounds.isFullyBounded()) {
                throw new IllegalArgumentException("Complete chunk coverage requires finite chunk bounds");
            }
            if (!(this.minChunkCoverage > 0.0D && this.minChunkCoverage <= 1.0D)) {
                throw new IllegalArgumentException("minChunkCoverage must be in (0, 1], got "
                        + this.minChunkCoverage);
            }
        }

        boolean hasFailures() {
            return this.layersCompared < this.minComparedLayers
                    || this.missingChunks != 0
                    || this.hasIncompleteCoverage()
                    || this.lightCorrectMismatches != 0
                    || this.missingLayers != 0
                    || this.invalidLayers != 0
                    || this.mismatchedLayers != 0;
        }

        private boolean hasIncompleteCoverage() {
            if (!this.requireCompleteChunks) {
                return false;
            }
            long expectedChunks = this.chunkBounds.expectedChunks();
            return this.chunksInBounds != expectedChunks
                    || this.chunksWithComparedLayers < this.requiredComparedChunks();
        }

        private long requiredComparedChunks() {
            return (long)Math.ceil(this.chunkBounds.expectedChunks() * this.minChunkCoverage);
        }

        void addIssue(String issue) {
            if (this.issues.size() < this.maxIssues) {
                this.issues.add(issue);
            } else {
                ++this.suppressedIssues;
            }
        }

        void recordTerrainDifference(ChunkKey compared, ChunkKey terrain, String difference) {
            if (this.firstTerrainDifference == null) {
                this.firstTerrainDifference = "compared=" + compared + " terrain=" + terrain + " " + difference;
            }
        }

        void print(Path expectedWorld, Path actualWorld) {
            System.out.println("Light golden diff");
            System.out.println("  expected: " + expectedWorld);
            System.out.println("  actual:   " + actualWorld);
            System.out.println("  mode:     " + (this.missingAsZero
                    ? "missing light tags compare as zero"
                    : "semantic SkyLight, strict BlockLight storage"));
            if (this.chunkBounds.isLimited()) {
                System.out.println("  bounds:   " + this.chunkBounds.display()
                        + ", " + this.skippedOutOfBoundsChunks + " skipped-out-of-bounds chunk(s)");
            }
            System.out.println("  require:  at least " + this.minComparedLayers + " compared light layer(s)");
            if (this.requireCompleteChunks) {
                System.out.println("  coverage: require all " + this.chunkBounds.expectedChunks()
                        + " bounded chunk(s) present and at least " + this.requiredComparedChunks()
                        + " with compared layers; " + this.chunksInBounds + " present in union, "
                        + this.eligibleChunks + " eligible, " + this.chunksWithComparedLayers
                        + " with compared layers");
            }
            System.out.println("  regions:  " + this.regionsCompared);
            System.out.println("  chunks:   " + this.chunksCompared
                    + " compared, " + this.missingChunks + " missing, "
                    + this.skippedUnlitChunks + " skipped-unlit, "
                    + this.skippedUnlitNeighborhoodChunks + " skipped-unlit-neighborhood, "
                    + this.skippedTerrainChunks + " skipped-terrain, "
                    + this.lightCorrectMismatches + " light-correct mismatched");
            System.out.println("  layers:   " + this.layersCompared + " compared, "
                    + this.missingLayers + " missing, "
                    + this.storageNoiseLayers + " storage-noise, "
                    + this.invalidLayers + " invalid, "
                    + this.mismatchedLayers + " mismatched");
            if (this.firstTerrainDifference != null) {
                System.out.println("  terrain:  " + this.firstTerrainDifference);
            }
            if (this.hasFailures()) {
                System.out.println("Light golden diff FAILED");
                if (this.layersCompared < this.minComparedLayers) {
                    System.out.println("  - only compared " + this.layersCompared
                            + " light layer(s), below required " + this.minComparedLayers);
                }
                if (this.missingChunks != 0) {
                    System.out.println("  - " + this.missingChunks + " chunk(s) are missing from one world");
                }
                if (this.hasIncompleteCoverage()) {
                    System.out.println("  - bounded chunk coverage is incomplete or contains skipped chunks");
                }
                for (String issue : this.issues) {
                    System.out.println("  - " + issue);
                }
                if (this.suppressedIssues != 0) {
                    System.out.println("  ... " + this.suppressedIssues + " more issue(s) suppressed");
                }
            } else {
                System.out.println("Light golden diff passed");
            }
        }
    }
}
