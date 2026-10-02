package com.moepus.byepregen.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.world.level.LightLayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LightRestartSnapshotTest {
    @TempDir
    Path directory;

    @Test
    void roundTripsBothLayers() throws IOException {
        var expected = snapshot("sky", "block");
        Path path = this.directory.resolve("snapshot");
        expected.write(path);
        assertEquals(expected, LightRestartSnapshot.read(path));
        assertNull(expected.firstDifference(LightRestartSnapshot.read(path)));
    }

    @Test
    void detectsBlockChangesEvenWhenSkyAndValueCountsMatch() {
        assertNotNull(snapshot("sky", "block").firstDifference(snapshot("sky", "changed")));
    }

    @Test
    void detectsSkyChangesEvenWhenBlockAndValueCountsMatch() {
        assertNotNull(snapshot("sky", "block").firstDifference(snapshot("changed", "block")));
    }

    @Test
    void detectsMissingLayerCoverage() {
        var expected = snapshot("sky", "block");
        var missing = new LightRestartSnapshot(expected.samples(), expected.digests().subList(0, 1));
        assertNotNull(expected.firstDifference(missing));
    }

    private static LightRestartSnapshot snapshot(String skyHash, String blockHash) {
        return new LightRestartSnapshot(
                List.of(new LightRestartSnapshot.Sample(8, 72, 8, 0, 0)),
                List.of(new LightRestartSnapshot.ChunkDigest(0, 0, LightLayer.SKY, skyHash, 1, 2, 3),
                        new LightRestartSnapshot.ChunkDigest(0, 0, LightLayer.BLOCK, blockHash, 3, 2, 1)));
    }
}
