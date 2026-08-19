/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.ast;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.ToFloatFunction;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

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

    public enum CacheKind {
        CACHE_2D,
        CACHE_ONCE,
        CACHE_ALL_IN_CELL,
        FLAT_CACHE,
        INTERPOLATED,
        UNKNOWN
    }

    public enum SourceMode {
        FLAT,
        INTERPOLATED
    }

    public record RootNode(AstNode next) implements UnaryNode {
        public RootNode {
            Objects.requireNonNull(next, "next");
        }

        @Override public AstNode operand() { return this.next; }
        @Override public UnaryNode withOperand(AstNode operand) { return new RootNode(operand); }
    }

    public record ConstantNode(double value) implements LeafNode {
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

    public record NegMulNode(AstNode operand, double multiplier) implements UnaryNode {
        public NegMulNode { Objects.requireNonNull(operand, "operand"); }
        @Override public UnaryNode withOperand(AstNode value) { return new NegMulNode(value, this.multiplier); }
    }

    public record AddNode(AstNode left, AstNode right) implements BinaryNode {
        public AddNode { requireNodes(left, right); }
        @Override public BinaryNode withOperands(AstNode a, AstNode b) { return new AddNode(a, b); }
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
    }

    public record MaxNode(AstNode left, AstNode right) implements BinaryNode {
        public MaxNode { requireNodes(left, right); }
        @Override public BinaryNode withOperands(AstNode a, AstNode b) { return new MaxNode(a, b); }
    }

    public record MinShortNode(AstNode left, AstNode right, double rightMin) implements BinaryNode {
        public MinShortNode { requireNodes(left, right); }
        @Override public BinaryNode withOperands(AstNode a, AstNode b) { return new MinShortNode(a, b, this.rightMin); }
        @Override public boolean canSwapOperands() { return false; }
    }

    public record MaxShortNode(AstNode left, AstNode right, double rightMax) implements BinaryNode {
        public MaxShortNode { requireNodes(left, right); }
        @Override public BinaryNode withOperands(AstNode a, AstNode b) { return new MaxShortNode(a, b, this.rightMax); }
        @Override public boolean canSwapOperands() { return false; }
    }

    public record YClampedGradientNode(
            int fromY, int toY, double fromValue, double toValue
    ) implements LeafNode {
    }

    public record RangeChoiceNode(
            AstNode input,
            double minInclusive,
            double maxExclusive,
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

    public record CacheNode(DensityFunction source, CacheKind kind, AstNode delegate) implements UnaryNode {
        public CacheNode {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(delegate, "delegate");
        }
        @Override public AstNode operand() { return this.delegate; }
        @Override public UnaryNode withOperand(AstNode value) { return new CacheNode(this.source, this.kind, value); }
    }

    public static final class SourceNode implements LeafNode {
        private final DensityFunction source;
        private final SourceMode mode;

        public SourceNode(DensityFunction source, SourceMode mode) {
            this.source = Objects.requireNonNull(source, "source");
            this.mode = Objects.requireNonNull(mode, "mode");
        }

        public DensityFunction source() { return this.source; }
        public SourceMode mode() { return this.mode; }
    }

    public static final class DelegateNode implements LeafNode {
        private final DensityFunction delegate;
        private final boolean yIndependent;

        public DelegateNode(DensityFunction delegate, boolean yIndependent) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.yIndependent = yIndependent;
        }

        public DensityFunction delegate() { return this.delegate; }
        public boolean yIndependent() { return this.yIndependent; }
    }

    public record NoiseNode(
            AstNode inputX,
            AstNode inputY,
            AstNode inputZ,
            DensityFunction.NoiseHolder noise
    ) implements AstNode {
        public NoiseNode {
            requireNodes(inputX, inputY);
            Objects.requireNonNull(inputZ, "inputZ");
            Objects.requireNonNull(noise, "noise");
        }
        @Override public AstNode[] children() { return new AstNode[]{this.inputX, this.inputY, this.inputZ}; }
        @Override public AstNode withChildren(AstNode[] children) {
            requireCount(children, 3);
            if (children[0] == this.inputX && children[1] == this.inputY && children[2] == this.inputZ) return this;
            return new NoiseNode(children[0], children[1], children[2], this.noise);
        }
    }

    public record WeirdScaledNode(
            AstNode input,
            DensityFunction.NoiseHolder noise,
            DensityFunctions.WeirdScaledSampler.RarityValueMapper mapper
    ) implements UnaryNode {
        public WeirdScaledNode {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(noise, "noise");
            Objects.requireNonNull(mapper, "mapper");
        }
        @Override public AstNode operand() { return this.input; }
        @Override public UnaryNode withOperand(AstNode value) { return new WeirdScaledNode(value, this.noise, this.mapper); }
    }

    public static final class SplineNode implements AstNode {
        private final CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline;
        private final List<DensityFunctions.Spline.Coordinate> coordinates;
        private final List<AstNode> coordinateNodes;

        public SplineNode(
                CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline,
                List<DensityFunctions.Spline.Coordinate> coordinates,
                List<AstNode> coordinateNodes
        ) {
            this.spline = Objects.requireNonNull(spline, "spline");
            this.coordinates = List.copyOf(coordinates);
            this.coordinateNodes = List.copyOf(coordinateNodes);
            if (this.coordinates.size() != this.coordinateNodes.size()) {
                throw new IllegalArgumentException("Spline coordinate lists differ in size");
            }
        }

        public CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline() { return this.spline; }

        public AstNode coordinateNode(DensityFunctions.Spline.Coordinate coordinate) {
            for (int i = 0; i < this.coordinates.size(); ++i) {
                if (this.coordinates.get(i) == coordinate) return this.coordinateNodes.get(i);
            }
            throw new IllegalArgumentException("Unknown spline coordinate");
        }

        public List<DensityFunctions.Spline.Coordinate> coordinates() { return this.coordinates; }

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

    public static <P, C extends ToFloatFunction<P>> void collectSplineCoordinates(
            CubicSpline<P, C> spline,
            Map<C, Boolean> coordinates
    ) {
        if (!(spline instanceof CubicSpline.Multipoint<P, C> multipoint)) return;
        coordinates.put(multipoint.coordinate(), Boolean.TRUE);
        for (CubicSpline<P, C> child : multipoint.values()) collectSplineCoordinates(child, coordinates);
    }

    public static <K> Map<K, Boolean> identityMap() {
        return new IdentityHashMap<>();
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
