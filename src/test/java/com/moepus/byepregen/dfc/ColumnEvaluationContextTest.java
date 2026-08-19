package com.moepus.byepregen.dfc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import org.junit.jupiter.api.Test;

final class ColumnEvaluationContextTest {
    @Test
    void sentinelUsesStableRawBits() {
        assertEquals(ColumnEvaluationContext.MEMO_MISS_BITS,
                Double.doubleToRawLongBits(ColumnEvaluationContext.MEMO_MISS));
    }

    @Test
    void actualSentinelPayloadCanBeMarkedReady() {
        ColumnEvaluationContext context = activeContext();
        try {
            context.prepareMemoizedCount(1);
            assertFalse(context.memoizedValueReady(0));
            assertTrue(context.memoizedValueMiss(0));
            context.setMemoizedValue(0, ColumnEvaluationContext.MEMO_MISS);
            assertTrue(context.memoizedValueReady(0));
            assertFalse(context.memoizedValueMiss(0));
            assertEquals(ColumnEvaluationContext.MEMO_MISS_BITS,
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
