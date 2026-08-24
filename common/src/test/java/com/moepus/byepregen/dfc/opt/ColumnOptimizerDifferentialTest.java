package com.moepus.byepregen.dfc.opt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.*;
import com.moepus.byepregen.dfc.SplineTestFixtures;
import com.moepus.byepregen.dfc.codegen.ColumnClassBuilder;
import com.moepus.byepregen.dfc.codegen.ColumnClassDefiner;
import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import com.moepus.byepregen.dfc.runtime.ColumnTemplate;
import com.moepus.byepregen.dfc.runtime.CompiledColumnEvaluator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.junit.jupiter.api.Test;

final class ColumnOptimizerDifferentialTest {
    private static final double DOUBLE_TOLERANCE = 1.0E-12D;
    private static final double SPLINE_TOLERANCE = 1.0E-6D;

    @Test
    void finiteCoreRewritesRemainAlgebraicallyEquivalent() {
        CoordinateNode y = new CoordinateNode(Axis.Y);
        AstNode original = new AddNode(
                new ConstantNode(3.0D),
                new MulNode(new ConstantNode(2.0D),
                        new AddNode(new ConstantNode(-3.0D), new MulNode(y, y))));
        AstNode optimized = ColumnOptimizer.optimize(original).root();
        for (int value = -100; value <= 100; ++value) {
            double expected = evaluate(original, value * 0.125D);
            double actual = evaluate(optimized, value * 0.125D);
            double tolerance = DOUBLE_TOLERANCE * (1.0D + Math.abs(expected));
            assertEquals(expected, actual, tolerance);
        }
    }

    @Test
    void splineAbsorptionUsesFloatSemanticsWithinTolerance() {
        CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline =
                CubicSpline.constant(2.25F);
        SplineNode source = new SplineNode(spline, List.of(), List.of());
        AstNode optimized = SplineArithmeticPass.apply(
                new AddNode(new ConstantNode(0.5D), source));
        SplineNode result = assertInstanceOf(SplineNode.class, optimized);
        assertEquals(2.75D, result.spline().apply(null), SPLINE_TOLERANCE);

        AstNode inexact = new AddNode(new ConstantNode(0.1D), source);
        assertInstanceOf(AddNode.class, SplineArithmeticPass.apply(inexact));
    }

    @Test
    void splineLocationAffineReversesNegativeScale() {
        DensityFunctions.Spline.Coordinate coordinate = coordinate();
        CoordinateNode y = new CoordinateNode(Axis.Y);
        AstNode affine = new AddNode(new ConstantNode(2.0D),
                new MulNode(new ConstantNode(-2.0D), y));
        SplineNode original = splineNode(coordinate, affine);

        SplineNode result = assertInstanceOf(SplineNode.class,
                SplineArithmeticPass.apply(original));
        assertSame(y, result.coordinateNode(coordinate));
        CubicSpline.Multipoint<DensityFunctions.Spline.Point,
                DensityFunctions.Spline.Coordinate> points = multipoint(result.spline());
        assertFloatArrayEquals(new float[]{-1.0F, 1.0F, 2.0F}, points.locations());
        assertFloatArrayEquals(new float[]{-6.0F, -4.0F, -2.0F}, points.derivatives());
        assertEquals(30.0F, points.values().get(0).apply(null));
        assertEquals(20.0F, points.values().get(1).apply(null));
        assertEquals(10.0F, points.values().get(2).apply(null));
    }

    @Test
    void splineLocationAffineRemainsEquivalentWithinFloatTolerance() throws Throwable {
        DensityFunctions.Spline.Coordinate coordinate = coordinate();
        CoordinateNode y = new CoordinateNode(Axis.Y);
        SplineNode original = splineNode(coordinate,
                new AddNode(new ConstantNode(2.0D),
                        new MulNode(new ConstantNode(-2.0D), y)));
        AstNode optimized = SplineArithmeticPass.apply(original);
        ColumnRange range = new ColumnRange(-8, 1, 17);
        double[] expected = evaluateColumn(original, range);
        double[] actual = evaluateColumn(optimized, range);
        for (int i = 0; i < expected.length; ++i) {
            double tolerance = SPLINE_TOLERANCE * (1.0D + Math.abs(expected[i]));
            assertEquals(expected[i], actual[i], tolerance);
        }
    }

    @Test
    void cycleGuardReportsLastPassAndAst() {
        Map<String, OptimizationPass> passes = new LinkedHashMap<>();
        passes.put("toggle", root -> root instanceof ConstantNode value && value.value() == 0.0D
                ? new ConstantNode(1.0D) : new ConstantNode(0.0D));
        List<String> executed = new ArrayList<>();
        ColumnOptimizer.OptimizationCycleException failure = assertThrows(
                ColumnOptimizer.OptimizationCycleException.class,
                () -> ColumnOptimizer.fixedPoint(new ConstantNode(0.0D), Set.of(),
                        executed, "test", passes, 4));
        assertTrue(failure.getMessage().contains("toggle"));
        assertTrue(failure.astDump().contains("ConstantNode"));
        assertEquals(4, executed.size());
    }

    private static double evaluate(AstNode node, double y) {
        if (node instanceof ConstantNode constant) return constant.value();
        if (node instanceof CoordinateNode coordinate) {
            return coordinate.axis() == Axis.Y ? y : 0.0D;
        }
        if (node instanceof AddNode add) return evaluate(add.left(), y) + evaluate(add.right(), y);
        if (node instanceof MulNode mul) return evaluate(mul.left(), y) * evaluate(mul.right(), y);
        if (node instanceof SquareNode square) {
            double value = evaluate(square.operand(), y);
            return value * value;
        }
        if (node instanceof NegNode neg) return -evaluate(neg.operand(), y);
        throw new AssertionError("Unsupported differential node " + node.getClass().getName());
    }

    private static DensityFunctions.Spline.Coordinate coordinate() {
        return SplineTestFixtures.coordinate();
    }

    private static SplineNode splineNode(
            DensityFunctions.Spline.Coordinate coordinate,
            AstNode coordinateNode
    ) {
        CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline =
                new CubicSpline.Multipoint<>(coordinate, new float[]{-2.0F, 0.0F, 4.0F},
                        List.of(CubicSpline.constant(10.0F), CubicSpline.constant(20.0F),
                                CubicSpline.constant(30.0F)),
                        new float[]{1.0F, 2.0F, 3.0F}, 10.0F, 30.0F);
        return new SplineNode(spline, List.of(coordinate), List.of(coordinateNode));
    }

    @SuppressWarnings("unchecked")
    private static CubicSpline.Multipoint<DensityFunctions.Spline.Point,
            DensityFunctions.Spline.Coordinate> multipoint(
            CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline
    ) {
        return (CubicSpline.Multipoint<DensityFunctions.Spline.Point,
                DensityFunctions.Spline.Coordinate>) spline;
    }

    private static void assertFloatArrayEquals(float[] expected, float[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; ++i) assertEquals(expected[i], actual[i]);
    }

    private static double[] evaluateColumn(
            AstNode root,
            ColumnRange range
    ) throws Throwable {
        ColumnClassBuilder.BuildResult generated = new ColumnClassBuilder(0).build(root);
        Object[] bindings = generated.bindings().stream().map(ColumnTemplate.Binding::value).toArray();
        CompiledColumnEvaluator evaluator = (CompiledColumnEvaluator) ColumnClassDefiner
                .defineConstructor(generated.classBytes()).invoke((Object) bindings);
        double[] output = new double[range.length()];
        ColumnEvaluationContext context = new ColumnEvaluationContext();
        context.prepare(output, 0, 0, range.minY(), range.cellHeight(),
                source -> new double[range.length()]);
        try {
            evaluator.evalColumn(context);
        } finally {
            context.clear();
        }
        return output;
    }

    private record ColumnRange(int minY, int cellHeight, int length) {
    }
}
