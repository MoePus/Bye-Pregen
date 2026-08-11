package com.moepus.byepregen.worldgen.surface;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.LAND;
import static org.objectweb.asm.Opcodes.LCMP;

final class SurfaceNoiseConditionEmitter {
    private final SurfaceEmissionContext context;
    private final SurfaceMethodLocals locals;

    SurfaceNoiseConditionEmitter(SurfaceEmissionContext context, SurfaceMethodLocals locals) {
        this.context = context;
        this.locals = locals;
    }

    void emit(
            MethodVisitor method,
            SurfaceRulePlan.KnownCondition condition,
            boolean branchOnTrue,
            Label target
    ) {
        SurfaceScalarLayout.NoiseCondition layout = (SurfaceScalarLayout.NoiseCondition)
                this.context.layout().condition(condition);
        this.emitCached(method, layout, branchOnTrue, target);
    }

    private void emitCached(
            MethodVisitor method,
            SurfaceScalarLayout.NoiseCondition layout,
            boolean branchOnTrue,
            Label target
    ) {
        int sampleBank = layout.sampleIndex() / Long.SIZE;
        long sampleMask = 1L << (layout.sampleIndex() & (Long.SIZE - 1));
        Label sampled = new Label();
        this.loadMask(method, SurfaceEmissionContext.sampledField(sampleBank), sampleMask);
        method.visitInsn(org.objectweb.asm.Opcodes.LCONST_0);
        method.visitInsn(LCMP);
        method.visitJumpInsn(IFNE, sampled);
        method.visitVarInsn(ALOAD, 0);
        this.loadContextInt(method, SurfaceRuntimeAbi.BLOCK_X);
        this.loadContextInt(method, SurfaceRuntimeAbi.BLOCK_Z);
        method.visitMethodInsn(
                INVOKEVIRTUAL,
                this.context.owner(),
                SurfaceEmissionContext.noiseSampleMethod(layout.sampleIndex()),
                "(II)V",
                false
        );
        method.visitLabel(sampled);

        int valueBank = layout.predicateIndex() / Long.SIZE;
        long valueMask = 1L << (layout.predicateIndex() & (Long.SIZE - 1));
        this.loadMask(method, SurfaceEmissionContext.valuesField(valueBank), valueMask);
        method.visitInsn(org.objectweb.asm.Opcodes.LCONST_0);
        method.visitInsn(LCMP);
        method.visitJumpInsn(branchOnTrue ? IFNE : IFEQ, target);
    }

    private void loadMask(MethodVisitor method, String field, long mask) {
        method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(GETFIELD, this.context.owner(), field, "J");
        method.visitLdcInsn(mask);
        method.visitInsn(LAND);
    }

    private void loadContextInt(MethodVisitor method, String accessor) {
        this.locals.loadInt(method, this.context, accessor);
    }

}
