package com.moepus.byepregen.test;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;

final class ChunkNbtComparator {
    private static final int SECTION_BLOCK_COUNT = 16 * 16 * 16;
    private static final int SECTION_BIOME_COUNT = 4 * 4 * 4;
    private static final int MIN_BLOCK_STATE_BITS = 4;

    private ChunkNbtComparator() {
    }

    static void assertEquivalent(CompoundTag expected, CompoundTag actual, String stage) {
        String difference = firstDifference(expected, actual, "root");
        if (difference != null) {
            throw new AssertionError(stage + " differs at " + difference);
        }
    }

    private static String firstDifference(Tag expected, Tag actual, String path) {
        if (expected == null || actual == null) {
            return path + ": expected=" + summary(expected) + ", actual=" + summary(actual);
        }
        if (expected.getId() != actual.getId()) {
            return path + ": expected type=" + expected.getType() + ", actual type=" + actual.getType();
        }
        if (expected instanceof CompoundTag expectedCompound && actual instanceof CompoundTag actualCompound) {
            return compareCompounds(expectedCompound, actualCompound, path);
        }
        if (expected instanceof CollectionTag<?> expectedList && actual instanceof CollectionTag<?> actualList) {
            return compareCollections(expectedList, actualList, path);
        }
        return expected.equals(actual)
                ? null
                : path + ": expected=" + summary(expected) + ", actual=" + summary(actual);
    }

    private static String compareCompounds(CompoundTag expected, CompoundTag actual, String path) {
        if (isSectionPalettePath(path, "block_states")) {
            return comparePalettedData(expected, actual, path, SECTION_BLOCK_COUNT, MIN_BLOCK_STATE_BITS);
        }
        if (isSectionPalettePath(path, "biomes")) {
            return comparePalettedData(expected, actual, path, SECTION_BIOME_COUNT, 0);
        }
        Set<String> keys = new TreeSet<>(expected.getAllKeys());
        if (!keys.equals(new TreeSet<>(actual.getAllKeys()))) {
            return path + ": expected keys=" + keys + ", actual keys=" + new TreeSet<>(actual.getAllKeys());
        }
        for (String key : keys) {
            String difference = firstDifference(expected.get(key), actual.get(key), path + "." + key);
            if (difference != null) {
                return difference;
            }
        }
        return null;
    }

    private static String compareCollections(
            CollectionTag<?> expected,
            CollectionTag<?> actual,
            String path
    ) {
        if (expected.size() != actual.size()) {
            return path + ": expected size=" + expected.size() + ", actual size=" + actual.size();
        }
        for (int index = 0; index < expected.size(); ++index) {
            String difference = firstDifference(expected.get(index), actual.get(index), path + "[" + index + "]");
            if (difference != null) {
                return difference;
            }
        }
        return null;
    }

    private static String comparePalettedData(
            CompoundTag expected,
            CompoundTag actual,
            String path,
            int valueCount,
            int minimumBits
    ) {
        String shapeDifference = comparePaletteShape(expected, actual, path);
        if (shapeDifference != null) {
            return shapeDifference;
        }
        ListTag expectedPalette = (ListTag) expected.get("palette");
        ListTag actualPalette = (ListTag) actual.get("palette");
        if (expectedPalette.size() != actualPalette.size()) {
            return path + ".palette: expected size=" + expectedPalette.size()
                    + ", actual size=" + actualPalette.size();
        }
        String entriesDifference = comparePaletteEntries(expectedPalette, actualPalette, path);
        if (entriesDifference != null) {
            return entriesDifference;
        }
        int bits = expectedPalette.size() == 1
                ? 0
                : Math.max(minimumBits, ceilLog2(expectedPalette.size()));
        long[] expectedData = expected.getLongArray("data");
        long[] actualData = actual.getLongArray("data");
        String storageDifference = validateStorage(expectedData, actualData, valueCount, bits, path);
        if (storageDifference != null) {
            return storageDifference;
        }
        return compareDecodedValues(expectedPalette, expectedData, actualPalette, actualData, valueCount, bits, path);
    }

    private static String comparePaletteShape(CompoundTag expected, CompoundTag actual, String path) {
        Set<String> expectedKeys = new TreeSet<>(expected.getAllKeys());
        Set<String> actualKeys = new TreeSet<>(actual.getAllKeys());
        if (!expectedKeys.equals(actualKeys)) {
            return path + ": expected keys=" + expectedKeys + ", actual keys=" + actualKeys;
        }
        if (!(expected.get("palette") instanceof ListTag) || !(actual.get("palette") instanceof ListTag)) {
            return path + ".palette: expected and actual values must be lists";
        }
        boolean expectedData = expectedKeys.contains("data");
        boolean actualData = actualKeys.contains("data");
        if (expectedData && !expected.contains("data", Tag.TAG_LONG_ARRAY)
                || actualData && !actual.contains("data", Tag.TAG_LONG_ARRAY)) {
            return path + ".data: expected and actual values must be long arrays";
        }
        if (expectedData != actualData) {
            return path + ".data: long-array presence differs";
        }
        return null;
    }

    private static boolean isSectionPalettePath(String path, String name) {
        return path.startsWith("root.sections[") && path.endsWith("]." + name);
    }

    private static String comparePaletteEntries(ListTag expected, ListTag actual, String path) {
        boolean[] matched = new boolean[actual.size()];
        for (int expectedIndex = 0; expectedIndex < expected.size(); ++expectedIndex) {
            Tag entry = expected.get(expectedIndex);
            int actualIndex = findUnmatched(actual, matched, entry);
            if (actualIndex < 0) {
                return path + ".palette: missing entry " + summary(entry);
            }
            matched[actualIndex] = true;
        }
        return null;
    }

    private static int findUnmatched(ListTag values, boolean[] matched, Tag expected) {
        for (int index = 0; index < values.size(); ++index) {
            if (!matched[index] && expected.equals(values.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private static String validateStorage(
            long[] expected,
            long[] actual,
            int valueCount,
            int bits,
            String path
    ) {
        int expectedLength = bits == 0 ? 0 : (valueCount + Long.SIZE / bits - 1) / (Long.SIZE / bits);
        if (expected.length != expectedLength || actual.length != expectedLength) {
            return path + ".data: expected packed length=" + expectedLength
                    + ", vanilla length=" + expected.length + ", raw length=" + actual.length;
        }
        return null;
    }

    private static String compareDecodedValues(
            ListTag expectedPalette,
            long[] expectedData,
            ListTag actualPalette,
            long[] actualData,
            int valueCount,
            int bits,
            String path
    ) {
        for (int index = 0; index < valueCount; ++index) {
            int expectedId = localId(expectedData, index, bits);
            int actualId = localId(actualData, index, bits);
            if (expectedId >= expectedPalette.size() || actualId >= actualPalette.size()) {
                return path + ".data[" + index + "]: palette id out of bounds, vanilla="
                        + expectedId + ", raw=" + actualId;
            }
            if (!Objects.equals(expectedPalette.get(expectedId), actualPalette.get(actualId))) {
                return path + ".data[" + index + "]: expected=" + summary(expectedPalette.get(expectedId))
                        + ", actual=" + summary(actualPalette.get(actualId));
            }
        }
        return null;
    }

    private static int localId(long[] data, int index, int bits) {
        if (bits == 0) {
            return 0;
        }
        int valuesPerLong = Long.SIZE / bits;
        long mask = (1L << bits) - 1L;
        return (int) (data[index / valuesPerLong] >>> (index % valuesPerLong * bits) & mask);
    }

    private static int ceilLog2(int value) {
        return Integer.SIZE - Integer.numberOfLeadingZeros(value - 1);
    }

    private static String summary(Tag tag) {
        if (tag == null) {
            return "missing";
        }
        if (tag instanceof NumericTag || tag.getId() == Tag.TAG_STRING) {
            return tag.toString();
        }
        if (tag instanceof LongArrayTag array) {
            return "long-array[" + array.getAsLongArray().length + "]";
        }
        return tag.getType() + "(" + tag.sizeInBytes() + " bytes)";
    }
}
