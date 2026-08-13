package com.moepus.byepregen.worldgen;

import org.junit.jupiter.api.Test;

public final class ArenaHeightmapUpdateTest {
    private ArenaHeightmapUpdateTest() {
    }

    @Test
    void verifiesCurrentTopBoundary() {
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
