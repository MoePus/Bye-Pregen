/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.ast;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

public final class AstRewriter {
    private AstRewriter() {
    }

    public static AstNode rewrite(AstNode root, UnaryOperator<AstNode> rule) {
        return new Session(rule).rewrite(root);
    }

    private static final class Session {
        private final UnaryOperator<AstNode> rule;
        private final Map<AstNode, AstNode> rewritten = new IdentityHashMap<>();

        private Session(UnaryOperator<AstNode> rule) {
            this.rule = Objects.requireNonNull(rule, "rule");
        }

        private AstNode rewrite(AstNode node) {
            AstNode existing = this.rewritten.get(node);
            if (existing != null) return existing;
            AstNode[] children = node.children();
            AstNode[] rewrittenChildren = children;
            for (int i = 0; i < children.length; ++i) {
                AstNode child = this.rewrite(children[i]);
                if (child != children[i]) {
                    if (rewrittenChildren == children) rewrittenChildren = children.clone();
                    rewrittenChildren[i] = child;
                }
            }
            AstNode rebuilt = rewrittenChildren == children ? node : node.withChildren(rewrittenChildren);
            AstNode result = Objects.requireNonNull(this.rule.apply(rebuilt), "AST rule returned null");
            this.rewritten.put(node, result);
            return result;
        }
    }
}
