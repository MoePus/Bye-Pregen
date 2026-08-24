package com.moepus.byepregen.worldgen.biome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.level.biome.Climate;

/** Allocation-light equivalent of the vanilla {@link Climate.RTree} builder. */
public final class ClimateRTreeBuildOptimizer {
    private static final int CHILDREN_PER_NODE = 6;
    private static final int BOUND_COUNT = 7;

    private ClimateRTreeBuildOptimizer() {
    }

    public static <T> Climate.RTree.Node<T> build(
            int parameterCount,
            List<? extends Climate.RTree.Node<T>> children
    ) {
        return buildTyped(parameterCount, children);
    }

    public static <T> List<Climate.Parameter> buildParameterSpace(
            List<? extends Climate.RTree.Node<T>> children
    ) {
        return buildParameterSpaceTyped(children);
    }

    private static <T> Climate.RTree.Node<T> buildTyped(
            int parameterCount,
            List<? extends Climate.RTree.Node<T>> children
    ) {
        if (children.isEmpty()) {
            throw new IllegalStateException("Need at least one child to build a node");
        }
        if (children.size() == 1) {
            return children.get(0);
        }
        if (children.size() <= CHILDREN_PER_NODE) {
            children.sort(Comparator.comparingLong(node -> centerMagnitude(node, parameterCount)));
            return new Climate.RTree.SubTree<>(children);
        }

        Candidate<T> candidate = chooseCandidate(parameterCount, children);
        List<Climate.RTree.SubTree<T>> buckets = bucketize(candidate.order(), candidate.bucketSize());
        sort(buckets, parameterCount, candidate.dimension(), true);
        List<Climate.RTree.Node<T>> builtChildren = new ArrayList<>(buckets.size());
        for (Climate.RTree.SubTree<T> bucket : buckets) {
            builtChildren.add(buildTyped(parameterCount, Arrays.asList(bucket.children)));
        }
        return new Climate.RTree.SubTree<>(builtChildren);
    }

    @SuppressWarnings("unchecked")
    private static <T> Candidate<T> chooseCandidate(
            int parameterCount,
            List<? extends Climate.RTree.Node<T>> children
    ) {
        int bucketSize = bucketSize(children.size());
        long bestCost = Long.MAX_VALUE;
        int bestDimension = -1;
        List<Climate.RTree.Node<T>> bestOrder = null;
        long[] bounds = new long[BOUND_COUNT * 2];

        for (int dimension = 0; dimension < parameterCount; dimension++) {
            sort(children, parameterCount, dimension, false);
            long cost = bucketCost(children, bucketSize, bounds);
            if (bestCost > cost) {
                bestCost = cost;
                bestDimension = dimension;
                bestOrder = new ArrayList<>(children);
            }
        }
        return new Candidate<>(bestDimension, bucketSize, bestOrder);
    }

    private static long bucketCost(
            List<? extends Climate.RTree.Node<?>> nodes,
            int bucketSize,
            long[] bounds
    ) {
        long total = 0L;
        for (int start = 0; start < nodes.size(); start += bucketSize) {
            int end = Math.min(start + bucketSize, nodes.size());
            initializeBounds(nodes.get(start), bounds);
            for (int index = start + 1; index < end; index++) {
                extendBounds(nodes.get(index), bounds);
            }
            for (int dimension = 0; dimension < BOUND_COUNT; dimension++) {
                total += Math.abs(bounds[dimension + BOUND_COUNT] - bounds[dimension]);
            }
        }
        return total;
    }

    private static <T> List<Climate.RTree.SubTree<T>> bucketize(
            List<Climate.RTree.Node<T>> order,
            int bucketSize
    ) {
        List<Climate.RTree.SubTree<T>> buckets = new ArrayList<>((order.size() + bucketSize - 1) / bucketSize);
        for (int start = 0; start < order.size(); start += bucketSize) {
            int end = Math.min(start + bucketSize, order.size());
            buckets.add(new Climate.RTree.SubTree<>(order.subList(start, end)));
        }
        return buckets;
    }

    private static <T> List<Climate.Parameter> buildParameterSpaceTyped(
            List<? extends Climate.RTree.Node<T>> children
    ) {
        if (children.isEmpty()) {
            throw new IllegalArgumentException("SubTree needs at least one child");
        }
        Climate.RTree.Node<T> first = children.get(0);
        if (children.size() == 1) {
            return new ArrayList<>(Arrays.asList(first.parameterSpace));
        }

        long[] bounds = new long[BOUND_COUNT * 2];
        initializeBounds(first, bounds);
        for (int index = 1; index < children.size(); index++) {
            extendBounds(children.get(index), bounds);
        }
        List<Climate.Parameter> parameters = new ArrayList<>(BOUND_COUNT);
        for (int dimension = 0; dimension < BOUND_COUNT; dimension++) {
            parameters.add(new Climate.Parameter(bounds[dimension], bounds[dimension + BOUND_COUNT]));
        }
        return parameters;
    }

    private static void initializeBounds(Climate.RTree.Node<?> node, long[] bounds) {
        for (int dimension = 0; dimension < BOUND_COUNT; dimension++) {
            Climate.Parameter parameter = node.parameterSpace[dimension];
            bounds[dimension] = parameter.min();
            bounds[dimension + BOUND_COUNT] = parameter.max();
        }
    }

    private static void extendBounds(Climate.RTree.Node<?> node, long[] bounds) {
        for (int dimension = 0; dimension < BOUND_COUNT; dimension++) {
            Climate.Parameter parameter = node.parameterSpace[dimension];
            bounds[dimension] = Math.min(bounds[dimension], parameter.min());
            bounds[dimension + BOUND_COUNT] = Math.max(bounds[dimension + BOUND_COUNT], parameter.max());
        }
    }

    private static int bucketSize(int nodeCount) {
        int size = 1;
        while ((long) size * CHILDREN_PER_NODE < nodeCount) {
            size *= CHILDREN_PER_NODE;
        }
        return size;
    }

    private static <T> void sort(
            List<? extends Climate.RTree.Node<T>> children,
            int parameterCount,
            int firstDimension,
            boolean absolute
    ) {
        Comparator<Climate.RTree.Node<T>> comparator = comparator(firstDimension, absolute);
        for (int offset = 1; offset < parameterCount; offset++) {
            comparator = comparator.thenComparing(
                    comparator((firstDimension + offset) % parameterCount, absolute)
            );
        }
        children.sort(comparator);
    }

    private static <T> Comparator<Climate.RTree.Node<T>> comparator(int dimension, boolean absolute) {
        return Comparator.comparingLong(node -> {
            Climate.Parameter parameter = node.parameterSpace[dimension];
            long center = (parameter.min() + parameter.max()) / 2L;
            return absolute ? Math.abs(center) : center;
        });
    }

    private static long centerMagnitude(Climate.RTree.Node<?> node, int parameterCount) {
        long magnitude = 0L;
        for (int dimension = 0; dimension < parameterCount; dimension++) {
            Climate.Parameter parameter = node.parameterSpace[dimension];
            magnitude += Math.abs((parameter.min() + parameter.max()) / 2L);
        }
        return magnitude;
    }

    private record Candidate<T>(
            int dimension,
            int bucketSize,
            List<Climate.RTree.Node<T>> order
    ) {
    }
}
