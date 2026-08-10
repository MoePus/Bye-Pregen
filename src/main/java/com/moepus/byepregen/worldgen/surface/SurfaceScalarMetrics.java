package com.moepus.byepregen.worldgen.surface;

import java.util.concurrent.atomic.LongAdder;

public final class SurfaceScalarMetrics {
    private static final LongAdder COMPILED = new LongAdder();
    private static final LongAdder REJECTED = new LongAdder();
    private static final LongAdder BUILD_BINDINGS = new LongAdder();
    private static final LongAdder TOP_BINDINGS = new LongAdder();
    private static final LongAdder BIND_FAILURES = new LongAdder();
    private static final LongAdder BUILD_DIFFERENTIAL_EVALUATIONS = new LongAdder();
    private static final LongAdder TOP_DIFFERENTIAL_EVALUATIONS = new LongAdder();
    private static final LongAdder BUILD_DIFFERENTIAL_MISMATCHES = new LongAdder();
    private static final LongAdder TOP_DIFFERENTIAL_MISMATCHES = new LongAdder();
    private static volatile SurfaceDirectTemplate.Statistics latest;

    private SurfaceScalarMetrics() {
    }

    static void compiled(
            SurfaceScalarTarget target,
            SurfaceDirectTemplate.Statistics statistics
    ) {
        COMPILED.increment();
        latest = statistics;
    }

    static void rejected() {
        REJECTED.increment();
    }

    static void binding(
            SurfaceScalarTarget target,
            SurfaceDirectTemplate.Statistics statistics
    ) {
        if (target == SurfaceScalarTarget.BUILD_POINT) {
            BUILD_BINDINGS.increment();
        } else {
            TOP_BINDINGS.increment();
        }
        latest = statistics;
    }

    static void bindFailure() {
        BIND_FAILURES.increment();
    }

    static void differentialEvaluation(SurfaceScalarTarget target) {
        differentialCounter(
                target, BUILD_DIFFERENTIAL_EVALUATIONS, TOP_DIFFERENTIAL_EVALUATIONS
        ).increment();
    }

    static void differentialMismatch(SurfaceScalarTarget target) {
        differentialCounter(
                target, BUILD_DIFFERENTIAL_MISMATCHES, TOP_DIFFERENTIAL_MISMATCHES
        ).increment();
    }

    public static Snapshot snapshot() {
        SurfaceDirectTemplate.Statistics statistics = latest;
        return new Snapshot(
                COMPILED.sum(),
                REJECTED.sum(),
                BUILD_BINDINGS.sum(),
                TOP_BINDINGS.sum(),
                BIND_FAILURES.sum(),
                BUILD_DIFFERENTIAL_EVALUATIONS.sum(),
                TOP_DIFFERENTIAL_EVALUATIONS.sum(),
                BUILD_DIFFERENTIAL_MISMATCHES.sum(),
                TOP_DIFFERENTIAL_MISMATCHES.sum(),
                statistics == null ? 0 : statistics.classBytes(),
                statistics == null ? 0 : statistics.regions()
        );
    }

    public record Snapshot(
            long compiled,
            long rejected,
            long buildBindings,
            long topBindings,
            long bindFailures,
            long buildDifferentialEvaluations,
            long topDifferentialEvaluations,
            long buildDifferentialMismatches,
            long topDifferentialMismatches,
            int latestClassBytes,
            int latestRegions
    ) {
    }

    private static LongAdder differentialCounter(
            SurfaceScalarTarget target,
            LongAdder build,
            LongAdder top
    ) {
        return target == SurfaceScalarTarget.BUILD_POINT ? build : top;
    }
}
