package com.moepus.byepregen.test;

import com.moepus.byepregen.harness.ChunkBounds;
import com.moepus.byepregen.harness.HarnessProperties;

record LightDiffOptions(
        int maxIssues,
        int minComparedLayers,
        boolean missingAsZero,
        boolean requireCompleteChunks,
        double minChunkCoverage,
        ChunkBounds chunkBounds
) {
    private static final String PREFIX = "byepregen.lightGolden.";

    static LightDiffOptions fromProperties() {
        return new LightDiffOptions(
                HarnessProperties.getInt(PREFIX + "maxMismatches", 50),
                HarnessProperties.getInt(PREFIX + "minComparedLayers", 1),
                HarnessProperties.getBoolean(PREFIX + "missingAsZero", false),
                HarnessProperties.getBoolean(PREFIX + "requireCompleteChunks", false),
                HarnessProperties.getDouble(PREFIX + "minChunkCoverage", 0.8D),
                ChunkBounds.fromSystemProperties("byepregen.lightGolden")
        );
    }
}
