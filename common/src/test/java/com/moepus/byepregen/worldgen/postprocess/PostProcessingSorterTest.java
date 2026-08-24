package com.moepus.byepregen.worldgen.postprocess;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class PostProcessingSorterTest {
    private static final int POSITION_COUNT = 4096;
    private static final int LOCAL_MASK = 15;

    @Test
    void matchesReferenceOrderAcrossThresholdAndDenseInputs() {
        Random random = new Random(0x5EEDC0DEL);
        for (int size : new int[]{2, 15, 63, 64, 65, 256, 1024, 8192}) {
            short[] input = new short[size];
            for (int index = 0; index < size; index++) {
                input[index] = (short) random.nextInt(POSITION_COUNT);
            }
            assertSortedLikeReference(input);
        }
    }

    @Test
    void handlesEveryPositionAndClearsScratchBetweenLists() {
        short[] everyPositionTwice = new short[POSITION_COUNT * 2];
        for (int index = 0; index < everyPositionTwice.length; index++) {
            everyPositionTwice[index] = (short) (index & (POSITION_COUNT - 1));
        }

        short[] sparse = {0, 0, 15, 15, 0x0F00, 0x0F0F, 0x0FFF, 0x0111};
        ShortList denseList = new ShortArrayList(everyPositionTwice);
        ShortList sparseList = new ShortArrayList(sparse);
        PostProcessingSorter.sortAndDeduplicate(new ShortList[]{denseList, sparseList});

        assertArrayEquals(reference(everyPositionTwice), denseList.toShortArray());
        assertArrayEquals(reference(sparse), sparseList.toShortArray());
    }

    private static void assertSortedLikeReference(short[] input) {
        ShortList list = new ShortArrayList(input);
        PostProcessingSorter.sortAndDeduplicate(new ShortList[]{list});
        assertArrayEquals(reference(input), list.toShortArray(), "input size " + input.length);
    }

    private static short[] reference(short[] input) {
        return Arrays.stream(toIntArray(input))
                .distinct()
                .boxed()
                .sorted((left, right) -> Integer.compare(sortKey((short) (int) left), sortKey((short) (int) right)))
                .mapToInt(Integer::intValue)
                .collect(
                        () -> new ShortArrayCollector(input.length),
                        ShortArrayCollector::add,
                        ShortArrayCollector::addAll
                )
                .toArray();
    }

    private static int[] toIntArray(short[] values) {
        int[] result = new int[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = values[index] & (POSITION_COUNT - 1);
        }
        return result;
    }

    private static int sortKey(short packedPos) {
        return (bucket(packedPos) << 12) | palettedIndex(packedPos);
    }

    private static int bucket(short packedPos) {
        int localX = packedPos & LOCAL_MASK;
        int localZ = (packedPos >>> 8) & LOCAL_MASK;
        boolean west = localX == 0;
        boolean east = localX == LOCAL_MASK;
        boolean north = localZ == 0;
        boolean south = localZ == LOCAL_MASK;
        if ((west || east) && (north || south)) return 5;
        if (north) return 1;
        if (south) return 2;
        if (west) return 3;
        if (east) return 4;
        return 0;
    }

    private static int palettedIndex(short packedPos) {
        int localX = packedPos & LOCAL_MASK;
        int localY = (packedPos >>> 4) & LOCAL_MASK;
        int localZ = (packedPos >>> 8) & LOCAL_MASK;
        return (localY << 8) | (localZ << 4) | localX;
    }

    private static final class ShortArrayCollector {
        private final ShortArrayList values;

        private ShortArrayCollector(int expectedSize) {
            this.values = new ShortArrayList(expectedSize);
        }

        private void add(int value) {
            this.values.add((short) value);
        }

        private void addAll(ShortArrayCollector other) {
            this.values.addAll(other.values);
        }

        private short[] toArray() {
            return this.values.toShortArray();
        }
    }
}
