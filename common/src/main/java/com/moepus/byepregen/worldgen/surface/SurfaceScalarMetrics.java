package com.moepus.byepregen.worldgen.surface;

import java.util.concurrent.atomic.LongAdder;

public final class SurfaceScalarMetrics {
    private static final LongAdder COMPILED = new LongAdder();
    private static final LongAdder REJECTED = new LongAdder();
    private static final LongAdder BINDINGS = new LongAdder();
    private static final LongAdder BIND_FAILURES = new LongAdder();
    private static final LongAdder OUTPUT_COMPARISONS = new LongAdder();
    private static final LongAdder OUTPUT_MISMATCHES = new LongAdder();
    private static volatile SurfaceDirectTemplate.Statistics latest;

    private SurfaceScalarMetrics() {
    }

    static void compiled(SurfaceDirectTemplate.Statistics statistics) {
        COMPILED.increment();
        latest = statistics;
    }

    static void rejected() {
        REJECTED.increment();
    }

    static void binding(SurfaceDirectTemplate.Statistics statistics) {
        BINDINGS.increment();
        latest = statistics;
    }

    static void bindFailure() {
        BIND_FAILURES.increment();
    }

    static void outputComparison() {
        OUTPUT_COMPARISONS.increment();
    }

    static void outputMismatch() {
        OUTPUT_MISMATCHES.increment();
    }

    public static Snapshot snapshot() {
        SurfaceDirectTemplate.Statistics statistics = latest;
        return new Snapshot(
                COMPILED.sum(),
                REJECTED.sum(),
                BINDINGS.sum(),
                BIND_FAILURES.sum(),
                OUTPUT_COMPARISONS.sum(),
                OUTPUT_MISMATCHES.sum(),
                statistics == null ? 0 : statistics.classBytes(),
                statistics == null ? 0 : statistics.regions()
        );
    }

    public record Snapshot(
            long compiled,
            long rejected,
            long bindings,
            long bindFailures,
            long outputComparisons,
            long outputMismatches,
            int latestClassBytes,
            int latestRegions
    ) {
    }
}
