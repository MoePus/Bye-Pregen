/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.column;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.*;
import com.ishland.c2me.opts.dfc.common.ast.misc.*;
import com.ishland.c2me.opts.dfc.common.ast.noise.DFTWeirdScaledSamplerNode;
import com.ishland.c2me.opts.dfc.common.ast.noise.GenericShiftedNoiseNode;
import com.ishland.c2me.opts.dfc.common.ast.spline.SplineAstNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.*;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.level.levelgen.DensityFunctions;

abstract class ColumnAstRewriter {

    private final Map<AstNode, AstNode> rewritten = new IdentityHashMap<>();

    final AstNode rewrite(AstNode node) {
        AstNode existing = this.rewritten.get(node);
        if (existing != null) return existing;
        AstNode replacement = this.rewriteOnce(node);
        this.rewritten.put(node, replacement);
        return replacement;
    }

    abstract AstNode rewriteOnce(AstNode node);

    final AstNode rebuild(AstNode node) {
        if (node instanceof RootNode root) return this.rebuildRoot(root);
        if (node instanceof AbstractUnaryNode unary) return this.rebuildUnary(unary);
        if (node instanceof AbstractBinaryNode binary) return this.rebuildBinary(binary);
        if (node instanceof RangeChoiceNode range) return this.rebuildRange(range);
        if (node instanceof CacheLikeNode cache) return this.rebuildCache(cache);
        if (node instanceof FindTopSurfaceNode surface) return this.rebuildSurface(surface);
        if (node instanceof GenericShiftedNoiseNode noise) return this.rebuildNoise(noise);
        if (node instanceof DFTWeirdScaledSamplerNode sampler) return this.rebuildWeirdSampler(sampler);
        if (node instanceof SplineAstNode spline) return this.rebuildSpline(spline);
        if (node instanceof ColumnMemoized2DNode memoized) return this.rebuildMemoized(memoized);
        if (node.getChildren().length != 0) {
            throw new UnsupportedOperationException("Unsupported Column AST transform: " + node.getClass().getName());
        }
        return node;
    }

    private AstNode rebuildRoot(RootNode node) {
        AstNode next = this.rewrite(node.next);
        return next == node.next ? node : new RootNode(next);
    }

    private AstNode rebuildUnary(AbstractUnaryNode node) {
        AstNode operand = this.rewrite(node.operand);
        if (operand == node.operand) return node;
        if (node instanceof AbsNode) return new AbsNode(operand);
        if (node instanceof CubeNode) return new CubeNode(operand);
        if (node instanceof SquareNode) return new SquareNode(operand);
        if (node instanceof SqueezeNode) return new SqueezeNode(operand);
        if (node instanceof NegMulNode negMul) return new NegMulNode(operand, negMul.negMul);
        throw new UnsupportedOperationException("Unsupported Column unary transform: " + node.getClass().getName());
    }

    private AstNode rebuildBinary(AbstractBinaryNode node) {
        AstNode left = this.rewrite(node.left);
        AstNode right = this.rewrite(node.right);
        if (left == node.left && right == node.right) return node;
        if (node instanceof AddNode) return new AddNode(left, right);
        if (node instanceof DivNode) return new DivNode(left, right);
        if (node instanceof MaxShortNode shortNode) return new MaxShortNode(left, right, shortNode.rightMax);
        if (node instanceof MaxNode) return new MaxNode(left, right);
        if (node instanceof MinShortNode shortNode) return new MinShortNode(left, right, shortNode.rightMin);
        if (node instanceof MinNode) return new MinNode(left, right);
        if (node instanceof MulNode) return new MulNode(left, right);
        throw new UnsupportedOperationException("Unsupported Column binary transform: " + node.getClass().getName());
    }

    private AstNode rebuildRange(RangeChoiceNode node) {
        AstNode input = this.rewrite(node.input);
        AstNode in = this.rewrite(node.whenInRange);
        AstNode out = this.rewrite(node.whenOutOfRange);
        if (input == node.input && in == node.whenInRange && out == node.whenOutOfRange) return node;
        return new RangeChoiceNode(input, node.minInclusive, node.maxExclusive, in, out);
    }

    private AstNode rebuildCache(CacheLikeNode node) {
        AstNode delegate = this.rewrite(node.getDelegate());
        return delegate == node.getDelegate() ? node : new CacheLikeNode(node.getCacheLike(), delegate);
    }

    private AstNode rebuildSurface(FindTopSurfaceNode node) {
        AstNode density = this.rewrite(node.density);
        AstNode upper = this.rewrite(node.upperBound);
        AstNode lower = this.rewrite(node.lowerBound);
        if (density == node.density && upper == node.upperBound && lower == node.lowerBound) return node;
        return new FindTopSurfaceNode(density, upper, lower, node.cellHeight);
    }

    private AstNode rebuildNoise(GenericShiftedNoiseNode node) {
        AstNode x = this.rewrite(node.inputX);
        AstNode y = this.rewrite(node.inputY);
        AstNode z = this.rewrite(node.inputZ);
        if (x == node.inputX && y == node.inputY && z == node.inputZ) return node;
        return new GenericShiftedNoiseNode(x, y, z, node.noise);
    }

    private AstNode rebuildWeirdSampler(DFTWeirdScaledSamplerNode node) {
        AstNode input = this.rewrite(node.input);
        return input == node.input ? node : new DFTWeirdScaledSamplerNode(input, node.noise, node.mapper);
    }

    private AstNode rebuildSpline(SplineAstNode node) {
        Map<DensityFunctions.Spline.Coordinate, AstNode> replacements = new IdentityHashMap<>();
        for (Map.Entry<DensityFunctions.Spline.Coordinate, AstNode> entry : node.children.entrySet()) {
            AstNode child = entry.getValue();
            AstNode replacement = this.rewrite(child);
            if (replacement != child) {
                replacements.put(entry.getKey(), replacement);
            }
        }
        if (replacements.isEmpty()) return node;
        SplineAstNode rebuilt = new SplineAstNode(node.spline);
        rebuilt.children.clear();
        rebuilt.children.putAll(node.children);
        replacements.forEach(rebuilt.children::put);
        return rebuilt;
    }

    private AstNode rebuildMemoized(ColumnMemoized2DNode node) {
        AstNode delegate = this.rewrite(node.delegate());
        return delegate == node.delegate() ? node : new ColumnMemoized2DNode(delegate, node.slot());
    }
}
