package com.moepus.byepregen.dfc.runtime;

import java.util.concurrent.atomic.LongAdder;

/** Optional counters avoid an atomic update on every production column. */
public final class DensityColumnMetrics {
    private static final boolean ENABLED = Boolean.getBoolean("byepregen.dfc.metrics");
    private static final LongAdder COMPILED = new LongAdder();
    private static final LongAdder COLUMNS = new LongAdder();
    private DensityColumnMetrics() { }
    public static void recordCompiled() { if (ENABLED) COMPILED.increment(); }
    public static void recordEvaluatedColumn() { if (ENABLED) COLUMNS.increment(); }
    public static Snapshot snapshot() { return new Snapshot(COMPILED.sum(), COLUMNS.sum()); }
    public record Snapshot(long compiled, long evaluatedColumns) { }
}
