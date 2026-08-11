package com.moepus.byepregen.worldgen;

public final class ArenaHeightmapUpdateTest {
    private ArenaHeightmapUpdateTest() {
    }

    public static void main(String[] args) {
        assertBoundary(true);
        assertBoundary(false);
    }

    private static void assertBoundary(boolean opaque) {
        assertEquals(false, ArenaHeightmapUpdate.isNeeded(opaque, -1));
        assertEquals(!opaque, ArenaHeightmapUpdate.isNeeded(opaque, 0));
        assertEquals(opaque, ArenaHeightmapUpdate.isNeeded(opaque, 1));
    }

    private static void assertEquals(boolean expected, boolean actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
