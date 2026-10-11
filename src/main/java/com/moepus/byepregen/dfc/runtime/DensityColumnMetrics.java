package com.moepus.byepregen.dfc.runtime;

import java.util.concurrent.atomic.LongAdder;

public final class DensityColumnMetrics {
    private static final LongAdder COMPILED = new LongAdder();
    private static final LongAdder BOUND = new LongAdder();
    private static final LongAdder BIND_FAILURES = new LongAdder();
    private static final LongAdder BINDING_FALLBACKS = new LongAdder();
    private static final LongAdder ADDED_INTERPOLATORS = new LongAdder();
    private static final LongAdder EVALUATED_COLUMNS = new LongAdder();
    private static final LongAdder VERIFIED_SAMPLES = new LongAdder();

    private DensityColumnMetrics() {
    }

    public static void recordCompiled() { COMPILED.increment(); }
    public static void recordBound() { BOUND.increment(); }
    public static void recordBindFailure() { BIND_FAILURES.increment(); }
    public static void recordBindingFallback() { BINDING_FALLBACKS.increment(); }
    public static void recordAddedInterpolators(long count) { ADDED_INTERPOLATORS.add(count); }
    public static void recordEvaluatedColumn() { EVALUATED_COLUMNS.increment(); }
    public static void recordVerifiedSamples(long count) { VERIFIED_SAMPLES.add(count); }

    public static Snapshot snapshot() {
        return new Snapshot(COMPILED.sum(), BOUND.sum(), BIND_FAILURES.sum(),
                BINDING_FALLBACKS.sum(), ADDED_INTERPOLATORS.sum(), EVALUATED_COLUMNS.sum(),
                VERIFIED_SAMPLES.sum());
    }

    public record Snapshot(
            long compiled,
            long bound,
            long bindFailures,
            long bindingFallbacks,
            long addedInterpolators,
            long evaluatedColumns,
            long verifiedSamples
    ) {
    }
}
