package com.moepus.byepregen.dfc.codegen;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.*;
import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import java.util.*;
import org.objectweb.asm.*;

/** Retains the 26.3 single arithmetic loop inside the transplanted column dispatch. */
final class FusedColumnEmitter {
    private static final int MIN_OPERATIONS = 2;
    private static final int MAX_OPERATIONS = 96;
    private static final int MAX_INPUTS = 8;
    private static final int FIRST_INPUT_LOCAL = 5;
    private static final int INDEX_LOCAL = 16;
    private static final int FIRST_VALUE_LOCAL = 64;
    private static final String CONTEXT = Type.getInternalName(ColumnEvaluationContext.class);
    private final List<AstNode> inputs = new ArrayList<>();
    private final Map<AstNode, Integer> inputIndices = new IdentityHashMap<>();
    private final Set<AstNode> operations = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<AstNode, Integer> values = new IdentityHashMap<>();
    private final ColumnConditionalEmitter.ColumnCaller caller;

    private FusedColumnEmitter(ColumnConditionalEmitter.ColumnCaller caller) { this.caller = caller; }

    static boolean tryEmit(AstNode root, MethodVisitor method, ColumnConditionalEmitter.ColumnCaller caller) {
        if (!arithmetic(root)) return false;
        FusedColumnEmitter emitter = new FusedColumnEmitter(caller);
        emitter.collect(root);
        if (emitter.operations.size() < MIN_OPERATIONS || emitter.operations.size() > MAX_OPERATIONS
                || emitter.inputs.isEmpty() || emitter.inputs.size() > MAX_INPUTS) return false;
        emitter.emit(root, method);
        return true;
    }

    private void collect(AstNode node) {
        if (node instanceof ConstantNode) return;
        if (!arithmetic(node)) {
            if (!this.inputIndices.containsKey(node)) {
                this.inputIndices.put(node, this.inputs.size());
                this.inputs.add(node);
            }
            return;
        }
        if (this.operations.add(node)) for (AstNode child : node.children()) this.collect(child);
    }

    private static boolean arithmetic(AstNode node) {
        return node instanceof AddNode || node instanceof SubNode || node instanceof MulNode
                || node instanceof DivNode || node instanceof MinNode || node instanceof MaxNode
                || node instanceof LerpNode || node instanceof UnaryNode unary
                && ColumnMethodEmitter.supportsColumnUnary(unary);
    }

    private void emit(AstNode root, MethodVisitor method) {
        for (int i = 0; i < this.inputs.size(); ++i) {
            int local = inputLocal(i);
            if (i > 0) {
                method.visitVarInsn(Opcodes.ALOAD, 1);
                method.visitVarInsn(Opcodes.ALOAD, 2);
                method.visitInsn(Opcodes.ARRAYLENGTH);
                method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "borrowFloatArray", "(I)[F", false);
                method.visitVarInsn(Opcodes.ASTORE, local);
            }
            this.caller.call(method, this.inputs.get(i), local, 3, 4);
        }
        Label loop = new Label(), end = new Label();
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitVarInsn(Opcodes.ISTORE, INDEX_LOCAL);
        method.visitLabel(loop);
        method.visitVarInsn(Opcodes.ILOAD, INDEX_LOCAL);
        method.visitVarInsn(Opcodes.ILOAD, 4);
        method.visitJumpInsn(Opcodes.IF_ICMPGE, end);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, INDEX_LOCAL);
        this.expression(root, method);
        method.visitInsn(Opcodes.FASTORE);
        method.visitIincInsn(INDEX_LOCAL, 1);
        method.visitJumpInsn(Opcodes.GOTO, loop);
        method.visitLabel(end);
        for (int i = this.inputs.size() - 1; i > 0; --i) {
            method.visitVarInsn(Opcodes.ALOAD, 1);
            method.visitVarInsn(Opcodes.ALOAD, inputLocal(i));
            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "recycleFloatArray", "([F)V", false);
        }
    }

    private void expression(AstNode node, MethodVisitor method) {
        if (node instanceof ConstantNode constant) { method.visitLdcInsn(constant.value()); return; }
        Integer cached = this.values.get(node);
        if (cached != null) { method.visitVarInsn(Opcodes.FLOAD, cached); return; }
        Integer input = this.inputIndices.get(node);
        if (input != null) {
            method.visitVarInsn(Opcodes.ALOAD, inputLocal(input));
            method.visitVarInsn(Opcodes.ILOAD, INDEX_LOCAL);
            method.visitInsn(Opcodes.FALOAD);
        } else {
            for (AstNode child : node.children()) this.expression(child, method);
            if (node instanceof UnaryNode unary) ColumnMethodEmitter.emitUnaryOperation(method, unary);
            else if (node instanceof BinaryNode binary) ColumnMethodEmitter.emitBinaryOperation(method, binary);
            else method.visitMethodInsn(Opcodes.INVOKESTATIC, "com/moepus/byepregen/dfc/runtime/FloatMath",
                    "lerp", "(FFF)F", false);
        }
        int local = FIRST_VALUE_LOCAL + this.values.size();
        this.values.put(node, local);
        method.visitInsn(Opcodes.DUP);
        method.visitVarInsn(Opcodes.FSTORE, local);
    }

    private static int inputLocal(int index) { return index == 0 ? 2 : FIRST_INPUT_LOCAL + index - 1; }
}
