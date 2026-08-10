package com.moepus.byepregen.worldgen.surface;

import java.util.function.Supplier;
import net.minecraft.core.Holder;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
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
        this.writer.visit(
                Opcodes.V21,
                CLASS_ACCESS,
                this.context.owner(),
                null,
                "java/lang/Object",
                new String[]{this.context.abi().ruleOwner()}
        );
        this.emitFields();
        this.emitConstructor();
        this.emitRoot();
        this.emitRegions();
        this.emitNoiseSamples();
        new SurfaceBiomeCacheEmitter(this.context, this.writer).emit();
        this.writer.visitEnd();
        return this.writer.toByteArray();
    }

    static void emitColumnReset(MethodVisitor method, SurfaceEmissionContext context) {
        if (context.target() != SurfaceScalarTarget.BUILD_POINT
                || context.layout().noiseSampleBanks() == 0) {
            return;
        }
        Label currentColumn = new Label();
        context.loadContext(method);
        context.invokeContext(method, SurfaceRuntimeAbi.LAST_UPDATE_XZ, "()J");
        method.visitVarInsn(Opcodes.LSTORE, 4);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(
                Opcodes.GETFIELD,
                context.owner(),
                SurfaceEmissionContext.COLUMN_EPOCH_FIELD,
                "J"
        );
        method.visitVarInsn(Opcodes.LLOAD, 4);
        method.visitInsn(Opcodes.LCMP);
        method.visitJumpInsn(Opcodes.IFEQ, currentColumn);

        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.LLOAD, 4);
        method.visitFieldInsn(
                Opcodes.PUTFIELD,
                context.owner(),
                SurfaceEmissionContext.COLUMN_EPOCH_FIELD,
                "J"
        );
        for (int bank = 0; bank < context.layout().noiseSampleBanks(); bank++) {
            clearLongField(method, context.owner(), SurfaceEmissionContext.sampledField(bank));
        }
        method.visitLabel(currentColumn);
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
        this.emitCacheFields();
    }

    private void emitCacheFields() {
        if (this.context.target() == SurfaceScalarTarget.BUILD_POINT
                && this.context.layout().noiseSampleBanks() != 0) {
            this.writer.visitField(
                    Opcodes.ACC_PRIVATE,
                    SurfaceEmissionContext.COLUMN_EPOCH_FIELD,
                    "J",
                    null,
                    null
            ).visitEnd();
            for (int bank = 0; bank < this.context.layout().noiseSampleBanks(); bank++) {
                this.writer.visitField(
                        Opcodes.ACC_PRIVATE,
                        SurfaceEmissionContext.sampledField(bank),
                        "J",
                        null,
                        null
                ).visitEnd();
            }
            for (int bank = 0; bank < this.context.layout().noiseValueBanks(); bank++) {
                this.writer.visitField(
                        Opcodes.ACC_PRIVATE,
                        SurfaceEmissionContext.valuesField(bank),
                        "J",
                        null,
                        null
                ).visitEnd();
            }
        }
        if (!this.context.layout().biomeValues().isEmpty()) {
            this.writer.visitField(
                    Opcodes.ACC_PRIVATE,
                    SurfaceEmissionContext.BIOME_SUPPLIER_FIELD,
                    Type.getDescriptor(Supplier.class),
                    null,
                    null
            ).visitEnd();
            this.writer.visitField(
                    Opcodes.ACC_PRIVATE,
                    SurfaceEmissionContext.BIOME_HOLDER_FIELD,
                    Type.getDescriptor(Holder.class),
                    null,
                    null
            ).visitEnd();
            this.writer.visitField(
                    Opcodes.ACC_PRIVATE,
                    SurfaceEmissionContext.BIOME_BITS_FIELD,
                    "J",
                    null,
                    null
            ).visitEnd();
            this.writer.visitField(
                    Opcodes.ACC_PRIVATE,
                    SurfaceEmissionContext.BIOME_FALLBACKS_FIELD,
                    "[" + this.context.abi().bindingDescriptor(
                            SurfaceBindingLayout.Kind.CONDITION
                    ),
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
        SurfaceAsmSupport.pushInt(method, slot.valueIndex());
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

    private void emitNoiseSamples() {
        if (this.context.target() != SurfaceScalarTarget.BUILD_POINT) {
            return;
        }
        SurfaceNoiseSampleEmitter emitter = new SurfaceNoiseSampleEmitter(
                this.context, this.writer
        );
        for (SurfaceScalarLayout.NoiseSample sample : this.context.layout().noiseSamples()) {
            emitter.emit(sample);
        }
    }

    private static void clearLongField(MethodVisitor method, String owner, String name) {
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitInsn(Opcodes.LCONST_0);
        method.visitFieldInsn(Opcodes.PUTFIELD, owner, name, "J");
    }
}
