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
import com.moepus.byepregen.dfc.runtime.ColumnTestFrames;
import com.moepus.byepregen.dfc.runtime.CompiledColumnEvaluator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.densityfunction.op.SplineFunction;
import org.junit.jupiter.api.Test;

final class ColumnOptimizerDifferentialTest {
    private static final double DOUBLE_TOLERANCE = 1.0E-12D;
    private static final double SPLINE_TOLERANCE = 1.0E-6D;

    @Test
    void finiteCoreRewritesRemainAlgebraicallyEquivalent() {
        CoordinateNode y = new CoordinateNode(Axis.Y);
        AstNode original = new AddNode(
                new ConstantNode(3.0F),
                new MulNode(new ConstantNode(2.0F),
                        new AddNode(new ConstantNode(-3.0F), new MulNode(y, y))));
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
        CubicSpline<SplineFunction.Coordinate> spline =
                CubicSpline.constant(2.25F);
        SplineNode source = new SplineNode(spline, List.of(), List.of());
        AstNode optimized = SplineArithmeticPass.apply(
                new AddNode(new ConstantNode(0.5F), source));
        SplineNode result = assertInstanceOf(SplineNode.class, optimized);
        assertEquals(2.75D, CubicSpline.sample(result.spline(), null), SPLINE_TOLERANCE);

        // 26.3: spline values are floats now, so every finite offset is exactly absorbable and the
        // remaining non-absorbable constants are the non-finite ones.
        assertInstanceOf(SplineNode.class,
                SplineArithmeticPass.apply(new AddNode(new ConstantNode(0.1F), source)));
        AstNode nonFinite = new AddNode(new ConstantNode(Float.NaN), source);
        assertInstanceOf(AddNode.class, SplineArithmeticPass.apply(nonFinite));
    }

    @Test
    void splineLocationAffineReversesNegativeScale() {
        SplineFunction.Coordinate coordinate = coordinate();
        CoordinateNode y = new CoordinateNode(Axis.Y);
        AstNode affine = new AddNode(new ConstantNode(2.0F),
                new MulNode(new ConstantNode(-2.0F), y));
        SplineNode original = splineNode(coordinate, affine);

        SplineNode result = assertInstanceOf(SplineNode.class,
                SplineArithmeticPass.apply(original));
        assertSame(y, result.coordinateNode(coordinate));
        CubicSpline.Multipoint<SplineFunction.Coordinate> points = multipoint(result.spline());
        assertFloatArrayEquals(new float[]{-1.0F, 1.0F, 2.0F}, points.locations());
        assertFloatArrayEquals(new float[]{-6.0F, -4.0F, -2.0F}, points.derivatives());
        assertEquals(30.0F, CubicSpline.sample(points.values().get(0), null));
        assertEquals(20.0F, CubicSpline.sample(points.values().get(1), null));
        assertEquals(10.0F, CubicSpline.sample(points.values().get(2), null));
    }

    @Test
    void splineLocationAffineRemainsEquivalentWithinFloatTolerance() throws Throwable {
        SplineFunction.Coordinate coordinate = coordinate();
        CoordinateNode y = new CoordinateNode(Axis.Y);
        SplineNode original = splineNode(coordinate,
                new AddNode(new ConstantNode(2.0F),
                        new MulNode(new ConstantNode(-2.0F), y)));
        AstNode optimized = SplineArithmeticPass.apply(original);
        ColumnRange range = new ColumnRange(-8, 1, 17);
        float[] expected = evaluateColumn(original, range);
        float[] actual = evaluateColumn(optimized, range);
        for (int i = 0; i < expected.length; ++i) {
            double tolerance = SPLINE_TOLERANCE * (1.0D + Math.abs(expected[i]));
            assertEquals(expected[i], actual[i], tolerance);
        }
    }

    @Test
    void cycleGuardReportsLastPassAndAst() {
        Map<String, OptimizationPass> passes = new LinkedHashMap<>();
        passes.put("toggle", root -> root instanceof ConstantNode value && value.value() == 0.0F
                ? new ConstantNode(1.0F) : new ConstantNode(0.0F));
        List<String> executed = new ArrayList<>();
        ColumnOptimizer.OptimizationCycleException failure = assertThrows(
                ColumnOptimizer.OptimizationCycleException.class,
                () -> ColumnOptimizer.fixedPoint(new ConstantNode(0.0F), Set.of(),
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

    private static SplineFunction.Coordinate coordinate() {
        return SplineTestFixtures.coordinate();
    }

    private static SplineNode splineNode(
            SplineFunction.Coordinate coordinate,
            AstNode coordinateNode
    ) {
        // 26.3: CubicSpline.Multipoint derives its range and dropped the explicit min/max arguments.
        CubicSpline<SplineFunction.Coordinate> spline =
                new CubicSpline.Multipoint<>(coordinate, new float[]{-2.0F, 0.0F, 4.0F},
                        List.of(CubicSpline.constant(10.0F), CubicSpline.constant(20.0F),
                                CubicSpline.constant(30.0F)),
                        new float[]{1.0F, 2.0F, 3.0F});
        return new SplineNode(spline, List.of(coordinate), List.of(coordinateNode));
    }

    @SuppressWarnings("unchecked")
    private static CubicSpline.Multipoint<SplineFunction.Coordinate> multipoint(
            CubicSpline<SplineFunction.Coordinate> spline
    ) {
        return (CubicSpline.Multipoint<SplineFunction.Coordinate>) spline;
    }

    private static void assertFloatArrayEquals(float[] expected, float[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; ++i) assertEquals(expected[i], actual[i]);
    }

    private static float[] evaluateColumn(
            AstNode root,
            ColumnRange range
    ) throws Throwable {
        ColumnClassBuilder.BuildResult generated = new ColumnClassBuilder(0).build(root);
        Object[] bindings = generated.bindings().stream().map(ColumnTemplate.Binding::value).toArray();
        CompiledColumnEvaluator evaluator = (CompiledColumnEvaluator) ColumnClassDefiner
                .defineConstructor(generated.classBytes()).invoke((Object) bindings);
        // 26.3: column outputs and the evaluation context are float based, and the frame has to be
        // obtained through the request-owned workspace rather than constructed directly.
        float[] output = new float[range.length()];
        ColumnEvaluationContext context = ColumnTestFrames.prepared(
                output, 0, 0, range.minY(), range.cellHeight());
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
