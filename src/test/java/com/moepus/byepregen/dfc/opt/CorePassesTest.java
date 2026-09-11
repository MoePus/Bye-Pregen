package com.moepus.byepregen.dfc.opt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.*;
import com.moepus.byepregen.dfc.runtime.ColumnMath;
import org.junit.jupiter.api.Test;

final class CorePassesTest {
    private static final CoordinateNode Y = new CoordinateNode(Axis.Y);

    @Test
    void canonicalizeMovesSwappableConstantLeft() {
        AddNode result = assertInstanceOf(AddNode.class,
                CorePasses.canonicalize(new AddNode(Y, new ConstantNode(2.0D))));
        assertInstanceOf(ConstantNode.class, result.left());
    }

    @Test
    void constantFoldEvaluatesConstantSubtree() {
        ConstantNode result = assertInstanceOf(ConstantNode.class,
                CorePasses.constantFold(new MulNode(new ConstantNode(2.0D), new ConstantNode(3.0D))));
        assertEquals(6.0D, result.value());
    }

    @Test
    void constantFoldEvaluatesSqueezeThroughColumnMath() {
        // The squeeze formula lives in ColumnMath because the generated emitters call it there;
        // the optimizer must fold constants through that same implementation, not a private copy.
        for (double value : new double[] {-4.0D, -1.0D, -0.25D, 0.0D, 0.25D, 1.0D, 4.0D}) {
            ConstantNode result = assertInstanceOf(ConstantNode.class,
                    CorePasses.constantFold(new SqueezeNode(new ConstantNode(value))));
            assertEquals(ColumnMath.squeeze(value), result.value(),
                    "squeeze fold must match ColumnMath.squeeze for input " + value);
        }
    }

    @Test
    void constantFoldSqueezeMatchesClampAndCubeFormula() {
        double clamped = 0.25D;
        assertEquals(clamped * 0.5D - clamped * clamped * clamped / 24.0D,
                assertInstanceOf(ConstantNode.class,
                        CorePasses.constantFold(new SqueezeNode(new ConstantNode(clamped)))).value());
    }

    @Test
    void strengthReduceRecognizesSquare() {
        SquareNode result = assertInstanceOf(SquareNode.class,
                CorePasses.strengthReduce(new MulNode(Y, Y)));
        assertSame(Y, result.operand());
    }

    @Test
    void strengthReduceRecognizesCube() {
        CubeNode result = assertInstanceOf(CubeNode.class,
                CorePasses.strengthReduce(new MulNode(new SquareNode(Y), Y)));
        assertSame(Y, result.operand());
    }

    @Test
    void algebraicSimplifyRemovesNeutralAdd() {
        assertSame(Y, CorePasses.algebraicSimplify(
                new AddNode(new ConstantNode(0.0D), Y)));
    }

    @Test
    void identityEliminateCollapsesEqualMin() {
        assertSame(Y, CorePasses.identityEliminate(new MinNode(Y, Y)));
    }

    @Test
    void identityEliminateHandlesMaxOnEitherMinSide() {
        AstNode leftNested = CorePasses.identityEliminate(
                new MinNode(new MaxNode(new ConstantNode(1.0D), Y), Y));
        assertSame(Y, leftNested);

        AstNode rightNested = CorePasses.identityEliminate(
                new MaxNode(new MinNode(new ConstantNode(1.0D), Y), Y));
        assertSame(Y, rightNested);
    }

    @Test
    void rangePruneSelectsKnownBranch() {
        ConstantNode inside = new ConstantNode(4.0D);
        AstNode result = CorePasses.rangePrune(new RangeChoiceNode(
                new ConstantNode(0.5D), 0.0D, 1.0D,
                inside, new ConstantNode(8.0D)));
        assertSame(inside, result);
    }

    @Test
    void rangePruneUsesKnownOuterIntervalForNestedChoice() {
        RangeChoiceNode nested = new RangeChoiceNode(Y, 2.0D, 3.0D,
                new ConstantNode(7.0D), new ConstantNode(8.0D));
        RangeChoiceNode outer = new RangeChoiceNode(Y, 0.0D, 1.0D,
                nested, new ConstantNode(9.0D));
        RangeChoiceNode result = assertInstanceOf(RangeChoiceNode.class,
                CorePasses.rangePrune(outer));
        assertEquals(8.0D, assertInstanceOf(ConstantNode.class, result.whenInRange()).value());
    }

    @Test
    void rangePruneRemovesConstantMinShortCondition() {
        MinNode eager = assertInstanceOf(MinNode.class, CorePasses.rangePrune(
                new MinShortNode(new ConstantNode(0.0D), Y, -4.9294D)));
        assertEquals(0.0D, assertInstanceOf(ConstantNode.class, eager.left()).value());

        ConstantNode shorted = assertInstanceOf(ConstantNode.class, CorePasses.rangePrune(
                new MinShortNode(new ConstantNode(-5.0D), Y, -4.9294D)));
        assertEquals(-5.0D, shorted.value());
    }

    @Test
    void rangePruneRemovesConstantMaxShortCondition() {
        MaxNode eager = assertInstanceOf(MaxNode.class, CorePasses.rangePrune(
                new MaxShortNode(new ConstantNode(0.0D), Y, 4.9294D)));
        assertEquals(0.0D, assertInstanceOf(ConstantNode.class, eager.left()).value());

        ConstantNode shorted = assertInstanceOf(ConstantNode.class, CorePasses.rangePrune(
                new MaxShortNode(new ConstantNode(5.0D), Y, 4.9294D)));
        assertEquals(5.0D, shorted.value());
    }
}
