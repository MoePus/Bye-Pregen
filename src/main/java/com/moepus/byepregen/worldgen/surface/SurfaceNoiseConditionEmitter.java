package com.moepus.byepregen.worldgen.surface;

import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.DCMPG;
import static org.objectweb.asm.Opcodes.DCMPL;
import static org.objectweb.asm.Opcodes.DLOAD;
import static org.objectweb.asm.Opcodes.DSTORE;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IFGT;
import static org.objectweb.asm.Opcodes.IFLT;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.LAND;
import static org.objectweb.asm.Opcodes.LCMP;

final class SurfaceNoiseConditionEmitter {
    private static final String NOISE_DESCRIPTOR = "(DDD)D";

    private final SurfaceEmissionContext context;
    private final SurfaceMethodLocals locals;

    SurfaceNoiseConditionEmitter(SurfaceEmissionContext context, SurfaceMethodLocals locals) {
        this.context = context;
        this.locals = locals;
    }

    void emit(
            MethodVisitor method,
            SurfaceRulePlan.KnownCondition condition,
            SurfaceConditionSpec.Noise noise,
            boolean branchOnTrue,
            Label target
    ) {
        SurfaceScalarLayout.ConditionLayout layout = this.context.layout().condition(condition);
        if (this.context.target() == SurfaceScalarTarget.TOP_POINT) {
            this.emitNoiseValue(method, layout.primaryBinding());
            this.emitComparison(method, noise, branchOnTrue, target);
            return;
        }
        this.emitCached(method, layout, branchOnTrue, target);
    }

    private void emitCached(
            MethodVisitor method,
            SurfaceScalarLayout.ConditionLayout layout,
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

        int valueBank = layout.cacheIndex() / Long.SIZE;
        long valueMask = 1L << (layout.cacheIndex() & (Long.SIZE - 1));
        this.loadMask(method, SurfaceEmissionContext.valuesField(valueBank), valueMask);
        method.visitInsn(org.objectweb.asm.Opcodes.LCONST_0);
        method.visitInsn(LCMP);
        method.visitJumpInsn(branchOnTrue ? IFNE : IFEQ, target);
    }

    private void emitNoiseValue(MethodVisitor method, SurfaceRulePlan.BindingSlotId slot) {
        this.context.loadBinding(method, slot);
        this.loadContextInt(method, SurfaceRuntimeAbi.BLOCK_X);
        method.visitInsn(org.objectweb.asm.Opcodes.I2D);
        method.visitInsn(org.objectweb.asm.Opcodes.DCONST_0);
        this.loadContextInt(method, SurfaceRuntimeAbi.BLOCK_Z);
        method.visitInsn(org.objectweb.asm.Opcodes.I2D);
        method.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(NormalNoise.class),
                this.context.abi().noiseGetValue(),
                NOISE_DESCRIPTOR,
                false
        );
    }

    private void emitComparison(
            MethodVisitor method,
            SurfaceConditionSpec.Noise noise,
            boolean branchOnTrue,
            Label target
    ) {
        method.visitVarInsn(DSTORE, this.locals.scratchLocal());
        Label falseResult = branchOnTrue ? new Label() : target;
        method.visitVarInsn(DLOAD, this.locals.scratchLocal());
        method.visitLdcInsn(noise.minimum());
        method.visitInsn(DCMPL);
        method.visitJumpInsn(IFLT, falseResult);
        method.visitVarInsn(DLOAD, this.locals.scratchLocal());
        method.visitLdcInsn(noise.maximum());
        method.visitInsn(DCMPG);
        method.visitJumpInsn(IFGT, falseResult);
        if (branchOnTrue) {
            method.visitJumpInsn(GOTO, target);
            method.visitLabel(falseResult);
        }
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
