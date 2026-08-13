/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.analysis;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.AstTransformer;
import com.ishland.c2me.opts.dfc.common.ducks.IFastCacheLike;

import java.util.Objects;

public final class ColumnCacheNode implements AstNode {

    private final IFastCacheLike cacheLike;
    private final Mode mode;

    ColumnCacheNode(IFastCacheLike cacheLike, Mode mode) {
        this.cacheLike = Objects.requireNonNull(cacheLike);
        this.mode = Objects.requireNonNull(mode);
    }

    public IFastCacheLike cacheLike() {
        return this.cacheLike;
    }

    public Mode mode() {
        return this.mode;
    }

    @Override
    public AstNode[] getChildren() {
        return new AstNode[0];
    }

    @Override
    public AstNode transform(AstTransformer transformer) {
        return transformer.transform(this);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ColumnCacheNode that
                && this.cacheLike == that.cacheLike
                && this.mode == that.mode;
    }

    @Override
    public int hashCode() {
        int result = 31 * this.getClass().hashCode() + System.identityHashCode(this.cacheLike);
        return 31 * result + this.mode.hashCode();
    }

    @Override
    public boolean relaxedEquals(AstNode node) {
        return node instanceof ColumnCacheNode that
                && this.cacheLike.getClass() == that.cacheLike.getClass()
                && this.mode == that.mode;
    }

    @Override
    public int relaxedHashCode() {
        int result = 31 * this.getClass().hashCode() + this.cacheLike.getClass().hashCode();
        return 31 * result + this.mode.hashCode();
    }

    public enum Mode {
        FLAT,
        INTERPOLATED,
    }
}
