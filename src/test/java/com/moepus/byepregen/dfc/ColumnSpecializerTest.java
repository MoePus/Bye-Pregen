package com.moepus.byepregen.dfc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moepus.byepregen.dfc.analysis.ColumnSpecializer;
import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.AddNode;
import com.moepus.byepregen.dfc.ast.AstNodes.Axis;
import com.moepus.byepregen.dfc.ast.AstNodes.ConstantNode;
import com.moepus.byepregen.dfc.ast.AstNodes.CoordinateNode;
import com.moepus.byepregen.dfc.ast.AstNodes.Memoized2DNode;
import com.moepus.byepregen.dfc.ast.AstNodes.RangeChoiceNode;
import com.moepus.byepregen.dfc.ast.AstNodes.RootNode;
import org.junit.jupiter.api.Test;

final class ColumnSpecializerTest {
    @Test
    void structurallyEqualYIndependentSubtreesShareOneMemoSlot() {
        CoordinateNode x = new CoordinateNode(Axis.X);
        AstNode first = new AddNode(new ConstantNode(2.0D), x);
        AstNode second = new AddNode(new ConstantNode(2.0D), new CoordinateNode(Axis.X));
        ColumnSpecializer.Result result = ColumnSpecializer.specialize(
                new RootNode(new AddNode(first, second)));

        RootNode root = assertInstanceOf(RootNode.class, result.root());
        Memoized2DNode rootSlot = assertInstanceOf(Memoized2DNode.class, root.next());
        AddNode sum = assertInstanceOf(AddNode.class, rootSlot.delegate());
        assertSame(sum.left(), sum.right());
        assertInstanceOf(AddNode.class, sum.left());
        assertEquals(1, result.memoizedSlots());
        assertTrue(result.yIndependent());
    }

    @Test
    void YIndependentRangeBranchGetsLazyColumnSlot() {
        AstNode branch = new AddNode(new ConstantNode(1.0D), new CoordinateNode(Axis.X));
        RangeChoiceNode range = new RangeChoiceNode(
                new CoordinateNode(Axis.Y), 0.0D, 1.0D, branch, new ConstantNode(0.0D));

        AstNode result = ColumnSpecializer.specialize(new RootNode(range)).root();
        RangeChoiceNode specialized = assertInstanceOf(
                RangeChoiceNode.class, assertInstanceOf(RootNode.class, result).next());
        Memoized2DNode lazy = assertInstanceOf(Memoized2DNode.class, specialized.whenInRange());
        assertInstanceOf(AddNode.class, lazy.delegate());
        assertTrue(lazy.slot() >= 0);
        assertEquals(1, ColumnSpecializer.specialize(new RootNode(range)).memoizedSlots());
    }

    @Test
    void conditionalAndUnconditionalReferencesShareLazyWrapper() {
        AstNode shared = new AddNode(new ConstantNode(3.0D), new CoordinateNode(Axis.X));
        RangeChoiceNode choice = new RangeChoiceNode(new CoordinateNode(Axis.Y),
                0.0D, 1.0D, shared, new ConstantNode(0.0D));
        ColumnSpecializer.Result result = ColumnSpecializer.specialize(
                new RootNode(new AddNode(shared, choice)));

        AddNode root = assertInstanceOf(AddNode.class,
                assertInstanceOf(RootNode.class, result.root()).next());
        Memoized2DNode direct = assertInstanceOf(Memoized2DNode.class, root.left());
        RangeChoiceNode specializedChoice = assertInstanceOf(RangeChoiceNode.class, root.right());
        assertSame(direct, specializedChoice.whenInRange());
        assertEquals(1, result.memoizedSlots());
        assertFalse(result.yIndependent());
    }

    @Test
    void reportsYDependencyWithoutChangingSpecialization() {
        ColumnSpecializer.Result x = ColumnSpecializer.specialize(
                new RootNode(new CoordinateNode(Axis.X)));
        ColumnSpecializer.Result y = ColumnSpecializer.specialize(
                new RootNode(new CoordinateNode(Axis.Y)));

        assertTrue(x.yIndependent());
        assertFalse(y.yIndependent());
    }

}
