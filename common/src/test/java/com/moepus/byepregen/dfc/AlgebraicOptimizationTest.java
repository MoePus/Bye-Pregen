package com.moepus.byepregen.dfc;

import com.moepus.byepregen.dfc.ast.AstNodes.*;
import com.moepus.byepregen.dfc.opt.ColumnOptimizer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class AlgebraicOptimizationTest {
    private static final CoordinateNode X = new CoordinateNode(Axis.X);
    @Test void combinesConstantsAndCommonFactors() {
        var root = new AddNode(new MulNode(X, new ConstantNode(2)), new MulNode(X, new ConstantNode(3)));
        assertEquals(new MulNode(new ConstantNode(5), X), ColumnOptimizer.optimize(root).root());
    }
    @Test void cancelsOffsetsAlgebraicallyWithoutPreservingOriginalRounding() {
        var root = new AddNode(new AddNode(X, new ConstantNode(16777216)), new ConstantNode(-16777216));
        assertEquals(X, ColumnOptimizer.optimize(root).root());
    }
    @Test void foldsConstantsAndNeutralFactors() {
        assertEquals(new ConstantNode(5), ColumnOptimizer.optimize(new AddNode(new ConstantNode(2), new ConstantNode(3))).root());
        assertEquals(X, ColumnOptimizer.optimize(new MulNode(X, new ConstantNode(1))).root());
        assertEquals(new ConstantNode(16), ColumnOptimizer.optimize(new SquareNode(new ConstantNode(4))).root());
    }
    @Test void pipelineIsIdempotentAndSupportsDivisionStrengthReduction() {
        var root = new DivNode(X, new ConstantNode(4));
        var once = ColumnOptimizer.optimize(root).root();
        assertEquals(new MulNode(new ConstantNode(0.25F), X), once);
        assertEquals(once, ColumnOptimizer.optimize(once).root());
    }
}
