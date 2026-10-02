package com.moepus.byepregen.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moepus.byepregen.harness.ChunkBounds;
import com.moepus.byepregen.harness.ChunkKey;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class LightLayerComparatorTest {
    private static final int MIN_SECTION = -4;
    private static final int BOTTOM_LAST_NIBBLE = 255;
    private static final int NEXT_ROW_FIRST_NIBBLE = 256;

    @Test
    void acceptsSkyDifferencesUpToTwoOnTheBottomRowInEitherDirection() {
        for (int actual : new int[]{6, 7, 9, 10}) {
            LightDiffResult result = compare("SkyLight", MIN_SECTION, BOTTOM_LAST_NIBBLE, actual, null);
            assertEquals(0, result.mismatchedLayers);
            assertEquals(1, result.toleratedBottomSkyLayers);
            assertEquals(1, result.layersCompared);
        }
    }

    @Test
    void rejectsLargerBottomDifference() {
        for (int actual : new int[]{5, 11}) {
            assertEquals(1, compare("SkyLight", MIN_SECTION, 0, actual, null).mismatchedLayers);
        }
    }

    @Test
    void rejectsUnitBlockLightDifference() {
        assertEquals(1, compare("BlockLight", MIN_SECTION, 0, 9, null).mismatchedLayers);
    }

    @Test
    void rejectsHigherRowsAndLowerLightSentinel() {
        assertEquals(1, compare("SkyLight", MIN_SECTION, NEXT_ROW_FIRST_NIBBLE, 9, null).mismatchedLayers);
        assertEquals(1, compare("SkyLight", MIN_SECTION - 1, 0, 9, null).mismatchedLayers);
        assertEquals(1, compare("SkyLight", MIN_SECTION + 1, 0, 9, null).mismatchedLayers);
    }

    @Test
    void continuesAfterToleratedLowNibbleToCheckHighNibble() {
        assertEquals(1, compare("SkyLight", MIN_SECTION, 0, 9, 1).mismatchedLayers);
    }

    @Test
    void continuesAfterToleratedBottomRowToCheckRestOfSection() {
        assertEquals(1, compare("SkyLight", MIN_SECTION, 0, 9, NEXT_ROW_FIRST_NIBBLE).mismatchedLayers);
    }

    @Test
    void doesNotGuessMinimumHeightWhenMetadataIsMissingOrDifferent() {
        LightChunk expected = chunk(MIN_SECTION);
        LightChunk actual = chunk(MIN_SECTION);
        expected.minBlockY = null;
        assertEquals(1, compare(expected, actual).mismatchedLayers);
        expected.minBlockY = (MIN_SECTION - 1) << 4;
        assertEquals(1, compare(expected, actual).mismatchedLayers);
    }

    @Test
    void readsMinimumHeightFromVanillaSectionMetadata() {
        assertEquals(-64, chunk(MIN_SECTION).minBlockY);
        assertEquals(0, chunk(0).minBlockY);
        assertEquals(-128, chunk(-8).minBlockY);
    }

    private static LightDiffResult compare(String layer, int sectionY, int index, int value, Integer otherError) {
        LightChunk expected = chunk(MIN_SECTION);
        LightChunk actual = chunk(MIN_SECTION);
        LightSectionKey key = new LightSectionKey(sectionY, layer);
        byte[] expectedBytes = new byte[LightChunk.LIGHT_BYTES];
        byte[] actualBytes = new byte[LightChunk.LIGHT_BYTES];
        set(expectedBytes, index, 8);
        set(actualBytes, index, value);
        if (otherError != null) {
            set(actualBytes, otherError, 3);
        }
        expected.lights.put(key, expectedBytes);
        actual.lights.put(key, actualBytes);
        return runComparison(expected, actual);
    }

    private static LightDiffResult compare(LightChunk expected, LightChunk actual) {
        LightSectionKey key = new LightSectionKey(MIN_SECTION, "SkyLight");
        byte[] bytes = new byte[LightChunk.LIGHT_BYTES];
        bytes[0] = 1;
        expected.lights.put(key, new byte[LightChunk.LIGHT_BYTES]);
        actual.lights.put(key, bytes);
        return runComparison(expected, actual);
    }

    private static LightDiffResult runComparison(LightChunk expected, LightChunk actual) {
        ChunkKey key = new ChunkKey(0, 0);
        ChunkBounds bounds = new ChunkBounds(0, 0, 0, 0);
        LightDiffResult result = new LightDiffResult(new LightDiffOptions(10, 1, false, false, 1.0, bounds));
        var worlds = new LightGoldenDiff.WorldComparison(Map.of(key, expected), Map.of(key, actual), bounds, result);
        LightLayerComparator.compare(new LightGoldenDiff.ChunkComparison("test", key, expected, actual, worlds));
        return result;
    }

    private static LightChunk chunk(int minSection) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("yPos", minSection);
        return LightChunk.from(tag);
    }

    private static void set(byte[] data, int index, int value) {
        int shift = (index & 1) * 4;
        data[index >>> 1] = (byte)((data[index >>> 1] & ~(15 << shift)) | (value << shift));
    }
}
