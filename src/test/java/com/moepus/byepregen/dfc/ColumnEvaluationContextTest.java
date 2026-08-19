package com.moepus.byepregen.dfc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import org.junit.jupiter.api.Test;

final class ColumnEvaluationContextTest {
    private static final long SENTINEL_BITS = 0x7ffd_db97_2d48_6a4fL;
    private static final double SENTINEL = Double.longBitsToDouble(SENTINEL_BITS);

    @Test
    void actualSentinelPayloadCanBeMarkedReady() {
        ColumnEvaluationContext context = activeContext();
        try {
            context.prepareMemoizedCount(1);
            assertTrue(context.memoizedValueMiss(0));
            context.setMemoizedValue(0, SENTINEL);
            assertFalse(context.memoizedValueMiss(0));
            assertEquals(SENTINEL_BITS,
                    Double.doubleToRawLongBits(context.memoizedValue(0)));
        } finally {
            context.clear();
        }
    }

    @Test
    void onlyCanonicalRawBitsAreMisses() {
        ColumnEvaluationContext context = activeContext();
        try {
            context.prepareMemoizedCount(1);
            context.setMemoizedValue(0, Double.longBitsToDouble(0x7ff8_0000_0000_0001L));
            assertFalse(context.memoizedValueMiss(0), "a non-canonical NaN is a valid result");
        } finally {
            context.clear();
        }
    }

    @Test
    void scratchArraysAreReusedInLifoOrder() {
        ColumnEvaluationContext context = activeContext();
        try {
            double[] first = context.borrowDoubleArray(4);
            double[] second = context.borrowDoubleArray(8);
            context.recycleDoubleArray(second);
            context.recycleDoubleArray(first);
            assertSame(first, context.borrowDoubleArray(4));
            assertSame(second, context.borrowDoubleArray(8));
            context.recycleDoubleArray(second);
            context.recycleDoubleArray(first);
        } finally {
            context.clear();
        }
    }

    private static ColumnEvaluationContext activeContext() {
        ColumnEvaluationContext context = new ColumnEvaluationContext();
        context.prepare(new double[1], 0, 0, 0, 1, source -> new double[1]);
        return context;
    }
}
