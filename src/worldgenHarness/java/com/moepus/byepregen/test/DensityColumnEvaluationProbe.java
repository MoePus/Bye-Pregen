package com.moepus.byepregen.test;

import com.moepus.byepregen.dfc.compile.DensityColumnCompiler;
import com.moepus.byepregen.dfc.opt.ColumnOptimizer;
import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import com.moepus.byepregen.dfc.runtime.ColumnTemplate;
import com.moepus.byepregen.dfc.runtime.CompiledColumnEvaluator;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

final class DensityColumnEvaluationProbe {
    private static final int COLUMN_LENGTH = 33;
    private static final int CELL_HEIGHT = 4;
    private static final double DOUBLE_TOLERANCE = 1.0E-12D;
    private static final double SPLINE_TOLERANCE = 1.0E-6D;

    private DensityColumnEvaluationProbe() {
    }

    static void verify() {
        NoiseFixture fixture = noiseFixture();
        verifyGraph(bindNoise(ordinaryGraph(fixture.parameters()), fixture.noise()), DOUBLE_TOLERANCE);
        verifyGraph(splineGraph(), SPLINE_TOLERANCE);
        verifyGraph(DensityFunctions.interpolated(bindNoise(
                DensityFunctions.noise(fixture.parameters(), 0.125D, 0.25D), fixture.noise())),
                DOUBLE_TOLERANCE);
        verifyUnknownPassDisablesCompiler();
    }

    private static DensityFunction ordinaryGraph(Holder<NormalNoise.NoiseParameters> parameters) {
        DensityFunction gradient = DensityFunctions.yClampedGradient(-64, 64, -1.0D, 1.0D);
        DensityFunction noise = DensityFunctions.noise(parameters, 0.125D, 0.25D);
        DensityFunction shifted = DensityFunctions.shiftedNoise2d(
                DensityFunctions.constant(0.25D), DensityFunctions.constant(-0.5D),
                0.2D, parameters);
        DensityFunction choice = DensityFunctions.rangeChoice(
                gradient, -0.5D, 0.5D, noise.abs(), shifted.square());
        DensityFunction weird = DensityFunctions.weirdScaledSampler(
                gradient, parameters, DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE2);
        DensityFunction sum = DensityFunctions.add(
                DensityFunctions.mul(DensityFunctions.constant(0.75D), choice),
                DensityFunctions.add(weird.halfNegative(), gradient.squeeze()));
        return DensityFunctions.max(DensityFunctions.constant(-2.0D),
                DensityFunctions.min(DensityFunctions.constant(2.0D), sum));
    }

    private static DensityFunction splineGraph() {
        DensityFunction coordinateFunction = DensityFunctions.yClampedGradient(
                -64, 64, -1.0D, 1.0D);
        DensityFunctions.Spline.Coordinate coordinate =
                new DensityFunctions.Spline.Coordinate(Holder.direct(coordinateFunction));
        CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline =
                new CubicSpline.Multipoint<>(coordinate,
                        new float[]{-1.0F, 0.0F, 1.0F},
                        List.of(CubicSpline.constant(-0.75F), CubicSpline.constant(0.25F),
                                CubicSpline.constant(1.5F)),
                        new float[]{0.0F, 0.5F, 0.0F}, -0.75F, 1.5F);
        return DensityFunctions.add(DensityFunctions.constant(0.5D), DensityFunctions.spline(spline));
    }

    private static DensityFunction bindNoise(DensityFunction graph, NormalNoise noise) {
        return graph.mapAll(new DensityFunction.Visitor() {
            @Override
            public DensityFunction apply(DensityFunction function) {
                return function;
            }

            @Override
            public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder holder) {
                return new DensityFunction.NoiseHolder(holder.noiseData(), noise);
            }
        });
    }

    private static void verifyGraph(DensityFunction graph, double tolerance) {
        ColumnTemplate template = DensityColumnCompiler.compile(graph);
        require(template.available(), "differential graph did not compile: " + template.disabledReason());
        CompiledColumnEvaluator evaluator = template.bind(value -> value);
        verifyAt(evaluator, graph, -31, 47, -64, tolerance);
        verifyAt(evaluator, graph, 7, -19, -20, tolerance);
    }

    private static void verifyAt(
            CompiledColumnEvaluator evaluator,
            DensityFunction graph,
            int x,
            int z,
            int minY,
            double tolerance
    ) {
        double[] actual = new double[COLUMN_LENGTH];
        ColumnEvaluationContext context = new ColumnEvaluationContext();
        context.prepare(actual, x, z, minY, CELL_HEIGHT,
                source -> referenceColumn(source, x, z, minY));
        try {
            evaluator.evalColumn(context);
        } finally {
            context.clear();
        }
        for (int lane = 0; lane < actual.length; ++lane) {
            int y = minY + lane * CELL_HEIGHT;
            DensityFunction.FunctionContext point = new DensityFunction.SinglePointContext(x, y, z);
            double expected = graph.compute(point);
            double allowed = tolerance * (1.0D + Math.abs(expected));
            require(Math.abs(expected - actual[lane]) <= allowed,
                    "column differential mismatch at " + x + ',' + y + ',' + z
                            + ": expected=" + expected + ", actual=" + actual[lane]
                            + ", tolerance=" + allowed);
        }
    }

    private static double[] referenceColumn(DensityFunction source, int x, int z, int minY) {
        double[] values = new double[COLUMN_LENGTH];
        for (int lane = 0; lane < values.length; ++lane) {
            int y = minY + lane * CELL_HEIGHT;
            values[lane] = source.compute(new DensityFunction.SinglePointContext(x, y, z));
        }
        return values;
    }

    private static NoiseFixture noiseFixture() {
        NormalNoise.NoiseParameters parameters = new NormalNoise.NoiseParameters(
                -3, 1.0D, 0.5D, 0.25D);
        return new NoiseFixture(Holder.direct(parameters),
                NormalNoise.create(RandomSource.create(0x6f4a_9d21L), parameters));
    }

    private static void verifyUnknownPassDisablesCompiler() {
        String property = ColumnOptimizer.DISABLED_PASSES_PROPERTY;
        String previous = System.getProperty(property);
        System.setProperty(property, "not-a-real-pass");
        try {
            ColumnTemplate disabled = DensityColumnCompiler.compile(DensityFunctions.constant(1.0D));
            require(!disabled.available() && disabled.disabledReason().contains("not-a-real-pass"),
                    "unknown optimizer pass did not disable the column root");
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record NoiseFixture(
            Holder<NormalNoise.NoiseParameters> parameters,
            NormalNoise noise
    ) {
    }
}
