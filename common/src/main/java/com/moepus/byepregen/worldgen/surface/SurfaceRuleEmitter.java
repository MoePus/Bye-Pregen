package com.moepus.byepregen.worldgen.surface;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.IFNULL;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

final class SurfaceRuleEmitter {
    private static final String RULE_DESCRIPTOR = Type.getMethodDescriptor(
            Type.getType(BlockState.class),
            Type.INT_TYPE,
            Type.INT_TYPE,
            Type.INT_TYPE
    );

    private final SurfaceEmissionContext context;

    SurfaceRuleEmitter(SurfaceEmissionContext context) {
        this.context = context;
    }

    void emitRoot(MethodVisitor method, SurfaceRulePlan.Rule root) {
        this.emitMethodBody(method, new SurfaceRegionPlan.RuleBody(root), true);
    }

    void emitRegion(MethodVisitor method, SurfaceRegionPlan.Region region) {
        this.emitMethodBody(method, region.body(), false);
    }

    static int rootAccess() {
        return ACC_PUBLIC | ACC_FINAL;
    }

    static int regionAccess() {
        return ACC_PRIVATE | ACC_FINAL;
    }

    static String descriptor() {
        return RULE_DESCRIPTOR;
    }

    private void emitMethodBody(
            MethodVisitor method,
            SurfaceRegionPlan.Body body,
            boolean rootMethod
    ) {
        method.visitCode();
        if (rootMethod) {
            SurfaceScalarClassEmitter.emitColumnReset(method, this.context);
        }
        SurfaceMethodLocals locals = SurfaceMethodLocals.create(this.context, body);
        locals.emitPrelude(method, this.context);
        MethodState state = new MethodState(
                locals,
                new SurfaceConditionEmitter(this.context, locals)
        );
        Label nullResult = new Label();
        switch (body) {
            case SurfaceRegionPlan.RuleBody rule -> this.emitRule(
                    method, state, rule.rule(), rule.rule(), null, nullResult
            );
            case SurfaceRegionPlan.SequenceRange range -> this.emitSequence(
                    method,
                    state,
                    range.sequence(),
                    null,
                    range.sequence(),
                    range.fromIndex(),
                    range.toIndex(),
                    nullResult
            );
        }
        method.visitLabel(nullResult);
        method.visitInsn(ACONST_NULL);
        method.visitInsn(ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private void emitRule(
            MethodVisitor method,
            MethodState state,
            SurfaceRulePlan.Rule rule,
            SurfaceRulePlan.Rule regionRoot,
            SurfaceRulePlan.Sequence activeRange,
            Label nullTarget
    ) {
        String outlined = rule == regionRoot ? null : this.context.regions().outlinedMethod(rule);
        if (outlined != null) {
            this.emitOutlined(method, state, outlined, nullTarget);
            return;
        }
        if (rule instanceof SurfaceRulePlan.State stateRule) {
            this.context.loadBinding(method, this.context.layout().ruleSlot(stateRule));
            method.visitInsn(ARETURN);
            return;
        }
        if (rule instanceof SurfaceRulePlan.Bandlands) {
            this.emitBandlands(method);
            return;
        }
        if (rule instanceof SurfaceRulePlan.OpaqueRule opaque) {
            this.emitOpaqueRule(method, state, opaque, nullTarget);
            return;
        }
        if (rule instanceof SurfaceRulePlan.Test test) {
            state.conditions().emitBranch(method, test.condition(), false, nullTarget);
            this.emitRule(
                    method, state, test.followup(), regionRoot, activeRange, nullTarget
            );
            return;
        }
        SurfaceRulePlan.Sequence sequence = (SurfaceRulePlan.Sequence) rule;
        this.emitSequence(
                method,
                state,
                sequence,
                regionRoot,
                activeRange,
                0,
                sequence.rules().size(),
                nullTarget
        );
    }

    private void emitSequence(
            MethodVisitor method,
            MethodState state,
            SurfaceRulePlan.Sequence sequence,
            SurfaceRulePlan.Rule regionRoot,
            SurfaceRulePlan.Sequence activeRange,
            int fromIndex,
            int toIndex,
            Label nullTarget
    ) {
        if (fromIndex == toIndex) {
            method.visitJumpInsn(org.objectweb.asm.Opcodes.GOTO, nullTarget);
            return;
        }
        int index = fromIndex;
        while (index < toIndex) {
            SurfaceRegionPlan.SequenceRange range = activeRange == sequence
                    ? null
                    : this.context.regions().rangeStartingAt(sequence, index);
            int nextIndex = range == null ? index + 1 : range.toIndex();
            Label next = nextIndex == toIndex ? nullTarget : new Label();
            if (range == null) {
                this.emitRule(
                        method,
                        state,
                        sequence.rules().get(index),
                        regionRoot,
                        activeRange,
                        next
                );
            } else {
                this.emitOutlined(method, state, range.methodName(), next);
            }
            if (nextIndex != toIndex) {
                method.visitLabel(next);
            }
            index = nextIndex;
        }
    }

    private void emitOutlined(
            MethodVisitor method,
            MethodState state,
            String name,
            Label nullTarget
    ) {
        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ILOAD, 1);
        method.visitVarInsn(ILOAD, 2);
        method.visitVarInsn(ILOAD, 3);
        method.visitMethodInsn(INVOKEVIRTUAL, this.context.owner(), name, RULE_DESCRIPTOR, false);
        method.visitVarInsn(ASTORE, state.locals().scratchLocal());
        method.visitVarInsn(ALOAD, state.locals().scratchLocal());
        method.visitJumpInsn(IFNULL, nullTarget);
        method.visitVarInsn(ALOAD, state.locals().scratchLocal());
        method.visitInsn(ARETURN);
    }

    private void emitBandlands(MethodVisitor method) {
        this.context.loadSurfaceSystem(method);
        method.visitVarInsn(ILOAD, 1);
        method.visitVarInsn(ILOAD, 2);
        method.visitVarInsn(ILOAD, 3);
        method.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(SurfaceSystem.class),
                SurfaceRuntimeAbi.BAND,
                RULE_DESCRIPTOR,
                false
        );
        method.visitInsn(ARETURN);
    }

    private void emitOpaqueRule(
            MethodVisitor method,
            MethodState state,
            SurfaceRulePlan.OpaqueRule rule,
            Label nullTarget
    ) {
        this.context.loadBinding(method, this.context.layout().ruleSlot(rule));
        method.visitVarInsn(ILOAD, 1);
        method.visitVarInsn(ILOAD, 2);
        method.visitVarInsn(ILOAD, 3);
        method.visitMethodInsn(
                INVOKEINTERFACE,
                this.context.abi().ruleOwner(),
                this.context.abi().ruleTryApply(),
                RULE_DESCRIPTOR,
                true
        );
        method.visitVarInsn(ASTORE, state.locals().scratchLocal());
        method.visitVarInsn(ALOAD, state.locals().scratchLocal());
        method.visitJumpInsn(IFNULL, nullTarget);
        method.visitVarInsn(ALOAD, state.locals().scratchLocal());
        method.visitInsn(ARETURN);
    }

    private record MethodState(
            SurfaceMethodLocals locals,
            SurfaceConditionEmitter conditions
    ) {
    }
}
