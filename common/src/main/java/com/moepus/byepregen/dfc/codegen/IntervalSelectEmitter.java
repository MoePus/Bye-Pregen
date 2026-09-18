package com.moepus.byepregen.dfc.codegen;

import java.util.List;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Shared scalar/column selection: strict thresholds, with NaN selectors taking the last branch. */
final class IntervalSelectEmitter {
    private IntervalSelectEmitter() { }

    static void branch(MethodVisitor method, List<Float> thresholds, int valueLocal, Label[] targets) {
        for (int i = 0; i < thresholds.size(); ++i) {
            method.visitVarInsn(Opcodes.FLOAD, valueLocal);
            method.visitLdcInsn(thresholds.get(i));
            method.visitInsn(Opcodes.FCMPG);
            method.visitJumpInsn(Opcodes.IFLT, targets[i]);
        }
        method.visitJumpInsn(Opcodes.GOTO, targets[targets.length - 1]);
    }

    static void index(MethodVisitor method, List<Float> thresholds, int valueLocal, int indexLocal) {
        Label[] targets = labels(thresholds.size() + 1);
        Label end = new Label();
        branch(method, thresholds, valueLocal, targets);
        for (int i = 0; i < targets.length; ++i) {
            method.visitLabel(targets[i]);
            ColumnClassBuilder.pushInt(method, i);
            method.visitVarInsn(Opcodes.ISTORE, indexLocal);
            method.visitJumpInsn(Opcodes.GOTO, end);
        }
        method.visitLabel(end);
    }

    static Label[] labels(int size) {
        Label[] result = new Label[size];
        for (int i = 0; i < size; ++i) result[i] = new Label();
        return result;
    }
}
