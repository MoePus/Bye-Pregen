package com.moepus.byepregen.test;

import com.moepus.byepregen.api.dfc.ColumnDensityFunctionRegistry;
import com.moepus.byepregen.dfc.compile.DensityColumnCompiler;
import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import com.moepus.byepregen.dfc.runtime.ColumnTemplate;
import com.moepus.byepregen.dfc.runtime.CompiledColumnEvaluator;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

final class DensityColumnDelegateProbe {
    private static final int COLUMN_LENGTH = 5;
    private static final int CELL_HEIGHT = 4;

    private DensityColumnDelegateProbe() {
    }

    static void verify() {
        CountingDelegate dependent = new CountingDelegate();
        assertColumn(dependent, new double[]{0.0D, 4.0D, 8.0D, 12.0D, 16.0D});
        require(dependent.calls.get() == COLUMN_LENGTH,
                "Y-dependent generic delegate was not called once per lane");

        RegisteredDelegate independent = new RegisteredDelegate();
        ColumnDensityFunctionRegistry.registerYIndependentDelegate(RegisteredDelegate.class);
        assertColumn(independent, new double[]{7.0D, 7.0D, 7.0D, 7.0D, 7.0D});
        require(independent.calls.get() == 1,
                "registered 2D delegate was not called once per column");

        SentinelDelegate sentinel = new SentinelDelegate();
        ColumnDensityFunctionRegistry.registerYIndependentDelegate(SentinelDelegate.class);
        assertColumn(sentinel, new double[]{ColumnEvaluationContext.MEMO_MISS,
                ColumnEvaluationContext.MEMO_MISS, ColumnEvaluationContext.MEMO_MISS,
                ColumnEvaluationContext.MEMO_MISS, ColumnEvaluationContext.MEMO_MISS});
        require(sentinel.calls.get() == 1,
                "a valid sentinel-valued memoized delegate was evaluated more than once");

        verifyConditionalLazySlot();
        verifyCacheNormalization();
        verifyScratchCleanupOnFailure();
    }

    private static void verifyConditionalLazySlot() {
        ColumnDensityFunctionRegistry.registerYIndependentDelegate(ConditionalDelegate.class);
        DensityFunction input = DensityFunctions.yClampedGradient(0, 16, 0.0D, 16.0D);

        ConditionalDelegate never = new ConditionalDelegate();
        assertColumn(DensityFunctions.rangeChoice(input, 100.0D, 101.0D,
                never, DensityFunctions.constant(0.0D)), new double[COLUMN_LENGTH]);
        require(never.calls.get() == 0, "unselected lazy delegate was evaluated");

        ConditionalDelegate once = new ConditionalDelegate();
        assertColumn(DensityFunctions.rangeChoice(input, 0.0D, 1.0D,
                once, DensityFunctions.constant(0.0D)),
                new double[]{11.0D, 0.0D, 0.0D, 0.0D, 0.0D});
        require(once.calls.get() == 1, "single-lane lazy delegate was not evaluated exactly once");

        ConditionalDelegate many = new ConditionalDelegate();
        assertColumn(DensityFunctions.rangeChoice(input, 0.0D, 17.0D,
                many, DensityFunctions.constant(0.0D)),
                new double[]{11.0D, 11.0D, 11.0D, 11.0D, 11.0D});
        require(many.calls.get() == 1, "multi-lane lazy delegate was not evaluated exactly once");
    }

    private static void verifyScratchCleanupOnFailure() {
        DensityFunction graph = DensityFunctions.add(DensityFunctions.constant(1.0D),
                DensityFunctions.add(DensityFunctions.constant(2.0D), new ThrowingDelegate()));
        ColumnTemplate template = DensityColumnCompiler.compile(graph);
        require(template.available(), "throwing delegate graph did not compile");
        CompiledColumnEvaluator evaluator = template.bind(value -> value);
        ColumnEvaluationContext context = new ColumnEvaluationContext();
        context.prepare(new double[COLUMN_LENGTH], 0, 0, 0, CELL_HEIGHT,
                source -> new double[COLUMN_LENGTH]);
        try {
            evaluator.evalColumn(context);
            throw new IllegalStateException("throwing delegate unexpectedly returned");
        } catch (ExpectedFailure expected) {
            // Expected. clear() below validates that every nested scratch lease was recycled.
        } finally {
            context.clear();
        }
    }

    private static void verifyCacheNormalization() {
        ConstantCountingDelegate cache2d = new ConstantCountingDelegate();
        assertColumn(DensityFunctions.cache2d(cache2d),
                new double[]{5.0D, 5.0D, 5.0D, 5.0D, 5.0D});
        require(cache2d.calls.get() == 1, "Cache2D child was not materialized once per column");

        CountingDelegate cacheOnce = new CountingDelegate();
        assertColumn(DensityFunctions.cacheOnce(cacheOnce),
                new double[]{0.0D, 4.0D, 8.0D, 12.0D, 16.0D});
        require(cacheOnce.calls.get() == COLUMN_LENGTH, "CacheOnce marker was not removed");

        CountingDelegate allInCell = new CountingDelegate();
        assertColumn(DensityFunctions.cacheAllInCell(allInCell),
                new double[]{0.0D, 4.0D, 8.0D, 12.0D, 16.0D});
        require(allInCell.calls.get() == COLUMN_LENGTH, "CacheAllInCell marker was not removed");

        ConstantCountingDelegate flat = new ConstantCountingDelegate();
        assertColumn(DensityFunctions.flatCache(flat),
                new double[]{5.0D, 5.0D, 5.0D, 5.0D, 5.0D});
        require(flat.calls.get() == 1, "FlatCache source was not sampled once per column");

        CountingDelegate interpolatedChild = new CountingDelegate();
        DensityFunction interpolated = DensityFunctions.interpolated(interpolatedChild);
        ColumnTemplate template = DensityColumnCompiler.compile(interpolated);
        require(template.available(), "Interpolated marker graph did not compile");
        CompiledColumnEvaluator evaluator = template.bind(value -> value);
        ColumnEvaluationContext context = new ColumnEvaluationContext();
        double[] actual = new double[COLUMN_LENGTH];
        double[] boundaries = {3.0D, 4.0D, 5.0D, 6.0D, 7.0D};
        context.prepare(actual, 0, 0, 0, CELL_HEIGHT, source -> boundaries);
        try {
            evaluator.evalColumn(context);
        } finally {
            context.clear();
        }
        for (int i = 0; i < actual.length; ++i) {
            require(actual[i] == boundaries[i], "Interpolated boundary copy mismatch");
        }
        require(interpolatedChild.calls.get() == 0,
                "Interpolated source called its original child instead of the boundary provider");
    }

    private static void assertColumn(DensityFunction function, double[] expected) {
        ColumnTemplate template = DensityColumnCompiler.compile(function);
        require(template.available(), "generic delegate root did not compile: " + template.disabledReason());
        CompiledColumnEvaluator evaluator = template.bind(value -> value);
        ColumnEvaluationContext context = new ColumnEvaluationContext();
        double[] actual = new double[COLUMN_LENGTH];
        context.prepare(actual, 3, 9, 0, CELL_HEIGHT, source -> {
            throw new AssertionError("delegate probe requested an interpolated source");
        });
        try {
            evaluator.evalColumn(context);
        } finally {
            context.clear();
        }
        for (int i = 0; i < expected.length; ++i) {
            require(Double.doubleToRawLongBits(actual[i]) == Double.doubleToRawLongBits(expected[i]),
                    "delegate column mismatch at lane " + i + ": " + actual[i]);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private abstract static class TestDelegate implements DensityFunction {
        final AtomicInteger calls = new AtomicInteger();

        @Override public void fillArray(double[] values, ContextProvider provider) {
            provider.fillAllDirectly(values, this);
        }
        @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(this); }
        @Override public double minValue() { return -1000.0D; }
        @Override public double maxValue() { return 1000.0D; }
        @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return null; }
    }

    private static final class CountingDelegate extends TestDelegate {
        @Override public double compute(FunctionContext context) {
            this.calls.incrementAndGet();
            return context.blockY();
        }
    }

    private static final class RegisteredDelegate extends TestDelegate {
        @Override public double compute(FunctionContext context) {
            this.calls.incrementAndGet();
            return 7.0D;
        }
    }

    private static final class SentinelDelegate extends TestDelegate {
        @Override public double compute(FunctionContext context) {
            this.calls.incrementAndGet();
            return ColumnEvaluationContext.MEMO_MISS;
        }
    }

    private static final class ConditionalDelegate extends TestDelegate {
        @Override public double compute(FunctionContext context) {
            this.calls.incrementAndGet();
            return 11.0D;
        }
    }

    private static final class ConstantCountingDelegate extends TestDelegate {
        @Override public double compute(FunctionContext context) {
            this.calls.incrementAndGet();
            return 5.0D;
        }
    }

    private static final class ThrowingDelegate extends TestDelegate {
        @Override public double compute(FunctionContext context) {
            throw new ExpectedFailure();
        }
    }

    private static final class ExpectedFailure extends RuntimeException {
    }
}
