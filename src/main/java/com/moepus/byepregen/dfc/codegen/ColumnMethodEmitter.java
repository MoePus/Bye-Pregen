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
import net.minecraft.world.level.levelgen.DensityFunction;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Emits column-array helpers while retaining point helpers for conditional and terminal nodes. */
final class ColumnMethodEmitter {
    static final String DESC = Type.getMethodDescriptor(Type.VOID_TYPE,
            Type.getType(ColumnEvaluationContext.class), Type.getType(double[].class));
    private static final String CONTEXT = Type.getInternalName(ColumnEvaluationContext.class);
    private static final String COLUMN_MATH = Type.getInternalName(ColumnMath.class);
    private static final String DENSITY_FUNCTION = Type.getInternalName(DensityFunction.class);
    private static final String ARRAYS = "java/util/Arrays";

    private final String owner;
    private final ClassWriter writer;
    private final BindingRegistry bindings;
    private final PointMethodEmitter points;
    private final Map<AstNode, String> methods = new IdentityHashMap<>();

    ColumnMethodEmitter(
            String owner,
            ClassWriter writer,
            BindingRegistry bindings,
            PointMethodEmitter points
    ) {
        this.owner = owner;
        this.writer = writer;
        this.bindings = bindings;
        this.points = points;
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
        if (node instanceof RootNode root) this.call(method, root.next(), 2);
        else if (node instanceof ConstantNode constant) emitFill(method, constant.value());
        else if (node instanceof CoordinateNode coordinate) this.emitCoordinate(method, coordinate.axis());
        else if (node instanceof Memoized2DNode memoized) this.emitMemoized(method, memoized);
        else if (node instanceof SourceNode source && source.mode() == SourceMode.INTERPOLATED) {
            this.emitInterpolated(method, source);
        } else if (node instanceof UnaryNode unary) {
            this.call(method, unary.operand(), 2);
            this.emitUnaryLoop(method, unary);
        } else if (node instanceof BinaryNode binary && isEagerBinary(binary)) {
            this.emitBinary(method, binary);
        } else {
            this.emitPointLoop(method, node);
        }
    }

    private void emitCoordinate(MethodVisitor method, Axis axis) {
        if (axis != Axis.Y) {
            method.visitVarInsn(Opcodes.ALOAD, 2);
            method.visitVarInsn(Opcodes.ALOAD, 1);
            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT,
                    axis == Axis.X ? "x" : "z", "()I", false);
            method.visitInsn(Opcodes.I2D);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAYS, "fill", "([DD)V", false);
            return;
        }
        loadContextInt(method, "minY", 4);
        loadContextInt(method, "cellHeight", 5);
        Loop loop = emitLoopStart(method, 3);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitVarInsn(Opcodes.ILOAD, 4);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitVarInsn(Opcodes.ILOAD, 5);
        method.visitInsn(Opcodes.IMUL);
        method.visitInsn(Opcodes.IADD);
        method.visitInsn(Opcodes.I2D);
        method.visitInsn(Opcodes.DASTORE);
        emitLoopEnd(method, 3, loop);
    }

    private void emitMemoized(MethodVisitor method, Memoized2DNode node) {
        method.visitVarInsn(Opcodes.ALOAD, 2);
        this.loadPointInvocation(method, node);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAYS, "fill", "([DD)V", false);
    }

    private void emitInterpolated(MethodVisitor method, SourceNode node) {
        int slot = this.points.interpolationSlot(node.source());
        this.points.ensureInterpolationToken(node.source());
        FieldRef field = this.bindings.interpolatedField(node.source(), slot);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        ColumnClassBuilder.pushInt(method, slot);
        this.loadField(method, field);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "copyInterpolatedColumn",
                "(IL" + DENSITY_FUNCTION + ";[D)V", false);
    }

    private void emitUnaryLoop(MethodVisitor method, UnaryNode node) {
        Loop loop = emitLoopStart(method, 3);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitInsn(Opcodes.DALOAD);
        emitUnaryOperation(method, node);
        method.visitInsn(Opcodes.DASTORE);
        emitLoopEnd(method, 3, loop);
    }

    private void emitBinary(MethodVisitor method, BinaryNode node) {
        this.call(method, node.left(), 2);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitInsn(Opcodes.ARRAYLENGTH);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "borrowDoubleArray", "(I)[D", false);
        method.visitVarInsn(Opcodes.ASTORE, 3);
        Label body = new Label();
        Label cleanup = new Label();
        Label failure = new Label();
        Label done = new Label();
        method.visitTryCatchBlock(body, cleanup, failure, "java/lang/Throwable");
        method.visitLabel(body);
        this.call(method, node.right(), 3);
        this.emitBinaryLoop(method, node);
        method.visitLabel(cleanup);
        recycleScratch(method);
        method.visitJumpInsn(Opcodes.GOTO, done);
        method.visitLabel(failure);
        method.visitVarInsn(Opcodes.ASTORE, 5);
        recycleScratch(method);
        method.visitVarInsn(Opcodes.ALOAD, 5);
        method.visitInsn(Opcodes.ATHROW);
        method.visitLabel(done);
    }

    private void emitBinaryLoop(MethodVisitor method, BinaryNode node) {
        Loop loop = emitLoopStart(method, 4);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 4);
        loadArrayValue(method, 2, 4);
        loadArrayValue(method, 3, 4);
        emitBinaryOperation(method, node);
        method.visitInsn(Opcodes.DASTORE);
        emitLoopEnd(method, 4, loop);
    }

    private void emitPointLoop(MethodVisitor method, AstNode node) {
        loadContextInt(method, "x", 4);
        loadContextInt(method, "z", 5);
        loadContextInt(method, "minY", 6);
        loadContextInt(method, "cellHeight", 7);
        Loop loop = emitLoopStart(method, 3);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ILOAD, 4);
        method.visitVarInsn(Opcodes.ILOAD, 6);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitVarInsn(Opcodes.ILOAD, 7);
        method.visitInsn(Opcodes.IMUL);
        method.visitInsn(Opcodes.IADD);
        method.visitVarInsn(Opcodes.ILOAD, 5);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, this.owner,
                this.points.method(node), PointMethodEmitter.DESC, false);
        method.visitInsn(Opcodes.DASTORE);
        emitLoopEnd(method, 3, loop);
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

    private void call(MethodVisitor method, AstNode node, int outputLocal) {
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, outputLocal);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, this.owner, this.method(node), DESC, false);
    }

    private void loadField(MethodVisitor method, FieldRef field) {
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, this.owner, field.name(), Type.getDescriptor(field.type()));
    }

    private static boolean isEagerBinary(BinaryNode node) {
        return !(node instanceof MinShortNode || node instanceof MaxShortNode);
    }

    private static void emitFill(MethodVisitor method, double value) {
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitLdcInsn(value);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAYS, "fill", "([DD)V", false);
    }

    private static void emitUnaryOperation(MethodVisitor method, UnaryNode node) {
        if (node instanceof AbsNode) invokeUnaryMath(method, "abs");
        else if (node instanceof NegNode) method.visitInsn(Opcodes.DNEG);
        else if (node instanceof SquareNode) {
            method.visitInsn(Opcodes.DUP2);
            method.visitInsn(Opcodes.DMUL);
        } else if (node instanceof CubeNode) {
            method.visitInsn(Opcodes.DUP2);
            method.visitInsn(Opcodes.DUP2);
            method.visitInsn(Opcodes.DMUL);
            method.visitInsn(Opcodes.DMUL);
        } else if (node instanceof SqueezeNode) {
            method.visitMethodInsn(Opcodes.INVOKESTATIC, COLUMN_MATH, "squeeze", "(D)D", false);
        } else if (node instanceof NegMulNode negMul) {
            emitNegMul(method, negMul.multiplier());
        } else {
            throw new IllegalArgumentException("Unsupported unary column node " + node.getClass().getName());
        }
    }

    private static void emitNegMul(MethodVisitor method, double multiplier) {
        Label positive = new Label();
        Label end = new Label();
        method.visitVarInsn(Opcodes.DSTORE, 4);
        method.visitVarInsn(Opcodes.DLOAD, 4);
        method.visitInsn(Opcodes.DCONST_0);
        method.visitInsn(Opcodes.DCMPL);
        method.visitJumpInsn(Opcodes.IFGT, positive);
        method.visitVarInsn(Opcodes.DLOAD, 4);
        method.visitLdcInsn(multiplier);
        method.visitInsn(Opcodes.DMUL);
        method.visitJumpInsn(Opcodes.GOTO, end);
        method.visitLabel(positive);
        method.visitVarInsn(Opcodes.DLOAD, 4);
        method.visitLabel(end);
    }

    private static void emitBinaryOperation(MethodVisitor method, BinaryNode node) {
        if (node instanceof AddNode) method.visitInsn(Opcodes.DADD);
        else if (node instanceof MulNode) method.visitInsn(Opcodes.DMUL);
        else if (node instanceof DivNode) method.visitInsn(Opcodes.DDIV);
        else if (node instanceof MinNode) invokeBinaryMath(method, "min");
        else if (node instanceof MaxNode) invokeBinaryMath(method, "max");
        else throw new IllegalArgumentException("Unsupported binary column node " + node.getClass().getName());
    }

    private static Loop emitLoopStart(MethodVisitor method, int indexLocal) {
        Label loop = new Label();
        Label end = new Label();
        method.visitInsn(Opcodes.ICONST_0);
        method.visitVarInsn(Opcodes.ISTORE, indexLocal);
        method.visitLabel(loop);
        method.visitVarInsn(Opcodes.ILOAD, indexLocal);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitInsn(Opcodes.ARRAYLENGTH);
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
        method.visitInsn(Opcodes.DALOAD);
    }

    private static void recycleScratch(MethodVisitor method) {
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 3);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "recycleDoubleArray", "([D)V", false);
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
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", name, "(D)D", false);
    }

    private static void invokeBinaryMath(MethodVisitor method, String name) {
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", name, "(DD)D", false);
    }

    private record Loop(Label start, Label end) {
    }
}
