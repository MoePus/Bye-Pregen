/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.codegen;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.*;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class PointBinaryEmitter {
    private final NodeCaller caller;
    private final boolean pointMode;

    PointBinaryEmitter(NodeCaller caller, boolean pointMode) {
        this.pointMode = pointMode;
        this.caller = caller;
    }

    void emit(MethodVisitor method, BinaryNode node) {
        if (node instanceof MinShortNode min) {
            if (min.left() instanceof ConstantNode value) {
                if (value.value() < min.rightMin()) {
                    this.caller.call(method, value);
                } else {
                    this.emitEager(method, new MinNode(min.left(), min.right()));
                }
                return;
            }
            this.emitShort(method, min, new ShortSpec(min.rightMin(), Opcodes.FCMPG, Opcodes.IFLT, "min"));
        } else if (node instanceof MaxShortNode max) {
            if (max.left() instanceof ConstantNode value) {
                if (value.value() > max.rightMax()) {
                    this.caller.call(method, value);
                } else {
                    this.emitEager(method, new MaxNode(max.left(), max.right()));
                }
                return;
            }
            this.emitShort(method, max, new ShortSpec(max.rightMax(), Opcodes.FCMPL, Opcodes.IFGT, "max"));
        } else if (this.pointMode && (node instanceof MulNode || node instanceof DivNode)
                && !(node.left() instanceof ConstantNode) && !(node.right() instanceof ConstantNode)) {
            this.emitZeroShort(method, node);
        } else {
            this.emitEager(method, node);
        }
    }

    private void emitEager(MethodVisitor method, BinaryNode node) {
        this.caller.call(method, node.left());
        if (node instanceof DivNode && node.right() instanceof ConstantNode constant) {
            method.visitLdcInsn(1.0F / constant.value());
            method.visitInsn(Opcodes.FMUL);
            return;
        }
        this.caller.call(method, node.right());
        if (node instanceof AddNode) method.visitInsn(Opcodes.FADD);
        else if (node instanceof SubNode) {
            if (this.pointMode) method.visitInsn(Opcodes.FSUB);
            else { method.visitInsn(Opcodes.FNEG); method.visitInsn(Opcodes.FADD); }
        }
        else if (node instanceof MulNode) method.visitInsn(Opcodes.FMUL);
        else if (node instanceof DivNode) method.visitInsn(Opcodes.FDIV);
        else if (node instanceof MinNode) invokeMath(method, "min");
        else if (node instanceof MaxNode) invokeMath(method, "max");
        else throw new UnsupportedOperationException("Unsupported binary node " + node.getClass().getName());
    }

    private void emitShort(MethodVisitor method, BinaryNode node, ShortSpec spec) {
        Label cached = new Label();
        Label end = new Label();
        this.caller.call(method, node.left());
        method.visitVarInsn(Opcodes.FSTORE, 5);
        method.visitVarInsn(Opcodes.FLOAD, 5);
        method.visitLdcInsn(spec.boundary());
        method.visitInsn(spec.compareOpcode());
        method.visitJumpInsn(spec.jumpOpcode(), cached);
        method.visitVarInsn(Opcodes.FLOAD, 5);
        this.caller.call(method, node.right());
        invokeMath(method, spec.operation());
        method.visitJumpInsn(Opcodes.GOTO, end);
        method.visitLabel(cached);
        method.visitVarInsn(Opcodes.FLOAD, 5);
        method.visitLabel(end);
    }

    private void invokeMath(MethodVisitor method, String name) {
        method.visitMethodInsn(Opcodes.INVOKESTATIC, this.pointMode ? "java/lang/Math"
                : "com/moepus/byepregen/dfc/runtime/FloatMath", this.pointMode ? name
                : name.equals("min") ? "volumeMin" : "volumeMax", "(FF)F", false);
    }

    private void emitZeroShort(MethodVisitor method, BinaryNode node) {
        Label zero = new Label(), end = new Label();
        this.caller.call(method, node.left());
        method.visitVarInsn(Opcodes.FSTORE, 5);
        method.visitVarInsn(Opcodes.FLOAD, 5);
        method.visitInsn(Opcodes.FCONST_0);
        method.visitInsn(Opcodes.FCMPL);
        method.visitJumpInsn(Opcodes.IFEQ, zero);
        method.visitVarInsn(Opcodes.FLOAD, 5);
        this.caller.call(method, node.right());
        method.visitInsn(node instanceof MulNode ? Opcodes.FMUL : Opcodes.FDIV);
        method.visitJumpInsn(Opcodes.GOTO, end);
        method.visitLabel(zero);
        method.visitInsn(Opcodes.FCONST_0);
        method.visitLabel(end);
    }

    interface NodeCaller {
        void call(MethodVisitor method, AstNode node);
    }

    private record ShortSpec(float boundary, int compareOpcode, int jumpOpcode, String operation) {
    }
}
