/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.codegen;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.BinaryNode;
import com.moepus.byepregen.dfc.ast.AstNodes.ConstantNode;
import com.moepus.byepregen.dfc.ast.AstNodes.MaxNode;
import com.moepus.byepregen.dfc.ast.AstNodes.MinNode;
import com.moepus.byepregen.dfc.ast.AstNodes.RangeChoiceNode;
import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Emits lazy control nodes while preserving column evaluation inside contiguous lane runs. */
final class ColumnConditionalEmitter {
    private static final String CONTEXT = Type.getInternalName(ColumnEvaluationContext.class);
    private final ColumnCaller caller;

    ColumnConditionalEmitter(ColumnCaller caller) {
        this.caller = caller;
    }

    void emitShortBinary(MethodVisitor method, BinaryNode node, double boundary, boolean max) {
        if (node.left() instanceof ConstantNode constant) {
            if (max ? constant.value() > boundary : constant.value() < boundary) {
                this.caller.call(method, constant, 2, 3, 4);
            } else {
                AstNode eager = max
                        ? new MaxNode(node.left(), node.right())
                        : new MinNode(node.left(), node.right());
                this.caller.call(method, eager, 2, 3, 4);
            }
            return;
        }
        this.caller.call(method, node.left(), 2, 3, 4);
        borrowScratch(method, 5);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitVarInsn(Opcodes.ISTORE, 6);
        Label scan = new Label();
        Label shorted = new Label();
        Label active = new Label();
        Label activeDone = new Label();
        Label done = new Label();
        method.visitLabel(scan);
        jumpIfAtEnd(method, 6, done);
        emitShortedJump(method, 6, boundary, max, shorted);
        method.visitVarInsn(Opcodes.ILOAD, 6);
        method.visitVarInsn(Opcodes.ISTORE, 7);
        method.visitLabel(active);
        jumpIfAtEnd(method, 6, activeDone);
        emitShortedJump(method, 6, boundary, max, activeDone);
        method.visitIincInsn(6, 1);
        method.visitJumpInsn(Opcodes.GOTO, active);
        method.visitLabel(activeDone);
        this.caller.call(method, node.right(), 5, 7, 6);
        emitMergeLoop(method, 5, 7, 6, max ? "max" : "min");
        method.visitJumpInsn(Opcodes.GOTO, scan);
        method.visitLabel(shorted);
        method.visitIincInsn(6, 1);
        method.visitJumpInsn(Opcodes.GOTO, scan);
        method.visitLabel(done);
        recycleScratch(method, 5);
    }

    void emitRangeChoice(MethodVisitor method, RangeChoiceNode node) {
        this.caller.call(method, node.input(), 2, 3, 4);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitVarInsn(Opcodes.ISTORE, 5);
        Label scan = new Label();
        Label beginOut = new Label();
        Label scanIn = new Label();
        Label finishIn = new Label();
        Label scanOut = new Label();
        Label finishOut = new Label();
        Label done = new Label();
        method.visitLabel(scan);
        jumpIfAtEnd(method, 5, done);
        emitOutsideJump(method, 5, node, beginOut);
        storeRunStart(method);
        method.visitLabel(scanIn);
        jumpIfAtEnd(method, 5, finishIn);
        emitOutsideJump(method, 5, node, finishIn);
        incrementAndJump(method, 5, scanIn);
        method.visitLabel(finishIn);
        this.callUnlessInput(method, node.whenInRange(), node.input());
        method.visitJumpInsn(Opcodes.GOTO, scan);
        method.visitLabel(beginOut);
        storeRunStart(method);
        method.visitLabel(scanOut);
        jumpIfAtEnd(method, 5, finishOut);
        emitInsideJump(method, 5, node, finishOut);
        incrementAndJump(method, 5, scanOut);
        method.visitLabel(finishOut);
        this.callUnlessInput(method, node.whenOutOfRange(), node.input());
        method.visitJumpInsn(Opcodes.GOTO, scan);
        method.visitLabel(done);
    }

    private void callUnlessInput(MethodVisitor method, AstNode child, AstNode input) {
        if (child != input) this.caller.call(method, child, 2, 6, 5);
    }

    private static void emitMergeLoop(MethodVisitor method, int scratch, int from, int to,
                                      String operation) {
        method.visitVarInsn(Opcodes.ILOAD, from);
        method.visitVarInsn(Opcodes.ISTORE, 8);
        Label loop = new Label();
        Label done = new Label();
        method.visitLabel(loop);
        method.visitVarInsn(Opcodes.ILOAD, 8);
        method.visitVarInsn(Opcodes.ILOAD, to);
        method.visitJumpInsn(Opcodes.IF_ICMPGE, done);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 8);
        loadArrayValue(method, 2, 8);
        loadArrayValue(method, scratch, 8);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", operation, "(DD)D", false);
        method.visitInsn(Opcodes.DASTORE);
        incrementAndJump(method, 8, loop);
        method.visitLabel(done);
    }

    private static void emitShortedJump(MethodVisitor method, int index, double boundary,
                                        boolean max, Label shorted) {
        loadArrayValue(method, 2, index);
        method.visitLdcInsn(boundary);
        method.visitInsn(max ? Opcodes.DCMPL : Opcodes.DCMPG);
        method.visitJumpInsn(max ? Opcodes.IFGT : Opcodes.IFLT, shorted);
    }

    private static void emitOutsideJump(MethodVisitor method, int index,
                                        RangeChoiceNode node, Label outside) {
        loadArrayValue(method, 2, index);
        method.visitLdcInsn(node.minInclusive());
        method.visitInsn(Opcodes.DCMPL);
        method.visitJumpInsn(Opcodes.IFLT, outside);
        loadArrayValue(method, 2, index);
        method.visitLdcInsn(node.maxExclusive());
        method.visitInsn(Opcodes.DCMPG);
        method.visitJumpInsn(Opcodes.IFGE, outside);
    }

    private static void emitInsideJump(MethodVisitor method, int index,
                                       RangeChoiceNode node, Label inside) {
        Label outside = new Label();
        loadArrayValue(method, 2, index);
        method.visitLdcInsn(node.minInclusive());
        method.visitInsn(Opcodes.DCMPL);
        method.visitJumpInsn(Opcodes.IFLT, outside);
        loadArrayValue(method, 2, index);
        method.visitLdcInsn(node.maxExclusive());
        method.visitInsn(Opcodes.DCMPG);
        method.visitJumpInsn(Opcodes.IFLT, inside);
        method.visitLabel(outside);
    }

    private static void borrowScratch(MethodVisitor method, int local) {
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitInsn(Opcodes.ARRAYLENGTH);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "borrowDoubleArray", "(I)[D", false);
        method.visitVarInsn(Opcodes.ASTORE, local);
    }

    private static void recycleScratch(MethodVisitor method, int local) {
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, local);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "recycleDoubleArray", "([D)V", false);
    }

    private static void jumpIfAtEnd(MethodVisitor method, int index, Label end) {
        method.visitVarInsn(Opcodes.ILOAD, index);
        method.visitVarInsn(Opcodes.ILOAD, 4);
        method.visitJumpInsn(Opcodes.IF_ICMPGE, end);
    }

    private static void storeRunStart(MethodVisitor method) {
        method.visitVarInsn(Opcodes.ILOAD, 5);
        method.visitVarInsn(Opcodes.ISTORE, 6);
    }

    private static void incrementAndJump(MethodVisitor method, int index, Label target) {
        method.visitIincInsn(index, 1);
        method.visitJumpInsn(Opcodes.GOTO, target);
    }

    private static void loadArrayValue(MethodVisitor method, int array, int index) {
        method.visitVarInsn(Opcodes.ALOAD, array);
        method.visitVarInsn(Opcodes.ILOAD, index);
        method.visitInsn(Opcodes.DALOAD);
    }

    @FunctionalInterface
    interface ColumnCaller {
        void call(MethodVisitor method, AstNode node, int outputLocal, int fromLocal, int toLocal);
    }
}
