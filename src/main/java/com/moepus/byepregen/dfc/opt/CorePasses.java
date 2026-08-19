/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.opt;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.*;
import com.moepus.byepregen.dfc.ast.AstRewriter;
import net.minecraft.util.Mth;

final class CorePasses {
    private CorePasses() {
    }

    static AstNode canonicalize(AstNode root) {
        return AstRewriter.rewrite(root, CorePasses::canonicalizeNode);
    }

    private static AstNode canonicalizeNode(AstNode node) {
        if (!(node instanceof BinaryNode binary) || !binary.canSwapOperands()) return node;
        AstNode left = binary.left();
        AstNode right = binary.right();
        if (!(right instanceof ConstantNode) || left instanceof ConstantNode) return node;
        return binary.withOperands(right, left);
    }

    static AstNode constantFold(AstNode root) {
        return AstRewriter.rewrite(root, CorePasses::foldNode);
    }

    private static AstNode foldNode(AstNode node) {
        if (node instanceof BinaryNode binary
                && binary.left() instanceof ConstantNode a
                && binary.right() instanceof ConstantNode b) {
            return new ConstantNode(foldBinary(binary, a.value(), b.value()));
        }
        if (node instanceof UnaryNode unary && unary.operand() instanceof ConstantNode value) {
            return foldUnary(unary, value.value());
        }
        return node;
    }

    private static double foldBinary(BinaryNode node, double left, double right) {
        if (node instanceof AddNode) return left + right;
        if (node instanceof MulNode) return left * right;
        if (node instanceof DivNode) return left / right;
        if (node instanceof MinNode || node instanceof MinShortNode) return Math.min(left, right);
        if (node instanceof MaxNode || node instanceof MaxShortNode) return Math.max(left, right);
        throw new IllegalArgumentException("Unknown binary node " + node.getClass().getName());
    }

    private static AstNode foldUnary(UnaryNode node, double value) {
        if (node instanceof AbsNode) return new ConstantNode(Math.abs(value));
        if (node instanceof NegNode) return new ConstantNode(-value);
        if (node instanceof SquareNode) return new ConstantNode(value * value);
        if (node instanceof CubeNode) return new ConstantNode(value * value * value);
        if (node instanceof NegMulNode neg) {
            return new ConstantNode(value > 0.0D ? value : value * neg.multiplier());
        }
        if (node instanceof SqueezeNode) {
            double clamped = Mth.clamp(value, -1.0D, 1.0D);
            return new ConstantNode(clamped * 0.5D - clamped * clamped * clamped / 24.0D);
        }
        return (AstNode) node;
    }

    static AstNode strengthReduce(AstNode root) {
        return AstRewriter.rewrite(root, CorePasses::reduceNode);
    }

    private static AstNode reduceNode(AstNode node) {
        if (node instanceof MulNode mul) {
            if (mul.left() == mul.right() || mul.left().equals(mul.right())) {
                return new SquareNode(mul.left());
            }
            if (mul.left() instanceof SquareNode square && square.operand().equals(mul.right())) {
                return new CubeNode(mul.right());
            }
            if (mul.right() instanceof SquareNode square && square.operand().equals(mul.left())) {
                return new CubeNode(mul.left());
            }
            if (mul.left() instanceof ConstantNode c && c.value() == -1.0D) {
                return new NegNode(mul.right());
            }
        }
        if (node instanceof AbsNode abs && nonNegative(abs.operand())) return abs.operand();
        if (node instanceof SquareNode square && square.operand() instanceof AbsNode abs) {
            return new SquareNode(abs.operand());
        }
        if (node instanceof NegMulNode neg && nonNegative(neg.operand())) return neg.operand();
        return node;
    }

    static AstNode algebraicSimplify(AstNode root) {
        return AstRewriter.rewrite(root, CorePasses::simplifyNode);
    }

    private static AstNode simplifyNode(AstNode node) {
        if (node instanceof AddNode add) return simplifyAdd(add);
        if (node instanceof MulNode mul) return simplifyMul(mul);
        if (node instanceof NegNode neg && neg.operand() instanceof NegNode inner) return inner.operand();
        if (node instanceof AbsNode abs && abs.operand() instanceof AbsNode) return abs.operand();
        if (node instanceof NegMulNode neg && neg.multiplier() == 1.0D) return neg.operand();
        if (node instanceof NegMulNode neg && neg.multiplier() == -1.0D) return new AbsNode(neg.operand());
        if (node instanceof AbsNode abs && abs.operand() instanceof CubeNode cube) {
            return new CubeNode(new AbsNode(cube.operand()));
        }
        return node;
    }

    private static AstNode simplifyAdd(AddNode node) {
        if (isConstant(node.left(), 0.0D)) return node.right();
        if (node.left() instanceof ConstantNode outer && node.right() instanceof AddNode inner
                && inner.left() instanceof ConstantNode nested) {
            return new AddNode(new ConstantNode(outer.value() + nested.value()), inner.right());
        }
        return node;
    }

    private static AstNode simplifyMul(MulNode node) {
        if (isConstant(node.left(), 0.0D)) return new ConstantNode(0.0D);
        if (isConstant(node.left(), 1.0D)) return node.right();
        if (node.left() instanceof ConstantNode outer && node.right() instanceof MulNode inner
                && inner.left() instanceof ConstantNode nested) {
            return new MulNode(new ConstantNode(outer.value() * nested.value()), inner.right());
        }
        return node;
    }

    static AstNode identityEliminate(AstNode root) {
        return AstRewriter.rewrite(root, CorePasses::eliminateIdentity);
    }

    private static AstNode eliminateIdentity(AstNode node) {
        if (node instanceof BinaryNode binary && binary.left().equals(binary.right())) {
            if (node instanceof MinNode || node instanceof MaxNode
                    || node instanceof MinShortNode || node instanceof MaxShortNode) {
                return binary.left();
            }
        }
        if (node instanceof MinNode min && min.right() instanceof MaxNode max
                && contains(max, min.left())) return min.left();
        if (node instanceof MinNode min && min.left() instanceof MaxNode max
                && contains(max, min.right())) return min.right();
        if (node instanceof MaxNode max && max.right() instanceof MinNode min
                && contains(min, max.left())) return max.left();
        if (node instanceof MaxNode max && max.left() instanceof MinNode min
                && contains(min, max.right())) return max.right();
        return node;
    }

    static AstNode rangePrune(AstNode root) {
        return AstRewriter.rewrite(root, CorePasses::pruneRange);
    }

    private static AstNode pruneRange(AstNode node) {
        if (node instanceof MinShortNode min && min.left() instanceof ConstantNode value) {
            return value.value() < min.rightMin()
                    ? value : new MinNode(min.left(), min.right());
        }
        if (node instanceof MaxShortNode max && max.left() instanceof ConstantNode value) {
            return value.value() > max.rightMax()
                    ? value : new MaxNode(max.left(), max.right());
        }
        if (!(node instanceof RangeChoiceNode range)) return node;
        if (range.minInclusive() >= range.maxExclusive()) return range.whenOutOfRange();
        if (range.input() instanceof ConstantNode value) {
            return value.value() >= range.minInclusive() && value.value() < range.maxExclusive()
                    ? range.whenInRange() : range.whenOutOfRange();
        }
        if (range.whenInRange() instanceof RangeChoiceNode inner
                && inner.input().equals(range.input())) {
            if (range.minInclusive() >= inner.minInclusive()
                    && range.maxExclusive() <= inner.maxExclusive()) {
                return new RangeChoiceNode(range.input(), range.minInclusive(), range.maxExclusive(),
                        inner.whenInRange(), range.whenOutOfRange());
            }
            if (range.maxExclusive() <= inner.minInclusive()
                    || range.minInclusive() >= inner.maxExclusive()) {
                return new RangeChoiceNode(range.input(), range.minInclusive(), range.maxExclusive(),
                        inner.whenOutOfRange(), range.whenOutOfRange());
            }
        }
        if (range.whenOutOfRange() instanceof RangeChoiceNode inner
                && inner.input().equals(range.input())
                && inner.minInclusive() >= range.minInclusive()
                && inner.maxExclusive() <= range.maxExclusive()) {
            return new RangeChoiceNode(range.input(), range.minInclusive(), range.maxExclusive(),
                    range.whenInRange(), inner.whenOutOfRange());
        }
        return node;
    }

    private static boolean contains(BinaryNode node, AstNode target) {
        return node.left().equals(target) || node.right().equals(target);
    }

    private static boolean nonNegative(AstNode node) {
        return node instanceof SquareNode || node instanceof AbsNode
                || node instanceof ConstantNode value && value.value() >= 0.0D;
    }

    private static boolean isConstant(AstNode node, double value) {
        return node instanceof ConstantNode constant && constant.value() == value;
    }
}
