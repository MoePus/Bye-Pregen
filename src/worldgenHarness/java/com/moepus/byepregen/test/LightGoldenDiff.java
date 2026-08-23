package com.moepus.byepregen.test;

import com.moepus.byepregen.harness.ChunkBounds;
import com.moepus.byepregen.harness.ChunkKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeSet;

public final class LightGoldenDiff {
    private LightGoldenDiff() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: LightGoldenDiff <expected-world-dir> <actual-world-dir>");
            System.err.println("Example: gradlew diffLightGolden -PbyepregenLightGoldenExpectedWorld=run/light-golden/vanilla/world -PbyepregenLightGoldenActualWorld=run/light-golden/ya/world");
            System.exit(2);
        }

        Path expectedWorld = Paths.get(args[0]).toAbsolutePath().normalize();
        Path actualWorld = Paths.get(args[1]).toAbsolutePath().normalize();
        LightDiffResult result = compareWorlds(expectedWorld, actualWorld, LightDiffOptions.fromProperties());
        result.print(expectedWorld, actualWorld);
        if (result.hasFailures()) {
            System.exit(1);
        }
    }

    private static LightDiffResult compareWorlds(
            Path expectedWorld,
            Path actualWorld,
            LightDiffOptions options
    ) throws IOException {
        validateWorldDirectory(expectedWorld, "Expected");
        validateWorldDirectory(actualWorld, "Actual");
        Map<String, Path> expectedRegions = LightWorldReader.regionFiles(expectedWorld);
        Map<String, Path> actualRegions = LightWorldReader.regionFiles(actualWorld);
        validateRegions(expectedWorld, expectedRegions, "Expected");
        validateRegions(actualWorld, actualRegions, "Actual");

        Map<ChunkKey, LightChunk> expectedWorldChunks = LightWorldReader.readWorld(expectedRegions);
        Map<ChunkKey, LightChunk> actualWorldChunks = LightWorldReader.readWorld(actualRegions);
        LightDiffResult result = new LightDiffResult(options);
        WorldComparison comparison = new WorldComparison(
                expectedWorldChunks, actualWorldChunks, options.chunkBounds(), result
        );
        TreeSet<String> regionKeys = new TreeSet<>(expectedRegions.keySet());
        regionKeys.addAll(actualRegions.keySet());
        for (String regionKey : regionKeys) {
            compareRegion(new RegionComparison(
                    regionKey,
                    LightWorldReader.readRegion(expectedRegions.get(regionKey)),
                    LightWorldReader.readRegion(actualRegions.get(regionKey)),
                    comparison
            ));
        }
        return result;
    }

    private static void compareRegion(RegionComparison region) {
        region.worlds().result().regionsCompared++;
        TreeSet<ChunkKey> chunkKeys = new TreeSet<>(region.expectedChunks().keySet());
        chunkKeys.addAll(region.actualChunks().keySet());
        for (ChunkKey chunkKey : chunkKeys) {
            compareChunk(new ChunkComparison(
                    region.regionKey(), chunkKey,
                    region.expectedChunks().get(chunkKey), region.actualChunks().get(chunkKey),
                    region.worlds()
            ));
        }
    }

    private static void compareChunk(ChunkComparison chunk) {
        LightDiffResult result = chunk.worlds().result();
        if (!chunk.worlds().bounds().contains(chunk.key().x(), chunk.key().z())) {
            result.skippedOutOfBoundsChunks++;
            return;
        }
        result.chunksInBounds++;
        if (chunk.expected() == null || chunk.actual() == null) {
            result.missingChunks++;
            result.addIssue(chunk.regionKey() + " chunk " + chunk.key() + " missing in "
                    + (chunk.expected() == null ? "expected" : "actual") + " world");
            return;
        }
        result.chunksCompared++;
        if (!sameTerrainNeighborhood(chunk.key(), chunk.worlds())) {
            result.skippedTerrainChunks++;
            return;
        }
        if (!chunk.expected().lightCorrect || !chunk.actual().lightCorrect) {
            compareLightCorrect(chunk, result);
            return;
        }
        if (!lightCorrectNeighborhood(chunk.key(), chunk.worlds())) {
            result.skippedUnlitNeighborhoodChunks++;
            return;
        }
        result.eligibleChunks++;
        int layersBefore = result.layersCompared;
        LightLayerComparator.compare(chunk);
        if (result.layersCompared != layersBefore) {
            result.chunksWithComparedLayers++;
        }
    }

    private static void compareLightCorrect(ChunkComparison chunk, LightDiffResult result) {
        if (chunk.expected().lightCorrect == chunk.actual().lightCorrect) {
            result.skippedUnlitChunks++;
            return;
        }
        result.lightCorrectMismatches++;
        result.addIssue(chunk.regionKey() + " chunk " + chunk.key()
                + " has different isLightOn"
                + " expected=" + chunk.expected().lightCorrect
                + " actual=" + chunk.actual().lightCorrect
                + " expectedStatus=" + chunk.expected().status
                + " actualStatus=" + chunk.actual().status);
    }

    private static boolean sameTerrainNeighborhood(ChunkKey center, WorldComparison comparison) {
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                ChunkKey key = new ChunkKey(center.x() + dx, center.z() + dz);
                LightChunk expected = comparison.expectedChunks().get(key);
                LightChunk actual = comparison.actualChunks().get(key);
                if (expected == null || actual == null) {
                    if (expected != actual) {
                        return false;
                    }
                } else {
                    String difference = expected.terrainDifference(actual);
                    if (difference != null) {
                        comparison.result().recordTerrainDifference(center, key, difference);
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean lightCorrectNeighborhood(ChunkKey center, WorldComparison comparison) {
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                ChunkKey key = new ChunkKey(center.x() + dx, center.z() + dz);
                LightChunk expected = comparison.expectedChunks().get(key);
                LightChunk actual = comparison.actualChunks().get(key);
                if (expected == null || actual == null) {
                    if (expected != actual) {
                        return false;
                    }
                } else if (!expected.lightCorrect || !actual.lightCorrect) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void validateWorldDirectory(Path world, String label) throws IOException {
        if (!Files.isDirectory(world)) {
            throw new IOException(label + " world directory does not exist: " + world);
        }
    }

    private static void validateRegions(Path world, Map<String, Path> regions, String label) throws IOException {
        if (regions.isEmpty()) {
            throw new IOException(label + " world has no region files: " + world);
        }
    }

    record WorldComparison(
            Map<ChunkKey, LightChunk> expectedChunks,
            Map<ChunkKey, LightChunk> actualChunks,
            ChunkBounds bounds,
            LightDiffResult result
    ) {
    }

    private record RegionComparison(
            String regionKey,
            Map<ChunkKey, LightChunk> expectedChunks,
            Map<ChunkKey, LightChunk> actualChunks,
            WorldComparison worlds
    ) {
    }

    record ChunkComparison(
            String regionKey,
            ChunkKey key,
            LightChunk expected,
            LightChunk actual,
            WorldComparison worlds
    ) {
    }
}
