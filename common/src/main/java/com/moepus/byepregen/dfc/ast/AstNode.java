/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.ast;

public interface AstNode {
    AstNode[] children();

    AstNode withChildren(AstNode[] children);
}
