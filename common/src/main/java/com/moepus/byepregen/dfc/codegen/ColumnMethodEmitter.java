/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.codegen;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes;
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

/** Emits column-array helpers while retaining point helpers for conditional and terminal nodes. */
final class ColumnMethodEmitter {
    static final String DESC = Type.getMethodDescriptor(Type.VOID_TYPE,
            Type.getType(ColumnEvaluationContext.class), Type.getType(float[].class),
            Type.INT_TYPE, Type.INT_TYPE);
    private static final String CONTEXT = Type.getInternalName(ColumnEvaluationContext.class);
    private static final String COLUMN_MATH = Type.getInternalName(ColumnMath.class);
    private static final String DENSITY_FUNCTION = Type.getInternalName(DensitySampler.class);
    private static final String ARRAYS = "java/util/Arrays";

    private final String owner;
    private final ClassWriter writer;
    private final BindingRegistry bindings;
    private final PointMethodEmitter points;
    private final ColumnConditionalEmitter conditionals;
    private final Map<AstNode, String> methods = new IdentityHashMap<>();

    ColumnMethodEmitter(GenerationContext context, PointMethodEmitter points) {
        this.owner = context.owner();
        this.writer = context.writer();
        this.bindings = context.bindings();
        this.points = points;
        this.conditionals = new ColumnConditionalEmitter(this::call);
    }

    String method(AstNode node) {
        String existing = this.methods.get(node);
        if (existing != null) return existing;
        String name = "column" + this.methods.size() + "_" + node.getClass().getSimpleName();
        this.methods.put(node, name);
        this.generate(node, name);
        return name;
    }

    private void generate(AstNode node, String name) {
        MethodVisitor method = this.writer.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                name, DESC, null, null);
        method.visitCode();
        this.emit(node, method);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private void emit(AstNode node, MethodVisitor method) {
        if (FusedColumnEmitter.tryEmit(node, method, this::call)) return;
        if (node instanceof RootNode root) this.call(method, root.next(), 2, 3, 4);
        else if (node instanceof ConstantNode constant) emitFill(method, constant.value());
        else if (node instanceof CoordinateNode coordinate) this.emitCoordinate(method, coordinate.axis());
        else if (node instanceof YClampedGradientNode gradient) this.emitGradient(method, gradient);
        else if (node instanceof Memoized2DNode memoized) this.emitMemoized(method, memoized);
        else if (node instanceof SourceNode source) {
            this.emitInterpolated(method, source);
        } else if (node instanceof DelegateNode delegate) {
            this.emitDelegate(method, delegate);
        } else if (node instanceof RangeChoiceNode range) {
            this.conditionals.emitRangeChoice(method, range);
        } else if (node instanceof IntervalSelectNode interval) {
            this.conditionals.emitIntervalSelect(method, interval);
        } else if (node instanceof MinShortNode min) {
            this.conditionals.emitShortBinary(method, min, min.rightMin(), false);
        } else if (node instanceof MaxShortNode max) {
            this.conditionals.emitShortBinary(method, max, max.rightMax(), true);
        } else if (node instanceof UnaryNode unary && supportsColumnUnary(unary)) {
            this.call(method, unary.operand(), 2, 3, 4);
            this.emitUnaryLoop(method, unary);
        } else if (node instanceof BinaryNode binary) {
            this.emitBinary(method, binary);
        } else {
            this.emitPointLoop(method, node);
        }
    }

    private void emitCoordinate(MethodVisitor method, Axis axis) {
        if (axis != Axis.Y) {
            method.visitVarInsn(Opcodes.ALOAD, 2);
            method.visitVarInsn(Opcodes.ILOAD, 3);
            method.visitVarInsn(Opcodes.ILOAD, 4);
            method.visitVarInsn(Opcodes.ALOAD, 1);
            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT,
                    axis == Axis.X ? "x" : "z", "()I", false);
            method.visitInsn(Opcodes.I2F);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAYS, "fill", "([FIIF)V", false);
            return;
        }
        loadContextInt(method, "minY", 5);
        loadContextInt(method, "cellHeight", 6);
        Loop loop = emitLoopStart(method, 7, 3, 4);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 7);
        method.visitVarInsn(Opcodes.ILOAD, 5);
        method.visitVarInsn(Opcodes.ILOAD, 7);
        method.visitVarInsn(Opcodes.ILOAD, 6);
        method.visitInsn(Opcodes.IMUL);
        method.visitInsn(Opcodes.IADD);
        method.visitInsn(Opcodes.I2F);
        method.visitInsn(Opcodes.FASTORE);
        emitLoopEnd(method, 7, loop);
    }

    private void emitMemoized(MethodVisitor method, Memoized2DNode node) {
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitVarInsn(Opcodes.ILOAD, 4);
        this.loadPointInvocation(method, node);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAYS, "fill", "([FIIF)V", false);
    }

    private void emitInterpolated(MethodVisitor method, SourceNode node) {
        this.emitDelegate(method, new DelegateNode(node.source(), false));
    }

    private void emitDelegate(MethodVisitor method, DelegateNode node) {
        FieldRef field = this.bindings.field(node.delegate(), DensitySampler.class);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        BindingRegistry.loadField(method, this.owner, field);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitVarInsn(Opcodes.ILOAD, 4);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "copySourceColumn",
                "(L" + DENSITY_FUNCTION + ";[FII)V", false);
    }

    private void emitGradient(MethodVisitor method, YClampedGradientNode node) {
        loadContextInt(method, "minY", 5);
        loadContextInt(method, "cellHeight", 6);
        Loop loop = emitLoopStart(method, 7, 3, 4);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 7);
        method.visitVarInsn(Opcodes.ILOAD, 5);
        method.visitVarInsn(Opcodes.ILOAD, 7);
        method.visitVarInsn(Opcodes.ILOAD, 6);
        method.visitInsn(Opcodes.IMUL);
        method.visitInsn(Opcodes.IADD);
        method.visitInsn(Opcodes.I2F);
        method.visitLdcInsn((float) node.fromY());
        method.visitLdcInsn((float) node.toY());
        method.visitLdcInsn(node.fromValue());
        method.visitLdcInsn(node.toValue());
        method.visitMethodInsn(Opcodes.INVOKESTATIC, COLUMN_MATH, "clampedMap",
                "(FFFFF)F", false);
        method.visitInsn(Opcodes.FASTORE);
        emitLoopEnd(method, 7, loop);
    }

    private void emitUnaryLoop(MethodVisitor method, UnaryNode node) {
        Loop loop = emitLoopStart(method, 5, 3, 4);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 5);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 5);
        method.visitInsn(Opcodes.FALOAD);
        emitUnaryOperation(method, node);
        method.visitInsn(Opcodes.FASTORE);
        emitLoopEnd(method, 5, loop);
    }

    private void emitBinary(MethodVisitor method, BinaryNode node) {
        if (node.left() instanceof ConstantNode left
                && node.right() instanceof ConstantNode right) {
            emitFill(method, AstNodes.foldBinary(node, left.value(), right.value()));
            return;
        }
        if (node.left() instanceof ConstantNode left) {
            this.call(method, node.right(), 2, 3, 4);
            this.emitConstantBinaryLoop(method, node, left.value(), true);
            return;
        }
        if (node.right() instanceof ConstantNode right) {
            this.call(method, node.left(), 2, 3, 4);
            this.emitConstantBinaryLoop(method, node, right.value(), false);
            return;
        }
        this.call(method, node.left(), 2, 3, 4);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitInsn(Opcodes.ARRAYLENGTH);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "borrowFloatArray", "(I)[F", false);
        method.visitVarInsn(Opcodes.ASTORE, 5);
        this.call(method, node.right(), 5, 3, 4);
        this.emitBinaryLoop(method, node, 5, 3, 4);
        recycleScratch(method, 5);
    }

    private void emitConstantBinaryLoop(MethodVisitor method, BinaryNode node,
                                        float constant, boolean constantOnLeft) {
        boolean multiplyByReciprocal = node instanceof DivNode && !constantOnLeft;
        Loop loop = emitLoopStart(method, 5, 3, 4);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 5);
        if (constantOnLeft) {
            method.visitLdcInsn(constant);
            loadArrayValue(method, 2, 5);
        } else {
            loadArrayValue(method, 2, 5);
            method.visitLdcInsn(multiplyByReciprocal ? 1.0F / constant : constant);
        }
        if (multiplyByReciprocal) method.visitInsn(Opcodes.FMUL);
        else emitBinaryOperation(method, node);
        method.visitInsn(Opcodes.FASTORE);
        emitLoopEnd(method, 5, loop);
    }

    private void emitBinaryLoop(MethodVisitor method, BinaryNode node, int scratch, int from, int to) {
        Loop loop = emitLoopStart(method, 6, from, to);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 6);
        loadArrayValue(method, 2, 6);
        loadArrayValue(method, scratch, 6);
        emitBinaryOperation(method, node);
        method.visitInsn(Opcodes.FASTORE);
        emitLoopEnd(method, 6, loop);
    }

    private void emitPointLoop(MethodVisitor method, AstNode node) {
        loadContextInt(method, "x", 5);
        loadContextInt(method, "z", 6);
        loadContextInt(method, "minY", 7);
        loadContextInt(method, "cellHeight", 8);
        Loop loop = emitLoopStart(method, 9, 3, 4);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 9);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ILOAD, 5);
        method.visitVarInsn(Opcodes.ILOAD, 7);
        method.visitVarInsn(Opcodes.ILOAD, 9);
        method.visitVarInsn(Opcodes.ILOAD, 8);
        method.visitInsn(Opcodes.IMUL);
        method.visitInsn(Opcodes.IADD);
        method.visitVarInsn(Opcodes.ILOAD, 6);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, this.owner,
                this.points.method(node), PointMethodEmitter.DESC, false);
        method.visitInsn(Opcodes.FASTORE);
        emitLoopEnd(method, 9, loop);
    }

    private void loadPointInvocation(MethodVisitor method, AstNode node) {
        method.visitVarInsn(Opcodes.ALOAD, 0);
        loadContextIntValue(method, "x");
        loadContextIntValue(method, "minY");
        loadContextIntValue(method, "z");
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, this.owner,
                this.points.method(node), PointMethodEmitter.DESC, false);
    }

    private void call(MethodVisitor method, AstNode node, int outputLocal, int fromLocal, int toLocal) {
        if (node instanceof ConstantNode constant) {
            emitFill(method, outputLocal, constant.value(), fromLocal, toLocal);
            return;
        }
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, outputLocal);
        method.visitVarInsn(Opcodes.ILOAD, fromLocal);
        method.visitVarInsn(Opcodes.ILOAD, toLocal);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, this.owner, this.method(node), DESC, false);
    }

    private static void emitFill(MethodVisitor method, float value) {
        emitFill(method, 2, value, 3, 4);
    }

    private static void emitFill(MethodVisitor method, int outputLocal, float value,
                                 int fromLocal, int toLocal) {
        method.visitVarInsn(Opcodes.ALOAD, outputLocal);
        method.visitVarInsn(Opcodes.ILOAD, fromLocal);
        method.visitVarInsn(Opcodes.ILOAD, toLocal);
        method.visitLdcInsn(value);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAYS, "fill", "([FIIF)V", false);
    }

    static void emitUnaryOperation(MethodVisitor method, UnaryNode node) {
        if (node instanceof NativeUnaryNode nativeUnary) PointMethodEmitter.emitNativeUnary(method, nativeUnary);
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
            throw new IllegalArgumentException("Unsupported unary column node " + node.getClass().getName());
        }
    }

    static boolean supportsColumnUnary(UnaryNode node) {
        return node instanceof NativeUnaryNode || node instanceof AbsNode || node instanceof NegNode
                || node instanceof SquareNode || node instanceof CubeNode
                || node instanceof SqueezeNode || node instanceof NegMulNode;
    }

    private static void emitNegMul(MethodVisitor method, float multiplier) {
        Label positive = new Label();
        Label end = new Label();
        method.visitVarInsn(Opcodes.FSTORE, 40);
        method.visitVarInsn(Opcodes.FLOAD, 40);
        method.visitInsn(Opcodes.FCONST_0);
        method.visitInsn(Opcodes.FCMPL);
        method.visitJumpInsn(Opcodes.IFGT, positive);
        method.visitVarInsn(Opcodes.FLOAD, 40);
        method.visitLdcInsn(multiplier);
        method.visitInsn(Opcodes.FMUL);
        method.visitJumpInsn(Opcodes.GOTO, end);
        method.visitLabel(positive);
        method.visitVarInsn(Opcodes.FLOAD, 40);
        method.visitLabel(end);
    }

    static void emitBinaryOperation(MethodVisitor method, BinaryNode node) {
        if (node instanceof AddNode) method.visitInsn(Opcodes.FADD);
        else if (node instanceof SubNode) { method.visitInsn(Opcodes.FNEG); method.visitInsn(Opcodes.FADD); }
        else if (node instanceof MulNode) method.visitInsn(Opcodes.FMUL);
        else if (node instanceof DivNode) method.visitInsn(Opcodes.FDIV);
        else if (node instanceof MinNode) invokeBinaryMath(method, "min");
        else if (node instanceof MaxNode) invokeBinaryMath(method, "max");
        else throw new IllegalArgumentException("Unsupported binary column node " + node.getClass().getName());
    }

    private static Loop emitLoopStart(MethodVisitor method, int indexLocal, int fromLocal, int toLocal) {
        Label loop = new Label();
        Label end = new Label();
        method.visitVarInsn(Opcodes.ILOAD, fromLocal);
        method.visitVarInsn(Opcodes.ISTORE, indexLocal);
        method.visitLabel(loop);
        method.visitVarInsn(Opcodes.ILOAD, indexLocal);
        method.visitVarInsn(Opcodes.ILOAD, toLocal);
        method.visitJumpInsn(Opcodes.IF_ICMPGE, end);
        return new Loop(loop, end);
    }

    private static void emitLoopEnd(MethodVisitor method, int indexLocal, Loop loop) {
        method.visitIincInsn(indexLocal, 1);
        method.visitJumpInsn(Opcodes.GOTO, loop.start());
        method.visitLabel(loop.end());
    }

    private static void loadArrayValue(MethodVisitor method, int arrayLocal, int indexLocal) {
        method.visitVarInsn(Opcodes.ALOAD, arrayLocal);
        method.visitVarInsn(Opcodes.ILOAD, indexLocal);
        method.visitInsn(Opcodes.FALOAD);
    }

    static void recycleScratch(MethodVisitor method, int local) {
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, local);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "recycleFloatArray", "([F)V", false);
    }

    private static void loadContextInt(MethodVisitor method, String name, int local) {
        loadContextIntValue(method, name);
        method.visitVarInsn(Opcodes.ISTORE, local);
    }

    private static void loadContextIntValue(MethodVisitor method, String name) {
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, name, "()I", false);
    }

    private static void invokeUnaryMath(MethodVisitor method, String name) {
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", name, "(F)F", false);
    }

    private static void invokeBinaryMath(MethodVisitor method, String name) {
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "com/moepus/byepregen/dfc/runtime/FloatMath", name.equals("min") ? "volumeMin" : "volumeMax", "(FF)F", false);
    }

    private record Loop(Label start, Label end) {
    }
}
