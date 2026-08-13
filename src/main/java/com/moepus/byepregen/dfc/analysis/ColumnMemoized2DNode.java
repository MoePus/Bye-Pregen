/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.analysis;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.AstTransformer;

import java.util.Objects;

public final class ColumnMemoized2DNode implements AstNode {

    private final AstNode delegate;
    private final int slot;

    ColumnMemoized2DNode(AstNode delegate, int slot) {
        this.delegate = Objects.requireNonNull(delegate);
        if (slot < 0) throw new IllegalArgumentException("Column memoized slot must not be negative");
        this.slot = slot;
    }

    public AstNode delegate() {
        return this.delegate;
    }

    public int slot() {
        return this.slot;
    }

    @Override
    public AstNode[] getChildren() {
        return new AstNode[]{this.delegate};
    }

    @Override
    public AstNode transform(AstTransformer transformer) {
        AstNode transformed = this.delegate.transform(transformer);
        ColumnMemoized2DNode node = transformed == this.delegate
                ? this
                : new ColumnMemoized2DNode(transformed, this.slot);
        return transformer.transform(node);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ColumnMemoized2DNode that && this.delegate.equals(that.delegate);
    }

    @Override
    public int hashCode() {
        return 31 * this.getClass().hashCode() + this.delegate.hashCode();
    }

    @Override
    public boolean relaxedEquals(AstNode node) {
        return node instanceof ColumnMemoized2DNode that && this.delegate.relaxedEquals(that.delegate);
    }

    @Override
    public int relaxedHashCode() {
        return 31 * this.getClass().hashCode() + this.delegate.relaxedHashCode();
    }
}
