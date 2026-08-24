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

    PointBinaryEmitter(NodeCaller caller) {
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
            this.emitShort(method, min, new ShortSpec(min.rightMin(), Opcodes.DCMPG, Opcodes.IFLT, "min"));
        } else if (node instanceof MaxShortNode max) {
            if (max.left() instanceof ConstantNode value) {
                if (value.value() > max.rightMax()) {
                    this.caller.call(method, value);
                } else {
                    this.emitEager(method, new MaxNode(max.left(), max.right()));
                }
                return;
            }
            this.emitShort(method, max, new ShortSpec(max.rightMax(), Opcodes.DCMPL, Opcodes.IFGT, "max"));
        } else {
            this.emitEager(method, node);
        }
    }

    private void emitEager(MethodVisitor method, BinaryNode node) {
        this.caller.call(method, node.left());
        this.caller.call(method, node.right());
        if (node instanceof AddNode) method.visitInsn(Opcodes.DADD);
        else if (node instanceof MulNode) method.visitInsn(Opcodes.DMUL);
        else if (node instanceof DivNode) method.visitInsn(Opcodes.DDIV);
        else if (node instanceof MinNode) invokeMath(method, "min");
        else if (node instanceof MaxNode) invokeMath(method, "max");
        else throw new UnsupportedOperationException("Unsupported binary node " + node.getClass().getName());
    }

    private void emitShort(MethodVisitor method, BinaryNode node, ShortSpec spec) {
        Label cached = new Label();
        Label end = new Label();
        this.caller.call(method, node.left());
        method.visitVarInsn(Opcodes.DSTORE, 5);
        method.visitVarInsn(Opcodes.DLOAD, 5);
        method.visitLdcInsn(spec.boundary());
        method.visitInsn(spec.compareOpcode());
        method.visitJumpInsn(spec.jumpOpcode(), cached);
        method.visitVarInsn(Opcodes.DLOAD, 5);
        this.caller.call(method, node.right());
        invokeMath(method, spec.operation());
        method.visitJumpInsn(Opcodes.GOTO, end);
        method.visitLabel(cached);
        method.visitVarInsn(Opcodes.DLOAD, 5);
        method.visitLabel(end);
    }

    private static void invokeMath(MethodVisitor method, String name) {
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", name, "(DD)D", false);
    }

    interface NodeCaller {
        void call(MethodVisitor method, AstNode node);
    }

    private record ShortSpec(double boundary, int compareOpcode, int jumpOpcode, String operation) {
    }
}
