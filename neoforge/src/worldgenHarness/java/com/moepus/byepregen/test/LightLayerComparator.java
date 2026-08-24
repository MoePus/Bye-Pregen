package com.moepus.byepregen.test;

import com.moepus.byepregen.harness.ChunkKey;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeSet;

final class LightLayerComparator {
    private static final int MAX_LIGHT_PATH_STEPS = 20;
    private static final int[][] NEIGHBOR_OFFSETS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    private LightLayerComparator() {
    }

    static void compare(LightGoldenDiff.ChunkComparison chunk) {
        TreeSet<LightSectionKey> keys = new TreeSet<>(chunk.expected().lights.keySet());
        keys.addAll(chunk.actual().lights.keySet());
        for (LightSectionKey key : keys) {
            compareLayer(new LayerContext(chunk, key));
        }
    }

    private static void compareLayer(LayerContext context) {
        LightChunk expectedChunk = context.chunk().expected();
        LightChunk actualChunk = context.chunk().actual();
        LightSectionKey key = context.key();
        LightDiffResult result = context.worlds().result();
        LayerStorage expected = new LayerStorage(
                expectedChunk.lights.containsKey(key), expectedChunk.lightOrZero(key), expectedChunk, "expected"
        );
        LayerStorage actual = new LayerStorage(
                actualChunk.lights.containsKey(key), actualChunk.lightOrZero(key), actualChunk, "actual"
        );

        if (!result.missingAsZero && expected.present() != actual.present()) {
            if (isStorageNoise(key, expected, actual)
                    || isSemanticSkyStorageNoise(context, expected, actual)) {
                result.storageNoiseLayers++;
                return;
            }
            byte[] reportedExpected = reportedBytes(key, expected);
            byte[] reportedActual = reportedBytes(key, actual);
            result.missingLayers++;
            result.addIssue(describeMismatch(context, reportedExpected, reportedActual)
                    + " (" + (expected.present() ? "actual" : "expected") + " layer missing)");
            return;
        }

        validateLength(context, expected);
        validateLength(context, actual);
        result.layersCompared++;
        if (!Arrays.equals(expected.bytes(), actual.bytes())) {
            result.mismatchedLayers++;
            result.addIssue(describeMismatch(context, expected.bytes(), actual.bytes()));
        }
    }

    private static void validateLength(LayerContext context, LayerStorage storage) {
        if (storage.present() && storage.bytes().length != LightChunk.LIGHT_BYTES) {
            context.worlds().result().invalidLayers++;
            context.worlds().result().addIssue(describeLayer(context)
                    + " has invalid " + storage.world() + " length " + storage.bytes().length);
        }
    }

    private static byte[] reportedBytes(LightSectionKey key, LayerStorage storage) {
        if (storage.present() || !LightChunk.SKY_LIGHT.equals(key.layer())) {
            return storage.bytes();
        }
        byte[] semantic = storage.chunk().semanticSkyLayer(key.sectionY());
        return semantic == null ? storage.bytes() : semantic;
    }

    private static boolean isStorageNoise(
            LightSectionKey key,
            LayerStorage expected,
            LayerStorage actual
    ) {
        if (expected.present() == actual.present() || LightChunk.SKY_LIGHT.equals(key.layer())) {
            return false;
        }
        return isFilled(expected.present() ? expected.bytes() : actual.bytes(), 0);
    }

    private static boolean isSemanticSkyStorageNoise(
            LayerContext context,
            LayerStorage expected,
            LayerStorage actual
    ) {
        if (!LightChunk.SKY_LIGHT.equals(context.key().layer())
                || expected.present() == actual.present()) {
            return false;
        }
        byte[] semanticExpected = expected.present()
                ? expected.bytes() : expected.chunk().semanticSkyLayer(context.key().sectionY());
        byte[] semanticActual = actual.present()
                ? actual.bytes() : actual.chunk().semanticSkyLayer(context.key().sectionY());
        return LightChunk.isValidLight(semanticExpected)
                && LightChunk.isValidLight(semanticActual)
                && Arrays.equals(semanticExpected, semanticActual);
    }

    private static boolean isFilled(byte[] bytes, int value) {
        if (bytes.length != LightChunk.LIGHT_BYTES) {
            return false;
        }
        byte expected = (byte)value;
        for (byte current : bytes) {
            if (current != expected) {
                return false;
            }
        }
        return true;
    }

    private static String describeMismatch(LayerContext context, byte[] expected, byte[] actual) {
        NibbleDiff diff = firstDifference(expected, actual);
        if (diff == null) {
            return describeLayer(context) + " differs";
        }
        ChunkKey chunkKey = context.chunk().key();
        LightSectionKey key = context.key();
        int worldX = (chunkKey.x() << 4) + diff.localX();
        int worldY = (key.sectionY() << 4) + diff.localY();
        int worldZ = (chunkKey.z() << 4) + diff.localZ();
        return describeLayer(context)
                + " differs at byte=" + diff.byteIndex()
                + " nibble=" + (diff.half() == 0 ? "low" : "high")
                + " local=(" + diff.localX() + "," + diff.localY() + "," + diff.localZ() + ")"
                + " world=(" + worldX + "," + worldY + "," + worldZ + ")"
                + " expected=" + diff.expected()
                + " actual=" + diff.actual()
                + " " + describeContext(context, diff);
    }

    private static String describeLayer(LayerContext context) {
        return context.chunk().regionKey() + " chunk " + context.chunk().key()
                + " sectionY=" + context.key().sectionY() + " " + context.key().layer();
    }

    private static NibbleDiff firstDifference(byte[] expected, byte[] actual) {
        int min = Math.min(expected.length, actual.length);
        for (int index = 0; index < min; index++) {
            int expectedByte = expected[index] & 0xFF;
            int actualByte = actual[index] & 0xFF;
            if (expectedByte == actualByte) {
                continue;
            }
            int expectedLow = expectedByte & 15;
            int actualLow = actualByte & 15;
            if (expectedLow != actualLow) {
                return new NibbleDiff(index, 0, expectedLow, actualLow);
            }
            return new NibbleDiff(index, 1, (expectedByte >>> 4) & 15, (actualByte >>> 4) & 15);
        }
        return expected.length == actual.length ? null : new NibbleDiff(min, 0, expected.length, actual.length);
    }

    private static String describeContext(LayerContext context, NibbleDiff diff) {
        ChunkKey chunkKey = context.chunk().key();
        int x = (chunkKey.x() << 4) + diff.localX();
        int y = (context.key().sectionY() << 4) + diff.localY();
        int z = (chunkKey.z() << 4) + diff.localZ();
        Map<ChunkKey, LightChunk> expected = context.worlds().expectedChunks();
        Map<ChunkKey, LightChunk> actual = context.worlds().actualChunks();
        String layer = context.key().layer();
        BlockPosition position = new BlockPosition(x, y, z);
        WorldLight expectedLight = new WorldLight(expected, layer);
        WorldLight actualLight = new WorldLight(actual, layer);
        return "blocks expected" + blockNeighborhood(expected, position)
                + " actual" + blockNeighborhood(actual, position)
                + " light expected" + lightNeighborhood(expectedLight, position)
                + " actual" + lightNeighborhood(actualLight, position)
                + " path expected/actual" + lightPath(expectedLight, actualLight, position)
                + " path actual/expected" + lightPath(actualLight, expectedLight, position);
    }

    private static String blockNeighborhood(Map<ChunkKey, LightChunk> chunks, BlockPosition position) {
        int x = position.x();
        int y = position.y();
        int z = position.z();
        return "{C=" + blockAtWorld(chunks, position)
                + " D=" + blockAtWorld(chunks, new BlockPosition(x, y - 1, z))
                + " U=" + blockAtWorld(chunks, new BlockPosition(x, y + 1, z))
                + " N=" + blockAtWorld(chunks, new BlockPosition(x, y, z - 1))
                + " S=" + blockAtWorld(chunks, new BlockPosition(x, y, z + 1))
                + " W=" + blockAtWorld(chunks, new BlockPosition(x - 1, y, z))
                + " E=" + blockAtWorld(chunks, new BlockPosition(x + 1, y, z))
                + "}";
    }

    private static String blockAtWorld(Map<ChunkKey, LightChunk> chunks, BlockPosition position) {
        LightChunk chunk = chunks.get(new ChunkKey(position.x() >> 4, position.z() >> 4));
        return chunk == null ? "missing" : chunk.blockStateAt(
                position.y() >> 4,
                new LocalPosition(position.x() & 15, position.y() & 15, position.z() & 15)
        );
    }

    private static String lightNeighborhood(WorldLight world, BlockPosition position) {
        int x = position.x();
        int y = position.y();
        int z = position.z();
        return "{C=" + lightAtWorld(world, position)
                + " D=" + lightAtWorld(world, new BlockPosition(x, y - 1, z))
                + " U=" + lightAtWorld(world, new BlockPosition(x, y + 1, z))
                + " N=" + lightAtWorld(world, new BlockPosition(x, y, z - 1))
                + " S=" + lightAtWorld(world, new BlockPosition(x, y, z + 1))
                + " W=" + lightAtWorld(world, new BlockPosition(x - 1, y, z))
                + " E=" + lightAtWorld(world, new BlockPosition(x + 1, y, z))
                + "}";
    }

    private static String lightAtWorld(WorldLight world, BlockPosition position) {
        int value = lightValueAtWorld(world, position);
        return value < 0 ? "missing" : Integer.toString(value);
    }

    private static String lightPath(
            WorldLight expected,
            WorldLight actual,
            BlockPosition start
    ) {
        int x = start.x();
        int y = start.y();
        int z = start.z();
        StringBuilder builder = new StringBuilder("[");
        for (int step = 0; step < MAX_LIGHT_PATH_STEPS; step++) {
            BlockPosition current = new BlockPosition(x, y, z);
            int value = lightValueAtWorld(expected, current);
            int actualValue = lightValueAtWorld(actual, current);
            if (step != 0) {
                builder.append(" -> ");
            }
            builder.append('(').append(x).append(',').append(y).append(',').append(z).append(")=")
                    .append(value < 0 ? "missing" : value).append('/')
                    .append(actualValue < 0 ? "missing" : actualValue).append(':')
                    .append(blockAtWorld(expected.chunks(), current)).append('/')
                    .append(blockAtWorld(actual.chunks(), current));
            BlockPosition next = nextBrighterPosition(expected, current, value);
            if (value >= 15 || value < 0 || next == null) {
                break;
            }
            x = next.x();
            y = next.y();
            z = next.z();
        }
        return builder.append(']').toString();
    }

    private static BlockPosition nextBrighterPosition(
            WorldLight world,
            BlockPosition position,
            int value
    ) {
        BlockPosition best = null;
        int bestValue = value;
        for (int[] offset : NEIGHBOR_OFFSETS) {
            int nextX = position.x() + offset[0];
            int nextY = position.y() + offset[1];
            int nextZ = position.z() + offset[2];
            BlockPosition candidate = new BlockPosition(nextX, nextY, nextZ);
            int nextValue = lightValueAtWorld(world, candidate);
            if (nextValue > bestValue) {
                bestValue = nextValue;
                best = new BlockPosition(nextX, nextY, nextZ);
            }
        }
        return best;
    }

    private static int lightValueAtWorld(WorldLight world, BlockPosition position) {
        LightChunk chunk = world.chunks().get(new ChunkKey(position.x() >> 4, position.z() >> 4));
        return chunk == null ? -1 : chunk.lightAt(
                world.layer(), position.y() >> 4,
                new LocalPosition(position.x() & 15, position.y() & 15, position.z() & 15)
        );
    }

    private record LayerContext(
            LightGoldenDiff.ChunkComparison chunk,
            LightSectionKey key
    ) {
        LightGoldenDiff.WorldComparison worlds() {
            return this.chunk.worlds();
        }
    }

    private record LayerStorage(boolean present, byte[] bytes, LightChunk chunk, String world) {
    }

    private record WorldLight(Map<ChunkKey, LightChunk> chunks, String layer) {
    }

    private record BlockPosition(int x, int y, int z) {
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
}
