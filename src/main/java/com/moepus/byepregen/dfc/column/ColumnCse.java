/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.column;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.*;
import com.ishland.c2me.opts.dfc.common.ast.noise.DFTWeirdScaledSamplerNode;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

final class ColumnCse {

    private ColumnCse() {
    }

    static CanonicalGraph canonicalize(AstNode root, Set<AstNode> forced2D) {
        Canonicalizer canonicalizer = new Canonicalizer(forced2D);
        return new CanonicalGraph(canonicalizer.rewrite(root), canonicalizer.canonicalForced2D);
    }

    static MemoizationPlan plan(CanonicalGraph graph) {
        DependencyAnalysis dependencies = new DependencyAnalysis(graph.forced2D);
        UseAnalysis uses = new UseAnalysis(dependencies);
        uses.collect(graph.root);
        Set<AstNode> candidates = Collections.newSetFromMap(new IdentityHashMap<>());
        AstNode directRoot = graph.root instanceof RootNode root ? root.next : graph.root;
        for (AstNode node : uses.references.keySet()) {
            // Materialize only where a 2D value crosses into a Y-dependent loop/root,
            // or where the canonical DAG references it more than once.
            boolean boundary = node == directRoot || uses.hasYDependentParent.contains(node);
            boolean shared = uses.references.getOrDefault(node, 0) > 1;
            if (!dependencies.isYDependent(node) && isMemoizable(node, graph.forced2D)
                    && (boundary || shared)) {
                candidates.add(node);
            }
        }
        return MemoizationPlan.create(graph.root, candidates);
    }

    private static boolean isMemoizable(AstNode node, Set<AstNode> forced2D) {
        if (forced2D.contains(node)) return true;
        return !(node instanceof ConstantNode) && !(node instanceof CoordinateNode);
    }

    record CanonicalGraph(AstNode root, Set<AstNode> forced2D) {
    }

    static final class MemoizationPlan {

        private final Map<AstNode, Integer> slots = new IdentityHashMap<>();

        private static MemoizationPlan create(AstNode root, Set<AstNode> candidates) {
            MemoizationPlan plan = new MemoizationPlan();
            plan.assign(root, candidates, Collections.newSetFromMap(new IdentityHashMap<>()));
            return plan;
        }

        private void assign(AstNode node, Set<AstNode> candidates, Set<AstNode> visited) {
            if (!visited.add(node)) return;
            if (candidates.contains(node)) this.slots.put(node, this.slots.size());
            for (AstNode child : node.getChildren()) this.assign(child, candidates, visited);
        }

        boolean contains(AstNode node) {
            return this.slots.containsKey(node);
        }

        int slot(AstNode node) {
            Integer slot = this.slots.get(node);
            if (slot == null) throw new IllegalArgumentException("Node is not memoized");
            return slot;
        }
    }

    private static final class Canonicalizer extends ColumnAstRewriter {

        private final Set<AstNode> forced2D;
        private final Set<AstNode> canonicalForced2D = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<AstNode, AstNode> canonical = new HashMap<>();

        private Canonicalizer(Set<AstNode> forced2D) {
            this.forced2D = forced2D;
        }

        @Override
        AstNode rewriteOnce(AstNode node) {
            AstNode rebuilt = this.rebuild(node);
            AstNode result = isCanonicalizable(rebuilt)
                    ? this.canonical.computeIfAbsent(rebuilt, unused -> rebuilt)
                    : rebuilt;
            if (this.forced2D.contains(node)) this.canonicalForced2D.add(result);
            return result;
        }

        private static boolean isCanonicalizable(AstNode node) {
            return !(node instanceof RootNode)
                    && !(node instanceof CacheLikeNode)
                    && !(node instanceof ColumnCacheNode)
                    && !(node instanceof DelegateNode);
        }
    }

    private static final class DependencyAnalysis {

        private final Set<AstNode> forced2D;
        private final Map<AstNode, Boolean> results = new IdentityHashMap<>();

        private DependencyAnalysis(Set<AstNode> forced2D) {
            this.forced2D = forced2D;
        }

        boolean isYDependent(AstNode node) {
            Boolean existing = this.results.get(node);
            if (existing != null) return existing;
            boolean result = this.compute(node);
            this.results.put(node, result);
            return result;
        }

        private boolean compute(AstNode node) {
            if (this.forced2D.contains(node) || node instanceof ConstantNode) return false;
            if (node instanceof ColumnCacheNode cache) return cache.mode() == ColumnCacheNode.Mode.INTERPOLATED;
            if (node instanceof CoordinateNode coordinate) return coordinate.axis == CoordinateNode.Axis.Y;
            if (node instanceof YClampedGradientNode || node instanceof DFTWeirdScaledSamplerNode
                    || node instanceof FindTopSurfaceNode || node instanceof DelegateNode
                    || node instanceof CacheLikeNode) return true;
            AstNode[] children = node.getChildren();
            if (children.length == 0) return true;
            for (AstNode child : children) if (this.isYDependent(child)) return true;
            return false;
        }
    }

    private static final class UseAnalysis {

        private final DependencyAnalysis dependencies;
        private final Map<AstNode, Integer> references = new IdentityHashMap<>();
        private final Set<AstNode> hasYDependentParent = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<AstNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        private UseAnalysis(DependencyAnalysis dependencies) {
            this.dependencies = dependencies;
        }

        private void collect(AstNode node) {
            this.references.putIfAbsent(node, 0);
            if (!this.visited.add(node)) return;
            boolean yDependentParent = this.dependencies.isYDependent(node);
            for (AstNode child : node.getChildren()) {
                this.references.merge(child, 1, Integer::sum);
                if (yDependentParent) this.hasYDependentParent.add(child);
                this.collect(child);
            }
        }
    }
}

