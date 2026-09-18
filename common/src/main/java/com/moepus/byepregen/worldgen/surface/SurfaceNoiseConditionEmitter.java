package com.moepus.byepregen.worldgen.surface;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.DCMPG;
import static org.objectweb.asm.Opcodes.DCMPL;
import static org.objectweb.asm.Opcodes.DLOAD;
import static org.objectweb.asm.Opcodes.DSTORE;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.IFGT;
import static org.objectweb.asm.Opcodes.IFLT;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;

final class SurfaceNoiseConditionEmitter {
    private static final String SUPPLIER = "java/util/function/DoubleSupplier";
    private static final String SUPPLIER_DESCRIPTOR = "()D";

    private final SurfaceEmissionContext context;
    private final SurfaceMethodLocals locals;

    SurfaceNoiseConditionEmitter(SurfaceEmissionContext context, SurfaceMethodLocals locals) {
        this.context = context;
        this.locals = locals;
    }

    /**
     * 26.3: the context caches one sampler per (noise, is3d) and hands back an epoch-aware
     * {@code DoubleSupplier}, so the condition samples inline instead of publishing a per-column
     * noise bank. The two comparisons keep vanilla's {@code DCMPL} / {@code DCMPG} polarity so NaN
     * still fails the condition.
     */
    void emit(
            MethodVisitor method,
            SurfaceRulePlan.KnownCondition condition,
            boolean branchOnTrue,
            Label target
    ) {
        SurfaceScalarLayout.Noise layout = (SurfaceScalarLayout.Noise)
                this.context.layout().condition(condition);
        SurfaceConditionSpec.Noise spec = (SurfaceConditionSpec.Noise)
                condition.value().spec();
        this.context.loadBinding(method, layout.supplier());
        method.visitMethodInsn(
                INVOKEINTERFACE, SUPPLIER, SurfaceRuntimeAbi.NOISE_VALUE, SUPPLIER_DESCRIPTOR, true
        );
        method.visitVarInsn(DSTORE, this.locals.scratchLocal());

        Label notInRange = branchOnTrue ? new Label() : target;
        method.visitVarInsn(DLOAD, this.locals.scratchLocal());
        method.visitLdcInsn(spec.minimum());
        method.visitInsn(DCMPL);
        method.visitJumpInsn(IFLT, notInRange);
        method.visitVarInsn(DLOAD, this.locals.scratchLocal());
        method.visitLdcInsn(spec.maximum());
        method.visitInsn(DCMPG);
        method.visitJumpInsn(IFGT, notInRange);
        if (branchOnTrue) {
            method.visitJumpInsn(GOTO, target);
            method.visitLabel(notInRange);
        }
    }
}
