package com.moepus.byepregen.dfc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import com.moepus.byepregen.dfc.runtime.ColumnTestFrames;
import org.junit.jupiter.api.Test;

final class ColumnEvaluationContextTest {
    // 26.3: the lazy memo sentinel moved from a double payload to the float payload declared by
    // ColumnEvaluationContext.MEMO_MISS_BITS, paired with a per-slot "ready" flag.
    private static final int SENTINEL_BITS = 0x7fc1_6a4f;
    private static final float SENTINEL = Float.intBitsToFloat(SENTINEL_BITS);

    @Test
    void actualSentinelPayloadCanBeMarkedReady() {
        ColumnEvaluationContext context = activeContext();
        try {
            context.prepareMemoizedCount(1);
            assertTrue(context.memoizedValueMiss(0));
            context.setMemoizedValue(0, SENTINEL);
            assertFalse(context.memoizedValueMiss(0));
            assertEquals(SENTINEL_BITS,
                    Float.floatToRawIntBits(context.memoizedValue(0)));
        } finally {
            context.clear();
        }
    }

    @Test
    void onlyCanonicalRawBitsAreMisses() {
        ColumnEvaluationContext context = activeContext();
        try {
            context.prepareMemoizedCount(1);
            // 26.3: a miss is the raw sentinel bits with the ready flag cleared, so any other
            // NaN payload is a stored result rather than a miss.
            context.setMemoizedValue(0, Float.intBitsToFloat(0x7f80_0001));
            assertFalse(context.memoizedValueMiss(0), "a non-canonical NaN is a valid result");
        } finally {
            context.clear();
        }
    }

    @Test
    void scratchArraysAreReusedInLifoOrder() {
        ColumnEvaluationContext context = activeContext();
        try {
            // 26.3: the column scratch pool is float based (borrow/recycleFloatArray).
            float[] first = context.borrowFloatArray(4);
            float[] second = context.borrowFloatArray(8);
            context.recycleFloatArray(second);
            context.recycleFloatArray(first);
            assertSame(first, context.borrowFloatArray(4));
            assertSame(second, context.borrowFloatArray(8));
            context.recycleFloatArray(second);
            context.recycleFloatArray(first);
        } finally {
            context.clear();
        }
    }

    private static ColumnEvaluationContext activeContext() {
        return ColumnTestFrames.prepared(new float[1], 0, 0, 0, 1);
    }
}
