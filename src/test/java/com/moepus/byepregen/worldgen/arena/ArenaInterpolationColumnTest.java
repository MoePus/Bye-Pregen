package com.moepus.byepregen.worldgen.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class ArenaInterpolationColumnTest {
    private static final int HEIGHT = 96;
    private static final int PAGE_HEIGHT = 4;
    private static final double UNWRITTEN = 1234567.0D;

    @Test
    void rangesAndPointSamplesMatchScalarSteppingBitwise() {
        for (int cellHeight : new int[]{4, 8, 12, 16, 24, 32}) {
            double[] edges = edges(cellHeight);
            ArenaInterpolationColumn column = new ArenaInterpolationColumn(edges, cellHeight, HEIGHT);
            for (int z = 0; z < 4; z++) {
                column.prepare(z);
                verifyRanges(column, scalarColumn(edges, cellHeight, z));
            }
        }
    }

    @Test
    void preparingNextColumnInvalidatesCachedPageAndSeesReusedEdges() {
        double[] edges = edges(8);
        ArenaInterpolationColumn column = new ArenaInterpolationColumn(edges, 8, HEIGHT);
        column.prepare(0);
        column.valueAt(3);
        Arrays.fill(edges, 0.25D);
        column.prepare(0);
        verifyRanges(column, scalarColumn(edges, 8, 0));
        column.prepare(3);
        verifyRanges(column, scalarColumn(edges, 8, 3));
    }

    @Test
    void signedZeroAndSubnormalInputsKeepScalarRoundingOrder() {
        double[] edges = new double[(HEIGHT / 12 + 1) * 2];
        for (double scale : new double[]{0.0D, Double.MIN_VALUE, Double.MIN_NORMAL}) {
            for (int i = 0; i < edges.length; i++) edges[i] = i % 4 < 2 ? -scale : scale;
            ArenaInterpolationColumn column = new ArenaInterpolationColumn(edges, 12, HEIGHT);
            column.prepare(0);
            verifyRanges(column, scalarColumn(edges, 12, 0));
        }
    }

    @Test
    void repeatedGenerationKeepsThePageCacheButNewGenerationInvalidatesIt() {
        double[] edges = edges(8);
        ArenaInterpolationColumn column = new ArenaInterpolationColumn(edges, 8, HEIGHT);
        column.prepareIfChanged(0, 17L);
        double first = column.valueAt(3);
        Arrays.fill(edges, 0.25D);
        column.prepareIfChanged(0, 17L);
        assertBits(first, column.valueAt(3));
        column.prepareIfChanged(0, 18L);
        assertBits(scalarColumn(edges, 8, 0)[3], column.valueAt(3));
    }

    @Test
    void invalidGeometryAndRangesFailExplicitly() {
        assertThrows(IllegalArgumentException.class, () -> new ArenaInterpolationColumn(new double[2], 3, 6));
        assertThrows(IllegalArgumentException.class, () -> new ArenaInterpolationColumn(new double[2], 8, 64));
        ArenaInterpolationColumn column = new ArenaInterpolationColumn(edges(8), 8, HEIGHT);
        assertThrows(IndexOutOfBoundsException.class, () -> column.valueAt(HEIGHT));
        assertThrows(IndexOutOfBoundsException.class, () -> column.fillRange(new double[HEIGHT], -1, 4));
        assertThrows(IllegalArgumentException.class, () -> column.fillRange(new double[1], 0, 1));
    }

    private static void verifyRanges(ArenaInterpolationColumn column, double[] expected) {
        for (int i = HEIGHT - 1; i >= 0; i--) assertBits(expected[i], column.valueAt(i));
        double[] actual = new double[HEIGHT];
        for (int from = 0; from <= HEIGHT; from++) {
            for (int to = from; to <= HEIGHT; to++) {
                Arrays.fill(actual, UNWRITTEN);
                column.fillRange(actual, from, to);
                verifyRange(actual, expected, from, to);
            }
        }
    }

    private static void verifyRange(double[] actual, double[] expected, int from, int to) {
        for (int i = 0; i < HEIGHT; i++) {
            assertBits(i >= from && i < to ? expected[i] : UNWRITTEN, actual[i]);
        }
    }

    // Independent reference: the non-DFC Arena's descending page state transitions.
    private static double[] scalarColumn(double[] edges, int cellHeight, int z) {
        double[] result = new double[HEIGHT];
        for (int cell = 0; cell < HEIGHT / cellHeight; cell++) {
            double lower = edges[cell * 2] + z * edges[cell * 2 + 1];
            double upper = edges[cell * 2 + 2] + z * edges[cell * 2 + 3];
            double difference = upper - lower;
            double value = upper;
            for (int page = cellHeight - PAGE_HEIGHT; page >= 0; page -= PAGE_HEIGHT) {
                double pageLower = page == 0 ? lower : lower + (double) page / cellHeight * difference;
                double step = (pageLower - value) * 0.25D;
                int base = cell * cellHeight + page;
                result[base + 3] = value += step;
                result[base + 2] = value += step;
                result[base + 1] = value = pageLower - step;
                result[base] = value = pageLower;
            }
        }
        return result;
    }

    private static double[] edges(int cellHeight) {
        Random random = new Random(719823);
        double[] result = new double[(HEIGHT / cellHeight + 1) * 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = Math.scalb(random.nextDouble() - 0.5D, random.nextInt(40) - 20);
        }
        return result;
    }

    private static void assertBits(double expected, double actual) {
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
    }
}
