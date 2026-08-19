package com.moepus.byepregen.dfc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.AddNode;
import com.moepus.byepregen.dfc.ast.AstNodes.ConstantNode;
import com.moepus.byepregen.dfc.ast.AstNodes.CoordinateNode;
import com.moepus.byepregen.dfc.ast.AstNodes.MinShortNode;
import com.moepus.byepregen.dfc.ast.AstNodes.SqueezeNode;
import com.moepus.byepregen.dfc.opt.ColumnOptimizer;
import org.junit.jupiter.api.Test;

final class ColumnOptimizerTest {
    @Test
    void canonicalizesConstantsToTheLeft() {
        AstNode root = new AddNode(new CoordinateNode(com.moepus.byepregen.dfc.ast.AstNodes.Axis.Y),
                new ConstantNode(2.0D));
        AddNode result = assertInstanceOf(AddNode.class, ColumnOptimizer.optimize(root).root());
        assertEquals(2.0D, assertInstanceOf(ConstantNode.class, result.left()).value());
    }

    @Test
    void doesNotCollapseNestedSqueeze() {
        SqueezeNode inner = new SqueezeNode(
                new CoordinateNode(com.moepus.byepregen.dfc.ast.AstNodes.Axis.Y));
        SqueezeNode result = assertInstanceOf(SqueezeNode.class,
                ColumnOptimizer.optimize(new SqueezeNode(inner)).root());
        assertInstanceOf(SqueezeNode.class, result.operand());
    }

    @Test
    void splineStageRunsOnlyOnce() {
        ColumnOptimizer.Result result = ColumnOptimizer.optimize(new ConstantNode(1.0D));
        assertEquals(1, result.executedPasses().stream()
                .filter("spline-arithmetic"::equals).count());
    }

    @Test
    void unknownDisabledPassIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ColumnOptimizer.disabledPasses("not-a-pass"));
    }

    @Test
    void disabledPassIsSkippedByDefaultSchedule() {
        String property = ColumnOptimizer.DISABLED_PASSES_PROPERTY;
        String previous = System.getProperty(property);
        System.setProperty(property, "constant-fold");
        try {
            ColumnOptimizer.Result result = ColumnOptimizer.optimize(
                    new AddNode(new ConstantNode(2.0D), new ConstantNode(3.0D)));
            assertInstanceOf(AddNode.class, result.root());
            assertEquals(0, result.executedPasses().stream()
                    .filter("constant-fold"::equals).count());
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    @Test
    void shortMinKeepsOperandDirection() {
        CoordinateNode y = new CoordinateNode(com.moepus.byepregen.dfc.ast.AstNodes.Axis.Y);
        MinShortNode original = new MinShortNode(y, new ConstantNode(2.0D), 2.0D);
        MinShortNode result = assertInstanceOf(MinShortNode.class,
                ColumnOptimizer.optimize(original).root());
        assertEquals(y, result.left());
        assertInstanceOf(ConstantNode.class, result.right());
    }
}
