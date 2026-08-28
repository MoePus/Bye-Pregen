/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.analysis;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Column cache normalization, structural CSE, dependency analysis, and lazy slot placement. */
public final class ColumnSpecializer {
    private ColumnSpecializer() {
    }

    public static Result specialize(AstNode root) {
        Normalization normalized = new Normalizer().normalize(root);
        Canonicalization canonical = new Canonicalizer(normalized.forced2D()).canonicalize(normalized.root());
        DependencyAnalysis dependencies = new DependencyAnalysis(canonical.forced2D());
        Usage usage = new Usage(dependencies);
        usage.collect(canonical.root(), null);
        Set<AstNode> candidates = selectCandidates(canonical, dependencies, usage);
        SlotAssigner slots = new SlotAssigner(candidates);
        AstNode result = slots.rewrite(canonical.root());
        return new Result(result, slots.count(), !dependencies.isYDependent(canonical.root()));
    }

    private static Set<AstNode> selectCandidates(
            Canonicalization graph, DependencyAnalysis dependencies, Usage usage
    ) {
        Set<AstNode> result = identitySet();
        AstNode directRoot = graph.root() instanceof RootNode root ? root.next() : graph.root();
        for (Map.Entry<AstNode, Integer> entry : usage.references.entrySet()) {
            AstNode node = entry.getKey();
            boolean boundary = node == directRoot || usage.yDependentParents.contains(node);
            boolean shared = entry.getValue() > 1;
            if (!dependencies.isYDependent(node) && memoizable(node, graph.forced2D())
                    && (boundary || shared)) {
                result.add(node);
            }
        }
        Set<AstNode> absorbed = identitySet();
        for (AstNode node : result) {
            Set<AstNode> parents = usage.parents.get(node);
            if (parents != null && parents.size() == 1
                    && result.contains(parents.iterator().next())) {
                absorbed.add(node);
            }
        }
        result.removeAll(absorbed);
        return result;
    }

    private static boolean memoizable(AstNode node, Set<AstNode> forced) {
        return forced.contains(node)
                || !(node instanceof ConstantNode || node instanceof CoordinateNode
                || node instanceof Memoized2DNode);
    }

    private static Set<AstNode> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    public record Result(AstNode root, int memoizedSlots, boolean yIndependent) {
    }

    private record Normalization(AstNode root, Set<AstNode> forced2D) {
    }

    private record Canonicalization(AstNode root, Set<AstNode> forced2D) {
    }

    private static final class Normalizer {
        private final Map<AstNode, AstNode> memo = new IdentityHashMap<>();
        private final Set<AstNode> forced = identitySet();

        private Normalization normalize(AstNode root) {
            return new Normalization(this.visit(root), this.forced);
        }

        private AstNode visit(AstNode node) {
            AstNode existing = this.memo.get(node);
            if (existing != null) return existing;
            AstNode[] children = node.children();
            AstNode[] rewritten = children.clone();
            boolean changed = false;
            for (int i = 0; i < children.length; ++i) {
                rewritten[i] = this.visit(children[i]);
                changed |= rewritten[i] != children[i];
            }
            AstNode rebuilt = changed ? node.withChildren(rewritten) : node;
            AstNode result = this.normalizeCache(rebuilt);
            this.memo.put(node, result);
            return result;
        }

        private AstNode normalizeCache(AstNode node) {
            if (!(node instanceof CacheNode cache)) return node;
            return switch (cache.kind()) {
                case CACHE_2D -> this.force2D(cache.delegate());
                case CACHE_ONCE, CACHE_ALL_IN_CELL -> cache.delegate();
                case FLAT_CACHE -> new SourceNode(cache.source(), SourceMode.FLAT);
                case INTERPOLATED -> new SourceNode(cache.source(), SourceMode.INTERPOLATED);
                case UNKNOWN -> new DelegateNode(cache.source(), false);
            };
        }

        private AstNode force2D(AstNode delegate) {
            this.forced.add(delegate);
            return delegate;
        }
    }

    private static final class Canonicalizer {
        private final Set<AstNode> inputForced;
        private final Set<AstNode> outputForced = identitySet();
        private final Map<AstNode, AstNode> memo = new IdentityHashMap<>();
        private final Map<AstNode, AstNode> canonical = new HashMap<>();

        private Canonicalizer(Set<AstNode> forced) {
            this.inputForced = forced;
        }

        private Canonicalization canonicalize(AstNode root) {
            return new Canonicalization(this.visit(root), this.outputForced);
        }

        private AstNode visit(AstNode node) {
            AstNode existing = this.memo.get(node);
            if (existing != null) return existing;
            AstNode[] children = node.children();
            AstNode[] rewritten = children.clone();
            boolean changed = false;
            for (int i = 0; i < children.length; ++i) {
                rewritten[i] = this.visit(children[i]);
                changed |= rewritten[i] != children[i];
            }
            AstNode rebuilt = changed ? node.withChildren(rewritten) : node;
            AstNode result = canonicalizable(rebuilt)
                    ? this.canonical.computeIfAbsent(rebuilt, ignored -> rebuilt) : rebuilt;
            if (this.inputForced.contains(node)) this.outputForced.add(result);
            this.memo.put(node, result);
            return result;
        }

        private static boolean canonicalizable(AstNode node) {
            return !(node instanceof RootNode || node instanceof SourceNode
                    || node instanceof DelegateNode || node instanceof Memoized2DNode);
        }
    }

    private static final class DependencyAnalysis {
        private final Set<AstNode> forced;
        private final Map<AstNode, Boolean> memo = new IdentityHashMap<>();

        private DependencyAnalysis(Set<AstNode> forced) {
            this.forced = forced;
        }

        private boolean isYDependent(AstNode node) {
            Boolean existing = this.memo.get(node);
            if (existing != null) return existing;
            boolean result = this.compute(node);
            this.memo.put(node, result);
            return result;
        }

        private boolean compute(AstNode node) {
            if (this.forced.contains(node) || node instanceof ConstantNode) return false;
            if (node instanceof CoordinateNode coordinate) return coordinate.axis() == Axis.Y;
            if (node instanceof SourceNode source) return source.mode() == SourceMode.INTERPOLATED;
            if (node instanceof DelegateNode delegate) return !delegate.yIndependent();
            if (node instanceof YClampedGradientNode || node instanceof WeirdScaledNode) return true;
            AstNode[] children = node.children();
            if (children.length == 0) return true;
            for (AstNode child : children) if (this.isYDependent(child)) return true;
            return false;
        }
    }

    private static final class Usage {
        private final DependencyAnalysis dependencies;
        private final Map<AstNode, Integer> references = new IdentityHashMap<>();
        private final Map<AstNode, Set<AstNode>> parents = new IdentityHashMap<>();
        private final Set<AstNode> yDependentParents = identitySet();
        private final Set<AstNode> visited = identitySet();

        private Usage(DependencyAnalysis dependencies) {
            this.dependencies = dependencies;
        }

        private void collect(AstNode node, AstNode parent) {
            this.references.merge(node, parent == null ? 0 : 1, Integer::sum);
            if (parent != null) {
                this.parents.computeIfAbsent(node, ignored -> identitySet()).add(parent);
            }
            if (parent != null && this.dependencies.isYDependent(parent)) {
                this.yDependentParents.add(node);
            }
            if (!this.visited.add(node)) return;
            for (AstNode child : node.children()) this.collect(child, node);
        }
    }

    private static final class SlotAssigner {
        private final Set<AstNode> candidates;
        private final Map<AstNode, AstNode> memo = new IdentityHashMap<>();
        private final Map<AstNode, Integer> slots = new IdentityHashMap<>();

        private SlotAssigner(Set<AstNode> candidates) {
            this.candidates = candidates;
        }

        private AstNode rewrite(AstNode node) {
            AstNode existing = this.memo.get(node);
            if (existing != null) return existing;
            AstNode[] children = node.children();
            AstNode[] rewritten = children.clone();
            boolean changed = false;
            for (int i = 0; i < children.length; ++i) {
                rewritten[i] = this.rewrite(children[i]);
                changed |= rewritten[i] != children[i];
            }
            AstNode rebuilt = changed ? node.withChildren(rewritten) : node;
            AstNode result = this.candidates.contains(node)
                    ? new Memoized2DNode(rebuilt, this.slot(node)) : rebuilt;
            this.memo.put(node, result);
            return result;
        }

        private int slot(AstNode node) {
            return this.slots.computeIfAbsent(node, ignored -> this.slots.size());
        }

        private int count() {
            return this.slots.size();
        }
    }
}
