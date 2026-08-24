package com.moepus.byepregen.test;

import com.moepus.byepregen.harness.ChunkBounds;
import com.moepus.byepregen.harness.ChunkKey;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class LightDiffResult {
    private final int maxIssues;
    private final int minComparedLayers;
    final boolean missingAsZero;
    private final boolean requireCompleteChunks;
    private final double minChunkCoverage;
    private final ChunkBounds chunkBounds;
    private final List<String> issues;
    private int suppressedIssues;
    int regionsCompared;
    int chunksCompared;
    long chunksInBounds;
    long eligibleChunks;
    long chunksWithComparedLayers;
    int skippedOutOfBoundsChunks;
    int skippedUnlitChunks;
    int skippedUnlitNeighborhoodChunks;
    int skippedTerrainChunks;
    int lightCorrectMismatches;
    int layersCompared;
    int missingChunks;
    int missingLayers;
    int storageNoiseLayers;
    int invalidLayers;
    int mismatchedLayers;
    private String firstTerrainDifference;

    LightDiffResult(LightDiffOptions options) {
        this.maxIssues = Math.max(0, options.maxIssues());
        this.minComparedLayers = Math.max(0, options.minComparedLayers());
        this.missingAsZero = options.missingAsZero();
        this.requireCompleteChunks = options.requireCompleteChunks();
        this.minChunkCoverage = options.minChunkCoverage();
        this.chunkBounds = options.chunkBounds();
        this.issues = new ArrayList<>(Math.min(this.maxIssues, 64));
        if (this.requireCompleteChunks && !this.chunkBounds.isFullyBounded()) {
            throw new IllegalArgumentException("Complete chunk coverage requires finite chunk bounds");
        }
        if (!(this.minChunkCoverage > 0.0D && this.minChunkCoverage <= 1.0D)) {
            throw new IllegalArgumentException("minChunkCoverage must be in (0, 1], got "
                    + this.minChunkCoverage);
        }
    }

    boolean hasFailures() {
        return this.layersCompared < this.minComparedLayers
                || this.missingChunks != 0
                || this.hasIncompleteCoverage()
                || this.lightCorrectMismatches != 0
                || this.missingLayers != 0
                || this.invalidLayers != 0
                || this.mismatchedLayers != 0;
    }

    void addIssue(String issue) {
        if (this.issues.size() < this.maxIssues) {
            this.issues.add(issue);
        } else {
            this.suppressedIssues++;
        }
    }

    void recordTerrainDifference(ChunkKey compared, ChunkKey terrain, String difference) {
        if (this.firstTerrainDifference == null) {
            this.firstTerrainDifference = "compared=" + compared + " terrain=" + terrain + " " + difference;
        }
    }

    void print(Path expectedWorld, Path actualWorld) {
        System.out.println("Light golden diff");
        System.out.println("  expected: " + expectedWorld);
        System.out.println("  actual:   " + actualWorld);
        System.out.println("  mode:     " + (this.missingAsZero
                ? "missing light tags compare as zero"
                : "semantic SkyLight, strict BlockLight storage"));
        if (this.chunkBounds.isLimited()) {
            System.out.println("  bounds:   " + this.chunkBounds.display()
                    + ", " + this.skippedOutOfBoundsChunks + " skipped-out-of-bounds chunk(s)");
        }
        System.out.println("  require:  at least " + this.minComparedLayers + " compared light layer(s)");
        this.printCoverage();
        System.out.println("  regions:  " + this.regionsCompared);
        System.out.println("  chunks:   " + this.chunksCompared
                + " compared, " + this.missingChunks + " missing, "
                + this.skippedUnlitChunks + " skipped-unlit, "
                + this.skippedUnlitNeighborhoodChunks + " skipped-unlit-neighborhood, "
                + this.skippedTerrainChunks + " skipped-terrain, "
                + this.lightCorrectMismatches + " light-correct mismatched");
        System.out.println("  layers:   " + this.layersCompared + " compared, "
                + this.missingLayers + " missing, "
                + this.storageNoiseLayers + " storage-noise, "
                + this.invalidLayers + " invalid, "
                + this.mismatchedLayers + " mismatched");
        if (this.firstTerrainDifference != null) {
            System.out.println("  terrain:  " + this.firstTerrainDifference);
        }
        this.printOutcome();
    }

    private void printCoverage() {
        if (this.requireCompleteChunks) {
            System.out.println("  coverage: require all " + this.chunkBounds.expectedChunks()
                    + " bounded chunk(s) present and at least " + this.requiredComparedChunks()
                    + " with compared layers; " + this.chunksInBounds + " present in union, "
                    + this.eligibleChunks + " eligible, " + this.chunksWithComparedLayers
                    + " with compared layers");
        }
    }

    private void printOutcome() {
        if (!this.hasFailures()) {
            System.out.println("Light golden diff passed");
            return;
        }
        System.out.println("Light golden diff FAILED");
        if (this.layersCompared < this.minComparedLayers) {
            System.out.println("  - only compared " + this.layersCompared
                    + " light layer(s), below required " + this.minComparedLayers);
        }
        if (this.missingChunks != 0) {
            System.out.println("  - " + this.missingChunks + " chunk(s) are missing from one world");
        }
        if (this.hasIncompleteCoverage()) {
            System.out.println("  - bounded chunk coverage is incomplete or contains skipped chunks");
        }
        for (String issue : this.issues) {
            System.out.println("  - " + issue);
        }
        if (this.suppressedIssues != 0) {
            System.out.println("  ... " + this.suppressedIssues + " more issue(s) suppressed");
        }
    }

    private boolean hasIncompleteCoverage() {
        if (!this.requireCompleteChunks) {
            return false;
        }
        long expectedChunks = this.chunkBounds.expectedChunks();
        return this.chunksInBounds != expectedChunks
                || this.chunksWithComparedLayers < this.requiredComparedChunks();
    }

    private long requiredComparedChunks() {
        return (long)Math.ceil(this.chunkBounds.expectedChunks() * this.minChunkCoverage);
    }
}
