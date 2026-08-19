package com.moepus.byepregen.dfc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moepus.byepregen.api.dfc.ColumnDensityFunctionRegistry;
import com.moepus.byepregen.dfc.compile.DensityColumnCompiler;
import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import com.moepus.byepregen.dfc.runtime.ColumnTemplate;
import com.moepus.byepregen.dfc.runtime.CompiledColumnEvaluator;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Covered by DensityColumnDelegateProbe in the bootstrapped worldgen harness")
final class DensityColumnCompilerTest {
    @Test
    void unknownDelegateCompilesAndRunsOncePerLane() {
        CountingDelegate delegate = new CountingDelegate();
        double[] output = evaluate(delegate, 4);

        assertArrayEquals(new double[]{0.0D, 4.0D, 8.0D, 12.0D}, output);
        assertEquals(4, delegate.calls.get());
    }

    @Test
    void registeredDelegateUsesOneLazyColumnSlot() {
        RegisteredDelegate delegate = new RegisteredDelegate();
        ColumnDensityFunctionRegistry.registerYIndependentDelegate(RegisteredDelegate.class);
        double[] output = evaluate(delegate, 5);

        assertArrayEquals(new double[]{7.0D, 7.0D, 7.0D, 7.0D, 7.0D}, output);
        assertEquals(1, delegate.calls.get());
    }

    private static double[] evaluate(DensityFunction function, int length) {
        ColumnTemplate template = DensityColumnCompiler.compile(function);
        assertTrue(template.available(), template.disabledReason());
        CompiledColumnEvaluator evaluator = template.bind(value -> value);
        ColumnEvaluationContext context = new ColumnEvaluationContext();
        double[] output = new double[length];
        context.prepare(output, 3, 9, 0, 4, source -> {
            throw new AssertionError("No interpolated source expected");
        });
        try {
            evaluator.evalColumn(context);
        } finally {
            context.clear();
        }
        return output;
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
}
