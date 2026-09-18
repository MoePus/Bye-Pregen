package com.moepus.byepregen.dfc.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moepus.byepregen.dfc.ast.AstNodes.Axis;
import com.moepus.byepregen.dfc.ast.AstNodes.ConstantNode;
import com.moepus.byepregen.dfc.ast.AstNodes.CoordinateNode;
import com.moepus.byepregen.dfc.ast.AstNodes.DivNode;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

final class PointBinaryEmitterTest {
    @Test
    void constantDivisorUsesReciprocalMultiplication() {
        MethodNode method = new MethodNode();
        // 26.3: the AST and the emitters are float based, and PointBinaryEmitter takes a point-mode flag.
        PointBinaryEmitter emitter = new PointBinaryEmitter((visitor, node) -> visitor.visitLdcInsn(8.0F), true);

        emitter.emit(method, new DivNode(new CoordinateNode(Axis.Y), new ConstantNode(4.0F)));

        assertTrue(containsOpcode(method, Opcodes.FMUL));
        assertFalse(containsOpcode(method, Opcodes.FDIV));
        assertTrue(containsFloatConstant(method, 0.25F));
    }

    private static boolean containsOpcode(MethodNode method, int opcode) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() == opcode) return true;
        }
        return false;
    }

    private static boolean containsFloatConstant(MethodNode method, float value) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof Float number && number == value) {
                return true;
            }
        }
        return false;
    }
}
