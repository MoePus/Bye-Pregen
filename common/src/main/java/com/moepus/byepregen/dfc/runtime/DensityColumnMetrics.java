package com.moepus.byepregen.dfc.runtime;

import java.util.concurrent.atomic.LongAdder;

public final class DensityColumnMetrics {
    private static final LongAdder COMPILED = new LongAdder();
    private static final LongAdder BOUND = new LongAdder();
    private static final LongAdder BIND_FAILURES = new LongAdder();
    private static final LongAdder BINDING_FALLBACKS = new LongAdder();
    private static final LongAdder ADDED_INTERPOLATORS = new LongAdder();
    private static final LongAdder EVALUATED_COLUMNS = new LongAdder();
    private static final LongAdder VERIFIED_BOUNDARIES = new LongAdder();
    private static final LongAdder BIOME_BOUND = new LongAdder();
    private static final LongAdder BIOME_EVALUATED_COLUMNS = new LongAdder();
    private static final LongAdder BIOME_DEPTH_CACHED_COLUMNS = new LongAdder();
    private static final LongAdder BIOME_FILL_ATTEMPTS = new LongAdder();
    private static final LongAdder BIOME_ARENA_FALLBACKS = new LongAdder();
    private static final LongAdder BIOME_SOURCE_FALLBACKS = new LongAdder();
    private static final LongAdder BIOME_BIND_FALLBACKS = new LongAdder();
    private static final LongAdder BIOME_EVALUATION_FALLBACKS = new LongAdder();
    private static final LongAdder BIOME_FILLS = new LongAdder();
    private static final LongAdder BIOME_VERIFIED_CELLS = new LongAdder();

    private DensityColumnMetrics() {
    }

    public static void recordCompiled() { COMPILED.increment(); }
    public static void recordBound() { BOUND.increment(); }
    public static void recordBindFailure() { BIND_FAILURES.increment(); }
    public static void recordBindingFallback() { BINDING_FALLBACKS.increment(); }
    public static void recordAddedInterpolators(long count) { ADDED_INTERPOLATORS.add(count); }
    public static void recordEvaluatedColumn() { EVALUATED_COLUMNS.increment(); }
    public static void recordVerifiedBoundaries(long count) { VERIFIED_BOUNDARIES.add(count); }
    public static void recordBiomeBound(long count) {
        BOUND.add(count);
        BIOME_BOUND.add(count);
    }
    public static void recordBiomeFillAttempt() { BIOME_FILL_ATTEMPTS.increment(); }
    public static void recordBiomeArenaFallback() { BIOME_ARENA_FALLBACKS.increment(); }
    public static void recordBiomeSourceFallback() { BIOME_SOURCE_FALLBACKS.increment(); }
    public static void recordBiomeBindFallback() { BIOME_BIND_FALLBACKS.increment(); }
    public static void recordBiomeEvaluationFallback() { BIOME_EVALUATION_FALLBACKS.increment(); }
    public static void recordBiomeFill(long evaluatedColumns, long depthCachedColumns) {
        EVALUATED_COLUMNS.add(evaluatedColumns);
        BIOME_EVALUATED_COLUMNS.add(evaluatedColumns);
        BIOME_DEPTH_CACHED_COLUMNS.add(depthCachedColumns);
        BIOME_FILLS.increment();
    }
    public static void recordBiomeVerifiedCells(long count) { BIOME_VERIFIED_CELLS.add(count); }

    public static Snapshot snapshot() {
        long biomeArenaFallbacks = BIOME_ARENA_FALLBACKS.sum();
        long biomeSourceFallbacks = BIOME_SOURCE_FALLBACKS.sum();
        long biomeBindFallbacks = BIOME_BIND_FALLBACKS.sum();
        long biomeEvaluationFallbacks = BIOME_EVALUATION_FALLBACKS.sum();
        long biomeFills = BIOME_FILLS.sum();
        return new Snapshot(COMPILED.sum(), BOUND.sum(), BIND_FAILURES.sum(),
                BINDING_FALLBACKS.sum(), ADDED_INTERPOLATORS.sum(), EVALUATED_COLUMNS.sum(),
                VERIFIED_BOUNDARIES.sum(), BIOME_BOUND.sum(), BIOME_EVALUATED_COLUMNS.sum(),
                BIOME_DEPTH_CACHED_COLUMNS.sum(), BIOME_FILL_ATTEMPTS.sum(),
                biomeArenaFallbacks, biomeSourceFallbacks,
                biomeBindFallbacks, biomeEvaluationFallbacks, biomeFills,
                BIOME_VERIFIED_CELLS.sum());
    }

    public record Snapshot(
            long compiled,
            long bound,
            long bindFailures,
            long bindingFallbacks,
            long addedInterpolators,
            long evaluatedColumns,
            long verifiedBoundaries,
            long biomeBound,
            long biomeEvaluatedColumns,
            long biomeDepthCachedColumns,
            long biomeFillAttempts,
            long biomeArenaFallbacks,
            long biomeSourceFallbacks,
            long biomeBindFallbacks,
            long biomeEvaluationFallbacks,
            long biomeFills,
            long biomeVerifiedCells
    ) {
        public long biomeFallbacks() {
            return this.biomeArenaFallbacks + this.biomeSourceFallbacks
                    + this.biomeBindFallbacks + this.biomeEvaluationFallbacks;
        }

        public long biomeOutcomes() {
            return this.biomeFills + this.biomeFallbacks();
        }
    }
}
