package com.moepus.byepregen.worldgen.postprocess;

import it.unimi.dsi.fastutil.shorts.ShortList;

import java.util.Arrays;

final class PostProcessingSorter {
    private static final int LOCAL_MASK = 15;
    private static final int BUCKET_INTERIOR = 0;
    private static final int BUCKET_NORTH_EDGE = 1;
    private static final int BUCKET_SOUTH_EDGE = 2;
    private static final int BUCKET_WEST_EDGE = 3;
    private static final int BUCKET_EAST_EDGE = 4;
    private static final int BUCKET_CORNERS = 5;
    private static final int POSITION_COUNT = 4096;
    private static final int BITSET_WORDS = POSITION_COUNT >>> 6;
    private static final int BITSET_SORT_THRESHOLD = 64;
    private static final short[] SORT_ORDER = createSortOrder();
    private static final ThreadLocal<long[]> SORT_BITS = ThreadLocal.withInitial(
            () -> new long[BITSET_WORDS]
    );

    private PostProcessingSorter() {
    }

    static void sortAndDeduplicate(ShortList[] postProcessing) {
        long[] sortBits = SORT_BITS.get();
        for (ShortList list : postProcessing) {
            if (list != null && list.size() > 1) {
                sortAndDeduplicate(list, sortBits);
            }
        }
    }

    private static void sortAndDeduplicate(ShortList list, long[] sortBits) {
        if (list.size() < BITSET_SORT_THRESHOLD) {
            insertionSort(list);
            compactSorted(list);
            return;
        }

        bitSetSort(list, sortBits);
    }

    private static void insertionSort(ShortList list) {
        int size = list.size();
        for (int index = 1; index < size; index++) {
            short value = list.getShort(index);
            int key = sortKey(value);
            int insertAt = index;
            while (insertAt > 0) {
                short previous = list.getShort(insertAt - 1);
                if (sortKey(previous) <= key) {
                    break;
                }

                list.set(insertAt, previous);
                insertAt--;
            }

            list.set(insertAt, value);
        }
    }

    private static void compactSorted(ShortList list) {
        int size = list.size();
        int writeIndex = 1;
        short lastPackedPos = list.getShort(0);
        for (int readIndex = 1; readIndex < size; readIndex++) {
            short packedPos = list.getShort(readIndex);
            if (packedPos == lastPackedPos) {
                continue;
            }

            lastPackedPos = packedPos;
            if (writeIndex != readIndex) {
                list.set(writeIndex, packedPos);
            }
            writeIndex++;
        }

        if (writeIndex < size) {
            list.removeElements(writeIndex, size);
        }
    }

    private static void bitSetSort(ShortList list, long[] sortBits) {
        Arrays.fill(sortBits, 0L);

        int size = list.size();
        for (int index = 0; index < size; index++) {
            int palettedIndex = palettedIndex(list.getShort(index));
            sortBits[palettedIndex >>> 6] |= 1L << (palettedIndex & 63);
        }

        int writeIndex = 0;
        for (short packedPos : SORT_ORDER) {
            int palettedIndex = palettedIndex(packedPos);
            if ((sortBits[palettedIndex >>> 6] & (1L << (palettedIndex & 63))) != 0L) {
                list.set(writeIndex++, packedPos);
            }
        }

        if (writeIndex < size) {
            list.removeElements(writeIndex, size);
        }
    }

    private static int sortKey(short packedPos) {
        return (bucket(packedPos) << 12) | palettedIndex(packedPos);
    }

    private static short[] createSortOrder() {
        short[] order = new short[POSITION_COUNT];
        int writeIndex = 0;
        for (int bucket = BUCKET_INTERIOR; bucket <= BUCKET_CORNERS; bucket++) {
            for (int index = 0; index < POSITION_COUNT; index++) {
                short packedPos = packPalettedIndex(index);
                if (bucket(packedPos) == bucket) {
                    order[writeIndex++] = packedPos;
                }
            }
        }

        return order;
    }

    private static int bucket(short packedPos) {
        int localX = localX(packedPos);
        int localZ = localZ(packedPos);
        boolean west = localX == 0;
        boolean east = localX == LOCAL_MASK;
        boolean north = localZ == 0;
        boolean south = localZ == LOCAL_MASK;

        if ((west || east) && (north || south)) return BUCKET_CORNERS;
        if (north) return BUCKET_NORTH_EDGE;
        if (south) return BUCKET_SOUTH_EDGE;
        if (west) return BUCKET_WEST_EDGE;
        if (east) return BUCKET_EAST_EDGE;
        return BUCKET_INTERIOR;
    }

    private static int palettedIndex(short packedPos) {
        return (localY(packedPos) << 8) | (localZ(packedPos) << 4) | localX(packedPos);
    }

    private static short packPalettedIndex(int index) {
        int localX = index & LOCAL_MASK;
        int localZ = (index >>> 4) & LOCAL_MASK;
        int localY = (index >>> 8) & LOCAL_MASK;
        return (short) ((localZ << 8) | (localY << 4) | localX);
    }

    private static int localX(short packedPos) {
        return packedPos & LOCAL_MASK;
    }

    private static int localY(short packedPos) {
        return (packedPos >>> 4) & LOCAL_MASK;
    }

    private static int localZ(short packedPos) {
        return (packedPos >>> 8) & LOCAL_MASK;
    }
}
