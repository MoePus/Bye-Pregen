/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.codegen;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.*;
import com.moepus.byepregen.dfc.codegen.BindingRegistry.FieldRef;
import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import com.moepus.byepregen.dfc.runtime.ColumnMath;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class PointMethodEmitter {
    static final String DESC = Type.getMethodDescriptor(Type.FLOAT_TYPE, Type.INT_TYPE,
            Type.INT_TYPE, Type.INT_TYPE, Type.getType(ColumnEvaluationContext.class));
    private static final String CONTEXT = Type.getInternalName(ColumnEvaluationContext.class);
    private static final String COLUMN_MATH = Type.getInternalName(ColumnMath.class);
    private static final String DENSITY_FUNCTION = Type.getInternalName(DensitySampler.class);

    private final String owner;
    private final boolean pointMode;
    private final ClassWriter writer;
    private final BindingRegistry bindings;
    private final PointBinaryEmitter binaries;
    private final SplineMethodEmitter splines;
    private final Map<AstNode, String> methods = new IdentityHashMap<>();

    PointMethodEmitter(GenerationContext context, boolean pointMode) {
        this.pointMode = pointMode;
        this.owner = context.owner();
        this.writer = context.writer();
        this.bindings = context.bindings();
        this.binaries = new PointBinaryEmitter(this::call, pointMode);
        this.splines = new SplineMethodEmitter(context, this::call, pointMode ? "scalar" : "volume");
    }

    String method(AstNode node) {
        String existing = this.methods.get(node);
        if (existing != null) return existing;
        String name = (this.pointMode ? "scalar" : "point") + this.methods.size() + "_" + node.getClass().getSimpleName();
        this.methods.put(node, name);
        this.generate(node, name);
        return name;
    }

    private void generate(AstNode node, String name) {
        MethodVisitor method = this.writer.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                name, DESC, null, null);
        method.visitCode();
        this.emit(node, method);
        method.visitInsn(Opcodes.FRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private void emit(AstNode node, MethodVisitor method) {
        if (node instanceof RootNode root) this.call(method, root.next());
        else if (node instanceof ConstantNode constant) method.visitLdcInsn(constant.value());
        else if (node instanceof CoordinateNode coordinate) emitCoordinate(method, coordinate.axis());
        else if (node instanceof Memoized2DNode memoized) this.emitMemoized(method, memoized);
        else if (node instanceof SourceNode source) this.emitSource(method, source);
        else if (node instanceof DelegateNode delegate) this.emitDelegate(method, delegate);
        else if (node instanceof RangeChoiceNode range) this.emitRange(method, range);
        else if (node instanceof IntervalSelectNode interval) this.emitIntervalSelect(method, interval);
        else if (node instanceof YClampedGradientNode gradient) emitGradient(method, gradient);
        else if (node instanceof SplineNode spline) this.emitSpline(method, spline);
        else if (node instanceof LerpNode lerp) this.emitLerp(method, lerp);
        else if (node instanceof UnaryNode unary) this.emitUnary(method, unary);
        else if (node instanceof BinaryNode binary) this.binaries.emit(method, binary);
        else throw new UnsupportedOperationException("Unsupported column AST node " + node.getClass().getName());
    }

    private void emitUnary(MethodVisitor method, UnaryNode node) {
        this.call(method, node.operand());
        if (node instanceof NativeUnaryNode nativeUnary) emitNativeUnary(method, nativeUnary);
        else if (node instanceof AbsNode) invokeUnaryMath(method, "abs");
        else if (node instanceof NegNode) method.visitInsn(Opcodes.FNEG);
        else if (node instanceof SquareNode) {
            method.visitInsn(Opcodes.DUP);
            method.visitInsn(Opcodes.FMUL);
        } else if (node instanceof CubeNode) {
            method.visitInsn(Opcodes.DUP);
            method.visitInsn(Opcodes.DUP);
            method.visitInsn(Opcodes.FMUL);
            method.visitInsn(Opcodes.FMUL);
        } else if (node instanceof SqueezeNode) {
            method.visitMethodInsn(Opcodes.INVOKESTATIC, COLUMN_MATH, "squeeze", "(F)F", false);
        } else if (node instanceof NegMulNode negMul) {
            emitNegMul(method, negMul.multiplier());
        } else {
            throw new UnsupportedOperationException("Unsupported unary node " + node.getClass().getName());
        }
    }

    static void emitNativeUnary(MethodVisitor method, NativeUnaryNode node) {
        if (node.operation() == NativeUnaryOp.CLAMP) {
            method.visitLdcInsn(node.first());
            method.visitLdcInsn(node.second());
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/util/Mth", "clamp", "(FFF)F", false);
        } else if (node.operation() == NativeUnaryOp.SIGN) {
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "signum", "(F)F", false);
        } else {
            method.visitInsn(Opcodes.F2D);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math",
                    node.operation() == NativeUnaryOp.SQRT ? "sqrt" : "log", "(D)D", false);
            method.visitInsn(Opcodes.D2F);
        }
    }

    private void emitLerp(MethodVisitor method, LerpNode node) {
        if (!this.pointMode) {
            this.call(method, node.alpha());
            this.call(method, node.first());
            this.call(method, node.second());
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "com/moepus/byepregen/dfc/runtime/FloatMath", "lerp", "(FFF)F", false);
            return;
        }
        Label first = new Label(), second = new Label(), end = new Label();
        this.call(method, node.alpha());
        method.visitVarInsn(Opcodes.FSTORE, 5);
        method.visitVarInsn(Opcodes.FLOAD, 5);
        method.visitInsn(Opcodes.FCONST_0);
        method.visitInsn(Opcodes.FCMPL);
        method.visitJumpInsn(Opcodes.IFEQ, first);
        method.visitVarInsn(Opcodes.FLOAD, 5);
        method.visitInsn(Opcodes.FCONST_1);
        method.visitInsn(Opcodes.FCMPL);
        method.visitJumpInsn(Opcodes.IFEQ, second);
        method.visitVarInsn(Opcodes.FLOAD, 5);
        this.call(method, node.first());
        this.call(method, node.second());
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/util/Mth", "lerp", "(FFF)F", false);
        method.visitJumpInsn(Opcodes.GOTO, end);
        method.visitLabel(first);
        this.call(method, node.first());
        method.visitJumpInsn(Opcodes.GOTO, end);
        method.visitLabel(second);
        this.call(method, node.second());
        method.visitLabel(end);
    }

    private static void emitNegMul(MethodVisitor method, float multiplier) {
        Label positive = new Label();
        Label end = new Label();
        method.visitVarInsn(Opcodes.FSTORE, 5);
        method.visitVarInsn(Opcodes.FLOAD, 5);
        method.visitInsn(Opcodes.FCONST_0);
        method.visitInsn(Opcodes.FCMPL);
        method.visitJumpInsn(Opcodes.IFGT, positive);
        method.visitVarInsn(Opcodes.FLOAD, 5);
        method.visitLdcInsn(multiplier);
        method.visitInsn(Opcodes.FMUL);
        method.visitJumpInsn(Opcodes.GOTO, end);
        method.visitLabel(positive);
        method.visitVarInsn(Opcodes.FLOAD, 5);
        method.visitLabel(end);
    }

    private void emitRange(MethodVisitor method, RangeChoiceNode node) {
        Label outside = new Label();
        Label end = new Label();
        this.call(method, node.input());
        method.visitVarInsn(Opcodes.FSTORE, 5);
        method.visitVarInsn(Opcodes.FLOAD, 5);
        method.visitLdcInsn(node.minInclusive());
        method.visitInsn(Opcodes.FCMPL);
        method.visitJumpInsn(Opcodes.IFLT, outside);
        method.visitVarInsn(Opcodes.FLOAD, 5);
        method.visitLdcInsn(node.maxExclusive());
        method.visitInsn(Opcodes.FCMPG);
        method.visitJumpInsn(Opcodes.IFGE, outside);
        this.callOrInput(method, node.whenInRange(), node.input());
        method.visitJumpInsn(Opcodes.GOTO, end);
        method.visitLabel(outside);
        this.callOrInput(method, node.whenOutOfRange(), node.input());
        method.visitLabel(end);
    }

    private void callOrInput(MethodVisitor method, AstNode selected, AstNode input) {
        if (selected == input) method.visitVarInsn(Opcodes.FLOAD, 5);
        else this.call(method, selected);
    }

    private void emitIntervalSelect(MethodVisitor method, IntervalSelectNode node) {
        this.call(method, node.input());
        method.visitVarInsn(Opcodes.FSTORE, 5);
        Label[] branches = IntervalSelectEmitter.labels(node.branches().size());
        Label end = new Label();
        IntervalSelectEmitter.branch(method, node.thresholds(), 5, branches);
        for (int i = 0; i < branches.length; ++i) {
            method.visitLabel(branches[i]);
            this.callOrInput(method, node.branches().get(i), node.input());
            method.visitJumpInsn(Opcodes.GOTO, end);
        }
        method.visitLabel(end);
    }

    private void emitDelegate(MethodVisitor method, DelegateNode node) {
        FieldRef field = this.bindings.field(node.delegate(), DensitySampler.class);
        method.visitVarInsn(Opcodes.ALOAD, 4);
        BindingRegistry.loadField(method, this.owner, field);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "delegateValue",
                "(L" + DENSITY_FUNCTION + ";III)F", false);
    }

    private void emitSource(MethodVisitor method, SourceNode node) {
        this.emitDelegate(method, new DelegateNode(node.source(), false));
    }

    private void emitSpline(MethodVisitor method, SplineNode node) {
        this.splines.emitSample(node, method);
    }

    private void emitMemoized(MethodVisitor method, Memoized2DNode node) {
        Label cached = new Label();
        Label end = new Label();
        method.visitVarInsn(Opcodes.ALOAD, 4);
        ColumnClassBuilder.pushInt(method, node.slot());
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "memoizedValueMiss", "(I)Z", false);
        method.visitJumpInsn(Opcodes.IFEQ, cached);
        method.visitVarInsn(Opcodes.ALOAD, 4);
        ColumnClassBuilder.pushInt(method, node.slot());
        this.call(method, node.delegate());
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "setMemoizedValue", "(IF)F", false);
        method.visitJumpInsn(Opcodes.GOTO, end);
        method.visitLabel(cached);
        method.visitVarInsn(Opcodes.ALOAD, 4);
        ColumnClassBuilder.pushInt(method, node.slot());
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "memoizedValue", "(I)F", false);
        method.visitLabel(end);
    }

    private static void emitGradient(MethodVisitor method, YClampedGradientNode node) {
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitInsn(Opcodes.I2F);
        method.visitLdcInsn((float) node.fromY());
        method.visitLdcInsn((float) node.toY());
        method.visitLdcInsn(node.fromValue());
        method.visitLdcInsn(node.toValue());
        method.visitMethodInsn(Opcodes.INVOKESTATIC, COLUMN_MATH, "clampedMap", "(FFFFF)F", false);
    }

    private void call(MethodVisitor method, AstNode node) {
        if (node instanceof ConstantNode constant) {
            method.visitLdcInsn(constant.value());
            return;
        }
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitVarInsn(Opcodes.ALOAD, 4);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, this.owner, this.method(node), DESC, false);
    }

    private static void emitCoordinate(MethodVisitor method, Axis axis) {
        method.visitVarInsn(Opcodes.ILOAD, axis == Axis.X ? 1 : axis == Axis.Y ? 2 : 3);
        method.visitInsn(Opcodes.I2F);
    }

    private static void invokeUnaryMath(MethodVisitor method, String name) {
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", name, "(F)F", false);
    }

}
