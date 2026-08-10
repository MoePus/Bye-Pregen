package com.moepus.byepregen.worldgen.surface;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class SurfaceAsmSupport {
    private SurfaceAsmSupport() {
    }

    static void pushInt(MethodVisitor method, int value) {
        if (value >= -1 && value <= 5) {
            method.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            method.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            method.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            method.visitLdcInsn(value);
        }
    }
}
