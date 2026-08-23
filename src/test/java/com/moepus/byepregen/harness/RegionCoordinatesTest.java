package com.moepus.byepregen.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegionCoordinatesTest {
    @Test
    void parsesCanonicalPositiveAndNegativeNames() throws IOException {
        assertEquals(new RegionCoordinates(12, -7), RegionCoordinates.parse("r.12.-7.mca"));
        assertTrue(RegionCoordinates.isRegionFileName("r.-1.0.mca"));
    }

    @Test
    void rejectsNonRegionAndOverflowingNames() {
        List<String> invalid = List.of("region.0.0.mca", "r.0.mca", "r.x.0.mca");
        for (String fileName : invalid) {
            assertThrows(IOException.class, () -> RegionCoordinates.parse(fileName));
            assertFalse(RegionCoordinates.isRegionFileName(fileName));
        }
        assertThrows(IOException.class, () -> RegionCoordinates.parse("r.2147483648.0.mca"));
        assertTrue(RegionCoordinates.isRegionFileName("r.2147483648.0.mca"));
    }

    @Test
    void convertsRegionLocalCoordinatesToChunkCoordinates() {
        RegionCoordinates region = new RegionCoordinates(-2, 3);

        assertEquals(new ChunkKey(-64, 96), region.chunkAt(0, 0));
        assertEquals(new ChunkKey(-33, 127), region.chunkAt(31, 31));
    }

    @Test
    void chunkKeysRetainZMajorOrdering() {
        List<ChunkKey> keys = List.of(
                new ChunkKey(1, 0), new ChunkKey(0, -1), new ChunkKey(0, 0)
        ).stream().sorted().toList();

        assertEquals(List.of(new ChunkKey(0, -1), new ChunkKey(0, 0), new ChunkKey(1, 0)), keys);
    }
}
