/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.column;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.CacheLikeNode;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Function;

final class ColumnAstSpecializer {

    private static final String WRAPPING_PREFIX = "wrapping, ";

    private ColumnAstSpecializer() {
    }

    static AstNode specialize(AstNode optimized) {
        return specialize(optimized, cache -> normalizeKind(cache.getCacheLike().c2me$describeCacheLike()));
    }

    static AstNode specialize(AstNode optimized, Function<CacheLikeNode, String> cacheKind) {
        // Cache2D is a dependency fact, not a runtime cache in a fixed-X/Z column.
        // Normalize wrappers first so CSE can choose the highest reusable 2D subtree.
        CacheNormalizer normalizer = new CacheNormalizer(cacheKind);
        AstNode normalized = normalizer.rewrite(optimized);
        ColumnCse.CanonicalGraph canonical = ColumnCse.canonicalize(normalized, normalizer.forced2D);
        ColumnCse.MemoizationPlan plan = ColumnCse.plan(canonical);
        return new MemoizingRewriter(plan).rewrite(canonical.root());
    }

    static int countMemoized(AstNode root) {
        Set<AstNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return countMemoized(root, visited);
    }

    private static int countMemoized(AstNode node, Set<AstNode> visited) {
        if (!visited.add(node)) return 0;
        int count = node instanceof ColumnMemoized2DNode ? 1 : 0;
        for (AstNode child : node.getChildren()) {
            count += countMemoized(child, visited);
        }
        return count;
    }

    private static String normalizeKind(String description) {
        return description.startsWith(WRAPPING_PREFIX)
                ? description.substring(WRAPPING_PREFIX.length())
                : description;
    }

    private static final class CacheNormalizer extends ColumnAstRewriter {

        private final Set<AstNode> forced2D = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Function<CacheLikeNode, String> cacheKind;

        private CacheNormalizer(Function<CacheLikeNode, String> cacheKind) {
            this.cacheKind = cacheKind;
        }

        @Override
        AstNode rewriteOnce(AstNode node) {
            AstNode rebuilt = this.rebuild(node);
            if (!(rebuilt instanceof CacheLikeNode cache)) return rebuilt;
            String kind = this.cacheKind.apply(cache);
            return switch (kind) {
                case "cache_2d" -> this.removeCache2D(cache);
                case "cache_once", "cache_all_in_cell" -> cache.getDelegate();
                case "flat_cache" -> new ColumnCacheNode(cache.getCacheLike(), ColumnCacheNode.Mode.FLAT);
                case "interpolated" -> new ColumnCacheNode(cache.getCacheLike(), ColumnCacheNode.Mode.INTERPOLATED);
                default -> cache;
            };
        }

        private AstNode removeCache2D(CacheLikeNode cache) {
            AstNode delegate = cache.getDelegate();
            this.forced2D.add(delegate);
            return delegate;
        }
    }

    private static final class MemoizingRewriter extends ColumnAstRewriter {

        private final ColumnCse.MemoizationPlan plan;

        private MemoizingRewriter(ColumnCse.MemoizationPlan plan) {
            this.plan = plan;
        }

        @Override
        AstNode rewriteOnce(AstNode node) {
            AstNode rebuilt = this.rebuild(node);
            return this.plan.contains(node)
                    ? new ColumnMemoized2DNode(rebuilt, this.plan.slot(node))
                    : rebuilt;
        }
    }
}

