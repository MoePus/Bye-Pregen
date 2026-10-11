package com.moepus.byepregen.dfc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.*;
import com.moepus.byepregen.dfc.codegen.ColumnClassBuilder;
import com.moepus.byepregen.dfc.codegen.ColumnClassDefiner;
import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import com.moepus.byepregen.dfc.runtime.ColumnTemplate;
import com.moepus.byepregen.dfc.runtime.CompiledColumnEvaluator;
import com.moepus.byepregen.worldgen.arena.ArenaInterpolationColumn;
import com.moepus.byepregen.worldgen.arena.InterpolatedMarkerAccess;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

final class InterpolatedDensityColumnTest {
    private static final int HEIGHT = 16;
    private static final int MIN_Y = -64;

    @BeforeAll
    static void initializeDensityCodecs() {
        SplineTestFixtures.coordinate();
    }

    @Test
    void nonlinearOperationsUseInteriorInterpolatedInputs() throws Throwable {
        CountingSource source = new CountingSource();
        SourceNode input = new SourceNode(source, SourceMode.INTERPOLATED);
        List<Case> cases = List.of(
                new Case(new AbsNode(input), Math::abs),
                new Case(new MinNode(input, new ConstantNode(0.25D)), v -> Math.min(v, 0.25D)),
                new Case(new MaxNode(input, new ConstantNode(-0.25D)), v -> Math.max(v, -0.25D)),
                new Case(new SqueezeNode(input), v -> v / 2.0D - v * v * v / 24.0D),
                new Case(new RangeChoiceNode(input, -0.3D, 0.3D,
                        new AbsNode(input), new SqueezeNode(input)),
                        v -> v >= -0.3D && v < 0.3D ? Math.abs(v) : v / 2.0D - v * v * v / 24.0D));
        ArenaInterpolationColumn column = inputColumn();
        for (Case test : cases) {
            double[] actual = evaluate(compile(test.graph(), 0), column, new ColumnEvaluationContext());
            for (int i = 0; i < HEIGHT; i++) {
                assertEquals(test.reference().applyAsDouble(column.valueAt(i)), actual[i], 1.0E-15D);
            }
        }
        assertEquals(0, source.calls.get(), "interpolated noise must not be re-evaluated per block");
    }

    @Test
    void fragmentedBranchesShareOne2dMemoPerWholeColumn() throws Throwable {
        CountingSource source = new CountingSource();
        CountingSource memo = new CountingSource();
        SourceNode input = new SourceNode(source, SourceMode.INTERPOLATED);
        AstNode memoized = new Memoized2DNode(new DelegateNode(memo, true), 0);
        AstNode graph = new RangeChoiceNode(input, -0.3D, 0.3D,
                new AddNode(input, memoized), new ConstantNode(-2.0D));
        CompiledColumnEvaluator evaluator = compile(graph, 1);
        ColumnEvaluationContext context = new ColumnEvaluationContext();
        ArenaInterpolationColumn column = inputColumn();
        for (int iteration = 1; iteration <= 2; iteration++) {
            double[] actual = evaluate(evaluator, column, context);
            for (int i = 0; i < HEIGHT; i++) {
                double v = column.valueAt(i);
                assertEquals(v >= -0.3D && v < 0.3D ? v + 7.0D : -2.0D, actual[i]);
            }
            assertEquals(iteration, memo.calls.get());
        }
    }

    @Test
    void repeatedInterpolatedSourceSeesChangedValuesOnNextColumn() throws Throwable {
        CountingSource source = new CountingSource();
        SourceNode input = new SourceNode(source, SourceMode.INTERPOLATED);
        AstNode graph = new RangeChoiceNode(input, -0.3D, 0.3D,
                new AddNode(input, new ConstantNode(1.0D)), new ConstantNode(-2.0D));
        CompiledColumnEvaluator evaluator = compile(graph, 0);
        double[] edges = {-1, 0.125D, 1, -0.125D, -1, 0.125D};
        ArenaInterpolationColumn column = new ArenaInterpolationColumn(edges, 8, HEIGHT);
        ColumnEvaluationContext context = new ColumnEvaluationContext();
        for (int z = 0; z < 4; z++) {
            column.prepare(z);
            double[] output = evaluate(evaluator, column, context);
            for (int i = 0; i < HEIGHT; i++) {
                double value = column.valueAt(i);
                assertEquals(value >= -0.3D && value < 0.3D ? value + 1.0D : -2.0D, output[i]);
            }
        }
    }

    @Test
    void providerIsResolvedOnceAcrossRangesAndPointAccessWithNegativeWorldY() {
        CountingSource source = new CountingSource();
        AtomicInteger resolutions = new AtomicInteger();
        ArenaInterpolationColumn column = inputColumn();
        ColumnEvaluationContext context = new ColumnEvaluationContext();
        double[] actual = new double[HEIGHT];
        context.prepare(actual, 9, -13, MIN_Y, 1, requested -> {
            assertSame(source, requested);
            resolutions.incrementAndGet();
            return column;
        });
        try {
            context.prepareInterpolationCount(1);
            context.copyInterpolatedColumnRange(0, source, actual, 0, 3);
            context.copyInterpolatedColumnRange(0, source, actual, 3, HEIGHT);
            for (int i = 0; i < HEIGHT; i++) {
                assertEquals(column.valueAt(i), actual[i]);
                assertEquals(actual[i], context.interpolatedValue(0, source, MIN_Y + i));
            }
            assertEquals(1, resolutions.get());
        } finally {
            context.clear();
        }
    }

    private static ArenaInterpolationColumn inputColumn() {
        ArenaInterpolationColumn column = new ArenaInterpolationColumn(
                new double[]{-1, 0, 1, 0, -1, 0}, 8, HEIGHT);
        column.prepare(0);
        return column;
    }

    private static double[] evaluate(CompiledColumnEvaluator evaluator, ArenaInterpolationColumn column,
                                     ColumnEvaluationContext context) {
        double[] output = new double[HEIGHT];
        context.prepare(output, 9, -13, MIN_Y, 1, source -> column);
        try {
            evaluator.evalColumn(context);
        } finally {
            context.clear();
        }
        return output;
    }

    private static CompiledColumnEvaluator compile(AstNode root, int memoCount) throws Throwable {
        ColumnClassBuilder.BuildResult generated = new ColumnClassBuilder(memoCount).build(root);
        Object[] bindings = generated.bindings().stream().map(ColumnTemplate.Binding::value).toArray();
        return (CompiledColumnEvaluator) ColumnClassDefiner.defineConstructor(generated.classBytes())
                .invoke((Object) bindings);
    }

    private record Case(AstNode graph, DoubleUnaryOperator reference) { }

    private static final class CountingSource implements DensityFunction.SimpleFunction, InterpolatedMarkerAccess {
        private Object token;
        @Override public Object byepregen$getInterpolationToken() { return token; }
        @Override public void byepregen$setInterpolationToken(Object token) { this.token = token; }
        private final AtomicInteger calls = new AtomicInteger();
        @Override public double compute(FunctionContext context) { calls.incrementAndGet(); return 7.0D; }
        @Override public double minValue() { return -10.0D; }
        @Override public double maxValue() { return 10.0D; }
        @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return null; }
    }

}
