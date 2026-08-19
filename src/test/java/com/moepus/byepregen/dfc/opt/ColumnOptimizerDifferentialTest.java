package com.moepus.byepregen.dfc.opt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.*;
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
}
