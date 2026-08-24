package com.moepus.byepregen.dfc.opt;

import com.moepus.byepregen.dfc.ast.AstNode;

@FunctionalInterface
public interface OptimizationPass {
    AstNode apply(AstNode root);
}
