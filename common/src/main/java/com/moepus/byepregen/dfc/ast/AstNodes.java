/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.util.BoundedFloatFunction;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.op.SplineFunction;

public final class AstNodes {
    private AstNodes() {
    }

    public interface LeafNode extends AstNode {
        @Override
        default AstNode[] children() {
            return new AstNode[0];
        }

        @Override
        default AstNode withChildren(AstNode[] children) {
            requireCount(children, 0);
            return this;
        }
    }

    public interface UnaryNode extends AstNode {
        AstNode operand();

        UnaryNode withOperand(AstNode operand);

        @Override
        default AstNode[] children() {
            return new AstNode[]{this.operand()};
        }

        @Override
        default AstNode withChildren(AstNode[] children) {
            requireCount(children, 1);
            return children[0] == this.operand() ? this : this.withOperand(children[0]);
        }
    }

    public interface BinaryNode extends AstNode {
        AstNode left();

        AstNode right();

        BinaryNode withOperands(AstNode left, AstNode right);

        default boolean canSwapOperands() {
            return true;
        }

        @Override
        default AstNode[] children() {
            return new AstNode[]{this.left(), this.right()};
        }

        @Override
        default AstNode withChildren(AstNode[] children) {
            requireCount(children, 2);
            return children[0] == this.left() && children[1] == this.right()
                    ? this
                    : this.withOperands(children[0], children[1]);
        }
    }

    public enum Axis {
        X, Y, Z
    }

    public enum CacheKind { CACHE_2D, CACHE_ONCE }

    /**
     * Folds a binary node whose two operands are compile-time constants. Shared by the optimizer
     * pass and the column emitter so both agree on every foldable node type.
     */
    public static float foldBinary(BinaryNode node, float left, float right) {
        if (node instanceof AddNode) return left + right;
        if (node instanceof SubNode) return left - right;
        if (node instanceof MulNode) return left * right;
        if (node instanceof DivNode) return left / right;
        if (node instanceof MinNode || node instanceof MinShortNode) return Math.min(left, right);
        if (node instanceof MaxNode || node instanceof MaxShortNode) return Math.max(left, right);
        throw new IllegalArgumentException("Unsupported binary node " + node.getClass().getName());
    }

    public record RootNode(AstNode next) implements UnaryNode {
        public RootNode {
            Objects.requireNonNull(next, "next");
        }

        @Override public AstNode operand() { return this.next; }
        @Override public UnaryNode withOperand(AstNode operand) { return new RootNode(operand); }
    }

    public record ConstantNode(float value) implements LeafNode {
    }

    public record CoordinateNode(Axis axis) implements LeafNode {
        public CoordinateNode {
            Objects.requireNonNull(axis, "axis");
        }
    }

    public record AbsNode(AstNode operand) implements UnaryNode {
        public AbsNode { Objects.requireNonNull(operand, "operand"); }
        @Override public UnaryNode withOperand(AstNode value) { return new AbsNode(value); }
    }

    public record NegNode(AstNode operand) implements UnaryNode {
        public NegNode { Objects.requireNonNull(operand, "operand"); }
        @Override public UnaryNode withOperand(AstNode value) { return new NegNode(value); }
    }

    public record SquareNode(AstNode operand) implements UnaryNode {
        public SquareNode { Objects.requireNonNull(operand, "operand"); }
        @Override public UnaryNode withOperand(AstNode value) { return new SquareNode(value); }
    }

    public record CubeNode(AstNode operand) implements UnaryNode {
        public CubeNode { Objects.requireNonNull(operand, "operand"); }
        @Override public UnaryNode withOperand(AstNode value) { return new CubeNode(value); }
    }

    public record SqueezeNode(AstNode operand) implements UnaryNode {
        public SqueezeNode { Objects.requireNonNull(operand, "operand"); }
        @Override public UnaryNode withOperand(AstNode value) { return new SqueezeNode(value); }
    }

    public record NegMulNode(AstNode operand, float multiplier) implements UnaryNode {
        public NegMulNode { Objects.requireNonNull(operand, "operand"); }
        @Override public UnaryNode withOperand(AstNode value) { return new NegMulNode(value, this.multiplier); }
    }

    public record AddNode(AstNode left, AstNode right) implements BinaryNode {
        public AddNode { requireNodes(left, right); }
        @Override public BinaryNode withOperands(AstNode a, AstNode b) { return new AddNode(a, b); }
    }

    public record SubNode(AstNode left, AstNode right) implements BinaryNode {
        public SubNode { requireNodes(left, right); }
        @Override public BinaryNode withOperands(AstNode a, AstNode b) { return new SubNode(a, b); }
        @Override public boolean canSwapOperands() { return false; }
    }

    public enum NativeUnaryOp { SQRT, LOG, SIGN, CLAMP }
    public record NativeUnaryNode(AstNode operand, NativeUnaryOp operation, float first, float second) implements UnaryNode {
        @Override public UnaryNode withOperand(AstNode value) {
            return new NativeUnaryNode(value, this.operation, this.first, this.second);
        }
    }

    public record LerpNode(AstNode alpha, AstNode first, AstNode second) implements AstNode {
        @Override public AstNode[] children() { return new AstNode[]{this.alpha, this.first, this.second}; }
        @Override public AstNode withChildren(AstNode[] values) {
            requireCount(values, 3);
            return new LerpNode(values[0], values[1], values[2]);
        }
    }

    public record MulNode(AstNode left, AstNode right) implements BinaryNode {
        public MulNode { requireNodes(left, right); }
        @Override public BinaryNode withOperands(AstNode a, AstNode b) { return new MulNode(a, b); }
    }

    public record DivNode(AstNode left, AstNode right) implements BinaryNode {
        public DivNode { requireNodes(left, right); }
        @Override public BinaryNode withOperands(AstNode a, AstNode b) { return new DivNode(a, b); }
        @Override public boolean canSwapOperands() { return false; }
    }

    public record MinNode(AstNode left, AstNode right) implements BinaryNode {
        public MinNode { requireNodes(left, right); }
        @Override public BinaryNode withOperands(AstNode a, AstNode b) { return new MinNode(a, b); }
        @Override public boolean canSwapOperands() { return false; }
    }

    public record MaxNode(AstNode left, AstNode right) implements BinaryNode {
        public MaxNode { requireNodes(left, right); }
        @Override public BinaryNode withOperands(AstNode a, AstNode b) { return new MaxNode(a, b); }
        @Override public boolean canSwapOperands() { return false; }
    }

    public record MinShortNode(AstNode left, AstNode right, float rightMin) implements BinaryNode {
        public MinShortNode { requireNodes(left, right); }
        @Override public BinaryNode withOperands(AstNode a, AstNode b) { return new MinShortNode(a, b, this.rightMin); }
        @Override public boolean canSwapOperands() { return false; }
    }

    public record MaxShortNode(AstNode left, AstNode right, float rightMax) implements BinaryNode {
        public MaxShortNode { requireNodes(left, right); }
        @Override public BinaryNode withOperands(AstNode a, AstNode b) { return new MaxShortNode(a, b, this.rightMax); }
        @Override public boolean canSwapOperands() { return false; }
    }

    public record YClampedGradientNode(
            int fromY, int toY, float fromValue, float toValue
    ) implements LeafNode {
    }

    public record RangeChoiceNode(
            AstNode input,
            float minInclusive,
            float maxExclusive,
            AstNode whenInRange,
            AstNode whenOutOfRange
    ) implements AstNode {
        public RangeChoiceNode {
            Objects.requireNonNull(input, "input");
            requireNodes(whenInRange, whenOutOfRange);
        }

        @Override public AstNode[] children() {
            return new AstNode[]{this.input, this.whenInRange, this.whenOutOfRange};
        }

        @Override public AstNode withChildren(AstNode[] children) {
            requireCount(children, 3);
            if (children[0] == this.input && children[1] == this.whenInRange
                    && children[2] == this.whenOutOfRange) return this;
            return new RangeChoiceNode(children[0], this.minInclusive, this.maxExclusive,
                    children[1], children[2]);
        }
    }

    public record IntervalSelectNode(AstNode input, List<Float> thresholds, List<AstNode> branches) implements AstNode {
        public IntervalSelectNode {
            Objects.requireNonNull(input, "input");
            thresholds = List.copyOf(thresholds);
            branches = List.copyOf(branches);
            if (branches.size() != thresholds.size() + 1) {
                throw new IllegalArgumentException("Interval select needs one more branch than thresholds");
            }
        }

        @Override public AstNode[] children() {
            AstNode[] result = new AstNode[this.branches.size() + 1];
            result[0] = this.input;
            for (int i = 0; i < this.branches.size(); ++i) result[i + 1] = this.branches.get(i);
            return result;
        }

        @Override public AstNode withChildren(AstNode[] children) {
            requireCount(children, this.branches.size() + 1);
            return new IntervalSelectNode(children[0], this.thresholds,
                    List.of(children).subList(1, children.length));
        }
    }

    public record CacheNode(DensitySampler source, CacheKind kind, AstNode delegate) implements UnaryNode {
        public CacheNode {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(delegate, "delegate");
        }
        @Override public AstNode operand() { return this.delegate; }
        @Override public UnaryNode withOperand(AstNode value) { return new CacheNode(this.source, this.kind, value); }
    }

    public static final class SourceNode implements LeafNode {
        private final DensitySampler source;

        public SourceNode(DensitySampler source) {
            this.source = Objects.requireNonNull(source, "source");
        }

        public DensitySampler source() { return this.source; }
    }

    public static final class DelegateNode implements LeafNode {
        private final DensitySampler delegate;
        private final boolean yIndependent;

        public DelegateNode(DensitySampler delegate, boolean yIndependent) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.yIndependent = yIndependent;
        }

        public DensitySampler delegate() { return this.delegate; }
        public boolean yIndependent() { return this.yIndependent; }
    }

    public static final class SplineNode implements AstNode {
        private final CubicSpline<SplineFunction.Coordinate> spline;
        private final List<SplineFunction.Coordinate> coordinates;
        private final List<AstNode> coordinateNodes;

        public SplineNode(
                CubicSpline<SplineFunction.Coordinate> spline,
                List<SplineFunction.Coordinate> coordinates,
                List<AstNode> coordinateNodes
        ) {
            this.spline = Objects.requireNonNull(spline, "spline");
            this.coordinates = List.copyOf(coordinates);
            this.coordinateNodes = List.copyOf(coordinateNodes);
            if (this.coordinates.size() != this.coordinateNodes.size()) {
                throw new IllegalArgumentException("Spline coordinate lists differ in size");
            }
        }

        public CubicSpline<SplineFunction.Coordinate> spline() { return this.spline; }

        public AstNode coordinateNode(SplineFunction.Coordinate coordinate) {
            for (int i = 0; i < this.coordinates.size(); ++i) {
                if (this.coordinates.get(i) == coordinate) return this.coordinateNodes.get(i);
            }
            throw new IllegalArgumentException("Unknown spline coordinate");
        }

        public List<SplineFunction.Coordinate> coordinates() { return this.coordinates; }

        @Override public AstNode[] children() { return this.coordinateNodes.toArray(AstNode[]::new); }

        @Override public AstNode withChildren(AstNode[] children) {
            requireCount(children, this.coordinateNodes.size());
            boolean changed = false;
            for (int i = 0; i < children.length; ++i) changed |= children[i] != this.coordinateNodes.get(i);
            return changed ? new SplineNode(this.spline, this.coordinates, List.of(children)) : this;
        }

        @Override public boolean equals(Object other) {
            return other instanceof SplineNode node
                    && this.spline.equals(node.spline)
                    && this.coordinates.equals(node.coordinates)
                    && this.coordinateNodes.equals(node.coordinateNodes);
        }

        @Override public int hashCode() {
            return Objects.hash(this.spline, this.coordinates, this.coordinateNodes);
        }
    }

    public record Memoized2DNode(AstNode delegate, int slot) implements UnaryNode {
        public Memoized2DNode {
            Objects.requireNonNull(delegate, "delegate");
            if (slot < 0) throw new IllegalArgumentException("Negative memoized slot");
        }
        @Override public AstNode operand() { return this.delegate; }
        @Override public UnaryNode withOperand(AstNode value) { return new Memoized2DNode(value, this.slot); }
    }

    public static <P, C extends BoundedFloatFunction<P>> List<C> collectSplineCoordinates(
            CubicSpline<C> spline
    ) {
        List<C> coordinates = new ArrayList<>();
        Set<C> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        collectSplineCoordinates(spline, coordinates, seen);
        return List.copyOf(coordinates);
    }

    private static <P, C extends BoundedFloatFunction<P>> void collectSplineCoordinates(
            CubicSpline<C> spline, List<C> coordinates, Set<C> seen
    ) {
        if (!(spline instanceof CubicSpline.Multipoint<C> multipoint)) return;
        if (seen.add(multipoint.coordinate())) coordinates.add(multipoint.coordinate());
        for (CubicSpline<C> child : multipoint.values()) {
            collectSplineCoordinates(child, coordinates, seen);
        }
    }

    private static void requireCount(AstNode[] children, int expected) {
        if (children.length != expected) {
            throw new IllegalArgumentException("Expected " + expected + " children, got " + children.length);
        }
    }

    private static void requireNodes(AstNode first, AstNode second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
    }
}
