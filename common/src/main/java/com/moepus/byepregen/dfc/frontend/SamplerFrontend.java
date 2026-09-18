package com.moepus.byepregen.dfc.frontend;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.*;
import com.moepus.byepregen.dfc.runtime.*;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.densityfunction.generator.ConstantFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction.*;
import net.minecraft.world.level.levelgen.densityfunction.op.UnaryFunction.*;
import net.minecraft.world.level.levelgen.densityfunction.op.ClampFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.LerpFunction;

/** Adapter for already-selected native samplers; shares the column AST and compiler pipeline. */
public final class SamplerFrontend {
    private enum BinaryOp { ADD, SUB, MUL, DIV }
    private enum UnaryOp { ABS, SQUARE, CUBE, SQRT, RECIPROCAL, NEGATE, SQUEEZE, LOG, SIGN }

    public AstNode read(DensitySampler sampler) { return this.read(sampler, 0); }
    private AstNode read(DensitySampler sampler, int depth) {
        if (sampler instanceof CompiledDensitySampler compiled) return compiled.root();
        if (sampler instanceof ConstantFunction.Sampler constant) return new ConstantNode(constant.value());
        AstNode result = this.binary(sampler, depth + 1);
        if (result == null) result = this.unary(sampler, depth + 1);
        if (result == null) result = this.other(sampler, depth + 1);
        return result != null ? result : new DelegateNode(new NativeDensitySource(sampler, 7, true), false);
    }

    private AstNode binary(DensitySampler sampler, int depth) {
        return switch (sampler) {
            case AddSampler s -> this.binary(BinaryOp.ADD, s.left(), s.right(), depth);
            case SubSampler s -> this.binary(BinaryOp.SUB, s.left(), s.right(), depth);
            case MulSampler s -> this.binary(BinaryOp.MUL, s.left(), s.right(), depth);
            case DivSampler s -> this.binary(BinaryOp.DIV, s.left(), s.right(), depth);
            case MinSampler s -> new MinShortNode( this.read(s.left(), depth), this.read(s.right(), depth), s.rightMinValue());
            case MaxSampler s -> new MaxShortNode( this.read(s.left(), depth), this.read(s.right(), depth), s.rightMaxValue());
            default -> this.constantBinary(sampler, depth);
        };
    }

    private AstNode constantBinary(DensitySampler sampler, int depth) {
        return switch (sampler) {
            case ConstAddSampler s -> new AddNode( this.read(s.left(), depth), new ConstantNode(s.right()));
            case ConstMulSampler s -> new MulNode( this.read(s.left(), depth), new ConstantNode(s.right()));
            case ConstSubSampler s -> new SubNode( new ConstantNode(s.left()), this.read(s.right(), depth));
            case ConstDivSampler s -> new DivNode( new ConstantNode(s.left()), this.read(s.right(), depth));
            case ConstMinSampler s -> new MinNode( this.read(s.left(), depth), new ConstantNode(s.right()));
            case ConstMaxSampler s -> new MaxNode( this.read(s.left(), depth), new ConstantNode(s.right()));
            default -> null;
        };
    }

    private AstNode binary(BinaryOp op, DensitySampler left, DensitySampler right, int depth) {
        AstNode a = this.read(left, depth), b = this.read(right, depth);
        return switch (op) {
            case ADD -> new AddNode(a, b);
            case SUB -> new SubNode(a, b);
            case MUL -> new MulNode(a, b);
            case DIV -> new DivNode(a, b);
        };
    }

    private AstNode unary(DensitySampler sampler, int depth) {
        return switch (sampler) {
            case AbsSampler s -> this.unary(UnaryOp.ABS, s.input(), depth);
            case SquareSampler s -> this.unary(UnaryOp.SQUARE, s.input(), depth);
            case CubeSampler s -> this.unary(UnaryOp.CUBE, s.input(), depth);
            case SqrtSampler s -> this.unary(UnaryOp.SQRT, s.input(), depth);
            case ReciprocalSampler s -> this.unary(UnaryOp.RECIPROCAL, s.input(), depth);
            case NegateSampler s -> this.unary(UnaryOp.NEGATE, s.input(), depth);
            default -> this.otherUnary(sampler, depth);
        };
    }

    private AstNode otherUnary(DensitySampler sampler, int depth) {
        return switch (sampler) {
            case LeakyReLUSampler s -> new NegMulNode(this.read(s.input(), depth), s.negativeFactor());
            case SqueezeSampler s -> this.unary(UnaryOp.SQUEEZE, s.input(), depth);
            case LogSampler s -> this.unary(UnaryOp.LOG, s.input(), depth);
            case SignSampler s -> this.unary(UnaryOp.SIGN, s.input(), depth);
            case ClampFunction.Sampler s -> new NativeUnaryNode(this.read(s.input(), depth), NativeUnaryOp.CLAMP, s.min(), s.max());
            default -> null;
        };
    }

    private AstNode unary(UnaryOp op, DensitySampler input, int depth) {
        AstNode child = this.read(input, depth);
        return switch (op) {
            case ABS -> new AbsNode(child);
            case SQUARE -> new SquareNode(child);
            case CUBE -> new CubeNode(child);
            case NEGATE -> new NegNode(child);
            case SQUEEZE -> new SqueezeNode(child);
            case RECIPROCAL -> new DivNode(new ConstantNode(1), child);
            case SQRT -> new NativeUnaryNode(child, NativeUnaryOp.SQRT, 0, 0);
            case LOG -> new NativeUnaryNode(child, NativeUnaryOp.LOG, 0, 0);
            case SIGN -> new NativeUnaryNode(child, NativeUnaryOp.SIGN, 0, 0);
        };
    }

    private AstNode other(DensitySampler sampler, int depth) {
        return switch (sampler) {
            case LerpFunction.Sampler s -> new LerpNode(this.read(s.alpha(), depth), this.read(s.first(), depth), this.read(s.second(), depth));
            case LerpFunction.ConstFirstSampler s -> new LerpNode(this.read(s.alpha(), depth), new ConstantNode(s.first()), this.read(s.second(), depth));
            case LerpFunction.ConstSecondSampler s -> new LerpNode(this.read(s.alpha(), depth), this.read(s.first(), depth), new ConstantNode(s.second()));
            default -> null;
        };
    }

}
