package com.moepus.byepregen.worldgen.surface;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class SurfaceScalarClassEmitter {
    private static final int CLASS_ACCESS = Opcodes.ACC_FINAL
            | Opcodes.ACC_SUPER
            | Opcodes.ACC_SYNTHETIC;
    private static final int FIELD_ACCESS = Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL;

    private final SurfaceEmissionContext context;
    private final ClassWriter writer;
    private final SurfaceRuleEmitter rules;

    SurfaceScalarClassEmitter(SurfaceEmissionContext context) {
        this.context = context;
        this.writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        this.rules = new SurfaceRuleEmitter(context);
    }

    byte[] emit() {
        String[] interfaces = this.context.layout().plan().boundedStoneDepthBelow()
                ? new String[]{
                        this.context.abi().ruleOwner(),
                        Type.getInternalName(SurfaceBoundedStoneDepthRule.class)
                }
                : new String[]{this.context.abi().ruleOwner()};
        // 26.3: hidden classes are defined from the compiler's own lookup, so the generated class
        // lives in this package next to its nest host instead of inside SurfaceRules.
        this.writer.visit(
                Opcodes.V25,
                CLASS_ACCESS,
                this.context.owner(),
                null,
                "java/lang/Object",
                interfaces
        );
        this.emitFields();
        this.emitConstructor();
        this.emitRoot();
        this.emitRegions();
        this.writer.visitEnd();
        return this.writer.toByteArray();
    }

    private void emitFields() {
        this.writer.visitField(
                FIELD_ACCESS,
                SurfaceEmissionContext.CONTEXT_FIELD,
                this.context.contextDescriptor(),
                null,
                null
        ).visitEnd();
        for (SurfaceBindingLayout.Slot slot : this.context.layout().bindings().storedSlots()) {
            this.writer.visitField(
                    FIELD_ACCESS,
                    slot.fieldName(),
                    this.context.abi().bindingDescriptor(slot.kind()),
                    null,
                    null
            ).visitEnd();
        }
    }

    private void emitConstructor() {
        String descriptor = Type.getMethodDescriptor(
                Type.VOID_TYPE,
                Type.getType(this.context.abi().contextClass()),
                Type.getType(Object[].class)
        );
        MethodVisitor method = this.writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                "<init>",
                descriptor,
                null,
                null
        );
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/lang/Object",
                "<init>",
                "()V",
                false
        );
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitFieldInsn(
                Opcodes.PUTFIELD,
                this.context.owner(),
                SurfaceEmissionContext.CONTEXT_FIELD,
                this.context.contextDescriptor()
        );
        for (SurfaceBindingLayout.Slot slot : this.context.layout().bindings().storedSlots()) {
            this.emitConstructorBinding(method, slot);
        }
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private void emitConstructorBinding(MethodVisitor method, SurfaceBindingLayout.Slot slot) {
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        SurfaceAsmSupport.pushInt(method, slot.id().value());
        method.visitInsn(Opcodes.AALOAD);
        String descriptor = this.context.abi().bindingDescriptor(slot.kind());
        if (slot.kind() == SurfaceBindingLayout.Kind.RESOLVED_ANCHOR) {
            method.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer");
            method.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    "java/lang/Integer",
                    "intValue",
                    "()I",
                    false
            );
        } else {
            method.visitTypeInsn(Opcodes.CHECKCAST, Type.getType(descriptor).getInternalName());
        }
        method.visitFieldInsn(
                Opcodes.PUTFIELD,
                this.context.owner(),
                slot.fieldName(),
                descriptor
        );
    }

    private void emitRoot() {
        MethodVisitor method = this.writer.visitMethod(
                SurfaceRuleEmitter.rootAccess(),
                this.context.abi().ruleTryApply(),
                SurfaceRuleEmitter.descriptor(),
                null,
                null
        );
        this.rules.emitRoot(method, this.context.layout().plan().root());
    }

    private void emitRegions() {
        for (SurfaceRegionPlan.Region region : this.context.regions().regions()) {
            MethodVisitor method = this.writer.visitMethod(
                    SurfaceRuleEmitter.regionAccess(),
                    region.methodName(),
                    SurfaceRuleEmitter.descriptor(),
                    null,
                    null
            );
            this.rules.emitRegion(method, region);
        }
    }
}
