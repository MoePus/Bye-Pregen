package com.moepus.byepregen.worldgen.surface;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.D2I;
import static org.objectweb.asm.Opcodes.DCMPG;
import static org.objectweb.asm.Opcodes.DLOAD;
import static org.objectweb.asm.Opcodes.DSTORE;
import static org.objectweb.asm.Opcodes.F2D;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.IADD;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IFGE;
import static org.objectweb.asm.Opcodes.IFGT;
import static org.objectweb.asm.Opcodes.IFLE;
import static org.objectweb.asm.Opcodes.IFLT;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.IF_ICMPEQ;
import static org.objectweb.asm.Opcodes.IF_ICMPGE;
import static org.objectweb.asm.Opcodes.IF_ICMPGT;
import static org.objectweb.asm.Opcodes.IF_ICMPLE;
import static org.objectweb.asm.Opcodes.IF_ICMPLT;
import static org.objectweb.asm.Opcodes.INEG;
import static org.objectweb.asm.Opcodes.IMUL;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.LAND;
import static org.objectweb.asm.Opcodes.LCMP;
import static com.moepus.byepregen.worldgen.surface.SurfaceAsmSupport.pushInt;

final class SurfaceConditionEmitter {
    private static final String TEST_DESCRIPTOR = "()Z";

    private final SurfaceEmissionContext context;
    private final SurfaceMethodLocals locals;
    private final SurfaceNoiseConditionEmitter noiseEmitter;

    SurfaceConditionEmitter(
            SurfaceEmissionContext context,
            SurfaceMethodLocals locals
    ) {
        this.context = context;
        this.locals = locals;
        this.noiseEmitter = new SurfaceNoiseConditionEmitter(context, locals);
    }

    void emitBranch(
            MethodVisitor method,
            SurfaceRulePlan.Condition condition,
            boolean branchOnTrue,
            Label target
    ) {
        if (condition instanceof SurfaceRulePlan.NotCondition not) {
            this.emitBranch(method, not.target(), !branchOnTrue, target);
            return;
        }
        if (condition instanceof SurfaceRulePlan.OpaqueCondition opaque) {
            this.emitDelegate(method, opaque, branchOnTrue, target);
            return;
        }
        this.emitKnown(method, (SurfaceRulePlan.KnownCondition) condition, branchOnTrue, target);
    }

    private void emitKnown(
            MethodVisitor method,
            SurfaceRulePlan.KnownCondition condition,
            boolean branchOnTrue,
            Label target
    ) {
        SurfaceConditionSpec spec = condition.value().spec();
        SurfaceConditionPlan plan = this.context.layout()
                .plan()
                .conditionPlan(condition.value().id());
        switch (plan.kind()) {
            case BIOME -> this.emitBiome(
                    method, condition, branchOnTrue, target
            );
            case NOISE -> this.noiseEmitter.emit(
                    method,
                    condition,
                    (SurfaceConditionSpec.Noise) spec,
                    branchOnTrue,
                    target
            );
            case STONE_DEPTH -> this.emitStoneDepth(
                    method, (SurfaceConditionSpec.StoneDepth) spec, branchOnTrue, target
            );
            case VERTICAL_GRADIENT -> this.emitGradient(
                    method,
                    condition,
                    (SurfaceConditionSpec.VerticalGradient) spec,
                    branchOnTrue,
                    target
            );
            case WATER -> this.emitWater(
                    method, (SurfaceConditionSpec.Water) spec, branchOnTrue, target
            );
            case Y_ABOVE -> this.emitYAbove(
                    method,
                    condition,
                    (SurfaceConditionSpec.YAbove) spec,
                    branchOnTrue,
                    target
            );
            case ABOVE_PRELIMINARY, HOLE, STEEP, TEMPERATURE -> this.emitSingleton(
                    method,
                    condition,
                    (SurfaceConditionSpec.Singleton) spec,
                    branchOnTrue,
                    target
            );
            case NEGATED, OPAQUE -> throw new IllegalStateException(
                    "Unexpected known condition " + spec
            );
        }
    }

    private void emitBiome(
            MethodVisitor method,
            SurfaceRulePlan.KnownCondition condition,
            boolean branchOnTrue,
            Label target
    ) {
        SurfaceScalarLayout.ConditionLayout layout = this.context.layout().condition(condition);
        long mask = 1L << layout.behaviorBit();
        method.visitVarInsn(ALOAD, 0);
        method.visitMethodInsn(
                INVOKEVIRTUAL,
                this.context.owner(),
                SurfaceEmissionContext.BIOME_BITS_METHOD,
                "()J",
                false
        );
        method.visitVarInsn(org.objectweb.asm.Opcodes.LSTORE, this.locals.scratchLocal());
        Label supported = new Label();
        Label completed = new Label();
        method.visitVarInsn(org.objectweb.asm.Opcodes.LLOAD, this.locals.scratchLocal());
        method.visitInsn(org.objectweb.asm.Opcodes.LCONST_0);
        method.visitInsn(LCMP);
        method.visitJumpInsn(IFLT, supported);
        method.visitVarInsn(ALOAD, 0);
        pushInt(method, layout.cacheIndex());
        method.visitMethodInsn(
                INVOKEVIRTUAL,
                this.context.owner(),
                SurfaceEmissionContext.BIOME_FALLBACK_METHOD,
                "(I)Z",
                false
        );
        method.visitJumpInsn(branchOnTrue ? IFNE : IFEQ, target);
        method.visitJumpInsn(GOTO, completed);
        method.visitLabel(supported);
        method.visitVarInsn(org.objectweb.asm.Opcodes.LLOAD, this.locals.scratchLocal());
        method.visitLdcInsn(mask);
        method.visitInsn(LAND);
        method.visitInsn(org.objectweb.asm.Opcodes.LCONST_0);
        method.visitInsn(LCMP);
        method.visitJumpInsn(branchOnTrue ? IFNE : IFEQ, target);
        method.visitLabel(completed);
    }

    private void emitStoneDepth(
            MethodVisitor method,
            SurfaceConditionSpec.StoneDepth stone,
            boolean branchOnTrue,
            Label target
    ) {
        this.loadContextInt(
                method,
                stone.surfaceType() == CaveSurface.CEILING
                        ? SurfaceRuntimeAbi.STONE_BELOW
                        : SurfaceRuntimeAbi.STONE_ABOVE
        );
        pushInt(method, 1 + stone.offset());
        if (stone.addSurfaceDepth()) {
            this.loadContextInt(method, SurfaceRuntimeAbi.SURFACE_DEPTH);
            method.visitInsn(IADD);
        }
        if (stone.secondaryDepthRange() != 0) {
            this.emitSecondaryDepth(method, stone.secondaryDepthRange());
            method.visitInsn(IADD);
        }
        method.visitJumpInsn(branchOnTrue ? IF_ICMPLE : IF_ICMPGT, target);
    }

    private void emitSecondaryDepth(MethodVisitor method, int range) {
        this.context.loadContext(method);
        this.context.invokeContext(method, SurfaceRuntimeAbi.SURFACE_SECONDARY, "()D");
        method.visitLdcInsn(-1.0D);
        method.visitLdcInsn(1.0D);
        method.visitLdcInsn(0.0D);
        method.visitLdcInsn((double) range);
        method.visitMethodInsn(
                INVOKESTATIC,
                Type.getInternalName(Mth.class),
                this.context.abi().mathMap(),
                "(DDDDD)D",
                false
        );
        method.visitInsn(D2I);
    }

    private void emitGradient(
            MethodVisitor method,
            SurfaceRulePlan.KnownCondition condition,
            SurfaceConditionSpec.VerticalGradient ignored,
            boolean branchOnTrue,
            Label target
    ) {
        SurfaceScalarLayout.ConditionLayout layout = this.context.layout().condition(condition);
        Label fallthrough = new Label();
        this.loadContextInt(method, SurfaceRuntimeAbi.BLOCK_Y);
        this.context.loadBinding(method, layout.primaryBinding());
        method.visitJumpInsn(branchOnTrue ? IF_ICMPLE : IF_ICMPLE, branchOnTrue ? target : fallthrough);
        this.loadContextInt(method, SurfaceRuntimeAbi.BLOCK_Y);
        this.context.loadBinding(method, layout.secondaryBinding());
        method.visitJumpInsn(branchOnTrue ? IF_ICMPGE : IF_ICMPGE, branchOnTrue ? fallthrough : target);
        this.emitGradientRandom(method, layout);
        method.visitJumpInsn(branchOnTrue ? IFLT : IFGE, target);
        method.visitLabel(fallthrough);
    }

    private void emitGradientRandom(
            MethodVisitor method,
            SurfaceScalarLayout.ConditionLayout layout
    ) {
        this.loadContextInt(method, SurfaceRuntimeAbi.BLOCK_Y);
        method.visitInsn(org.objectweb.asm.Opcodes.I2D);
        this.context.loadBinding(method, layout.primaryBinding());
        method.visitInsn(org.objectweb.asm.Opcodes.I2D);
        this.context.loadBinding(method, layout.secondaryBinding());
        method.visitInsn(org.objectweb.asm.Opcodes.I2D);
        method.visitLdcInsn(1.0D);
        method.visitLdcInsn(0.0D);
        method.visitMethodInsn(
                INVOKESTATIC,
                Type.getInternalName(Mth.class),
                this.context.abi().mathMap(),
                "(DDDDD)D",
                false
        );
        method.visitVarInsn(DSTORE, this.locals.scratchLocal());
        this.context.loadBinding(method, layout.tertiaryBinding());
        this.loadContextInt(method, SurfaceRuntimeAbi.BLOCK_X);
        this.loadContextInt(method, SurfaceRuntimeAbi.BLOCK_Y);
        this.loadContextInt(method, SurfaceRuntimeAbi.BLOCK_Z);
        method.visitMethodInsn(
                PositionalRandomFactory.class.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                Type.getInternalName(PositionalRandomFactory.class),
                this.context.abi().randomAt(),
                Type.getMethodDescriptor(
                        Type.getType(RandomSource.class),
                        Type.INT_TYPE,
                        Type.INT_TYPE,
                        Type.INT_TYPE
                ),
                PositionalRandomFactory.class.isInterface()
        );
        method.visitMethodInsn(
                RandomSource.class.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                Type.getInternalName(RandomSource.class),
                this.context.abi().randomNextFloat(),
                "()F",
                RandomSource.class.isInterface()
        );
        method.visitInsn(F2D);
        method.visitVarInsn(DLOAD, this.locals.scratchLocal());
        method.visitInsn(DCMPG);
    }

    private void emitWater(
            MethodVisitor method,
            SurfaceConditionSpec.Water water,
            boolean branchOnTrue,
            Label target
    ) {
        Label trueFallthrough = branchOnTrue ? null : new Label();
        this.loadContextInt(method, SurfaceRuntimeAbi.WATER_HEIGHT);
        pushInt(method, Integer.MIN_VALUE);
        method.visitJumpInsn(IF_ICMPEQ, branchOnTrue ? target : trueFallthrough);
        this.loadContextInt(method, SurfaceRuntimeAbi.BLOCK_Y);
        if (water.addStoneDepth()) {
            this.loadContextInt(method, SurfaceRuntimeAbi.STONE_ABOVE);
            method.visitInsn(IADD);
        }
        this.loadContextInt(method, SurfaceRuntimeAbi.WATER_HEIGHT);
        if (water.offset() != 0) {
            pushInt(method, water.offset());
            method.visitInsn(IADD);
        }
        this.emitSurfaceDepthTerm(method, water.surfaceDepthMultiplier());
        method.visitJumpInsn(branchOnTrue ? IF_ICMPGE : IF_ICMPLT, target);
        if (trueFallthrough != null) {
            method.visitLabel(trueFallthrough);
        }
    }

    private void emitYAbove(
            MethodVisitor method,
            SurfaceRulePlan.KnownCondition condition,
            SurfaceConditionSpec.YAbove yAbove,
            boolean branchOnTrue,
            Label target
    ) {
        SurfaceScalarLayout.ConditionLayout layout = this.context.layout().condition(condition);
        this.loadContextInt(method, SurfaceRuntimeAbi.BLOCK_Y);
        if (yAbove.addStoneDepth()) {
            this.loadContextInt(method, SurfaceRuntimeAbi.STONE_ABOVE);
            method.visitInsn(IADD);
        }
        if (layout.resolvedAnchor()) {
            pushInt(method, layout.cacheIndex());
        } else {
            this.context.loadBinding(method, layout.primaryBinding());
            this.context.loadWorldGenerationContext(method);
            method.visitMethodInsn(
                    INVOKEINTERFACE,
                    Type.getInternalName(VerticalAnchor.class),
                    this.context.abi().anchorResolveY(),
                    Type.getMethodDescriptor(
                            Type.INT_TYPE,
                            Type.getType(WorldGenerationContext.class)
                    ),
                    true
            );
        }
        this.emitSurfaceDepthTerm(method, yAbove.surfaceDepthMultiplier());
        method.visitJumpInsn(branchOnTrue ? IF_ICMPGE : IF_ICMPLT, target);
    }

    private void emitSingleton(
            MethodVisitor method,
            SurfaceRulePlan.KnownCondition condition,
            SurfaceConditionSpec.Singleton singleton,
            boolean branchOnTrue,
            Label target
    ) {
        switch (singleton) {
            case ABOVE_PRELIMINARY_SURFACE -> {
                this.loadContextInt(method, SurfaceRuntimeAbi.BLOCK_Y);
                this.context.loadContext(method);
                this.context.invokeContext(method, SurfaceRuntimeAbi.MIN_SURFACE_LEVEL, "()I");
                method.visitJumpInsn(branchOnTrue ? IF_ICMPGE : IF_ICMPLT, target);
            }
            case HOLE -> {
                this.loadContextInt(method, SurfaceRuntimeAbi.SURFACE_DEPTH);
                method.visitJumpInsn(branchOnTrue ? IFLE : IFGT, target);
            }
            case STEEP, TEMPERATURE -> this.emitDelegate(
                    method, condition, branchOnTrue, target
            );
        }
    }

    private void emitDelegate(
            MethodVisitor method,
            SurfaceRulePlan.Condition condition,
            boolean branchOnTrue,
            Label target
    ) {
        SurfaceRulePlan.BindingSlotId slot = this.context.layout()
                .condition(condition)
                .primaryBinding();
        this.context.loadBinding(method, slot);
        method.visitMethodInsn(
                INVOKEINTERFACE,
                this.context.abi().conditionOwner(),
                this.context.abi().conditionTest(),
                TEST_DESCRIPTOR,
                true
        );
        method.visitJumpInsn(branchOnTrue ? IFNE : IFEQ, target);
    }

    private void loadContextInt(MethodVisitor method, String accessor) {
        this.locals.loadInt(method, this.context, accessor);
    }

    private void emitSurfaceDepthTerm(MethodVisitor method, int multiplier) {
        if (multiplier == 0) {
            return;
        }
        this.loadContextInt(method, SurfaceRuntimeAbi.SURFACE_DEPTH);
        if (multiplier == -1) {
            method.visitInsn(INEG);
        } else if (multiplier != 1) {
            pushInt(method, multiplier);
            method.visitInsn(IMUL);
        }
        method.visitInsn(IADD);
    }
}
