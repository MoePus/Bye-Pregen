package com.moepus.byepregen.worldgen.surface;

import java.util.function.Supplier;
import net.minecraft.core.Holder;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class SurfaceBiomeCacheEmitter {
    private static final String FAST_DESCRIPTOR = "()J";
    private static final String SLOW_DESCRIPTOR = "(Ljava/util/function/Supplier;)J";
    private static final String HOLDER_DESCRIPTOR = Type.getDescriptor(Holder.class);
    private static final String SUPPLIER_DESCRIPTOR = Type.getDescriptor(Supplier.class);

    private final SurfaceEmissionContext context;
    private final ClassWriter writer;

    SurfaceBiomeCacheEmitter(SurfaceEmissionContext context, ClassWriter writer) {
        this.context = context;
        this.writer = writer;
    }

    void emit() {
        if (this.context.layout().biomeValues().isEmpty()) {
            return;
        }
        this.emitFastPath();
        this.emitSlowPath();
        this.emitFallback();
    }

    private void emitFastPath() {
        MethodVisitor method = this.writer.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                SurfaceEmissionContext.BIOME_BITS_METHOD,
                FAST_DESCRIPTOR,
                null,
                null
        );
        method.visitCode();
        this.context.loadContext(method);
        this.context.invokeContext(
                method,
                SurfaceRuntimeAbi.BIOME_SUPPLIER,
                Type.getMethodDescriptor(Type.getType(Supplier.class))
        );
        method.visitVarInsn(Opcodes.ASTORE, 1);
        Label slow = new Label();
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitJumpInsn(Opcodes.IFNULL, slow);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        this.loadField(method, SurfaceEmissionContext.BIOME_SUPPLIER_FIELD, SUPPLIER_DESCRIPTOR);
        method.visitJumpInsn(Opcodes.IF_ACMPNE, slow);
        this.loadField(method, SurfaceEmissionContext.BIOME_BITS_FIELD, "J");
        method.visitInsn(Opcodes.LRETURN);
        method.visitLabel(slow);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                this.context.owner(),
                SurfaceEmissionContext.BIOME_BITS_SLOW_METHOD,
                SLOW_DESCRIPTOR,
                false
        );
        method.visitInsn(Opcodes.LRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private void emitSlowPath() {
        MethodVisitor method = this.writer.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                SurfaceEmissionContext.BIOME_BITS_SLOW_METHOD,
                SLOW_DESCRIPTOR,
                null,
                null
        );
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                Type.getInternalName(Supplier.class),
                "get",
                "()Ljava/lang/Object;",
                true
        );
        method.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Holder.class));
        method.visitVarInsn(Opcodes.ASTORE, 2);
        Label classify = new Label();
        this.loadField(method, SurfaceEmissionContext.BIOME_SUPPLIER_FIELD, SUPPLIER_DESCRIPTOR);
        method.visitJumpInsn(Opcodes.IFNULL, classify);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        this.loadField(method, SurfaceEmissionContext.BIOME_HOLDER_FIELD, HOLDER_DESCRIPTOR);
        method.visitJumpInsn(Opcodes.IF_ACMPNE, classify);
        this.publishSupplier(method);
        this.loadField(method, SurfaceEmissionContext.BIOME_BITS_FIELD, "J");
        method.visitInsn(Opcodes.LRETURN);

        method.visitLabel(classify);
        this.context.loadBinding(method, this.context.layout().biomeTableSlot());
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                Type.getInternalName(SurfaceBiomeBehaviorTable.class),
                "behavior",
                Type.getMethodDescriptor(Type.LONG_TYPE, Type.getType(Holder.class)),
                false
        );
        method.visitVarInsn(Opcodes.LSTORE, 3);
        this.publishClassification(method);
        method.visitVarInsn(Opcodes.LLOAD, 3);
        method.visitInsn(Opcodes.LRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private void publishClassification(MethodVisitor method) {
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.LLOAD, 3);
        method.visitFieldInsn(
                Opcodes.PUTFIELD,
                this.context.owner(),
                SurfaceEmissionContext.BIOME_BITS_FIELD,
                "J"
        );
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitFieldInsn(
                Opcodes.PUTFIELD,
                this.context.owner(),
                SurfaceEmissionContext.BIOME_HOLDER_FIELD,
                HOLDER_DESCRIPTOR
        );
        this.publishSupplier(method);
    }

    private void emitFallback() {
        String condition = this.context.abi().bindingDescriptor(
                SurfaceBindingLayout.Kind.CONDITION
        );
        String array = "[" + condition;
        MethodVisitor method = this.writer.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                SurfaceEmissionContext.BIOME_FALLBACK_METHOD,
                "(I)Z",
                null,
                null
        );
        method.visitCode();
        this.loadField(method, SurfaceEmissionContext.BIOME_FALLBACKS_FIELD, array);
        method.visitVarInsn(Opcodes.ASTORE, 2);
        Label ready = new Label();
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitJumpInsn(Opcodes.IFNONNULL, ready);
        SurfaceAsmSupport.pushInt(method, this.context.layout().biomeFallbacks());
        method.visitTypeInsn(Opcodes.ANEWARRAY, this.context.abi().conditionOwner());
        method.visitVarInsn(Opcodes.ASTORE, 2);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitFieldInsn(
                Opcodes.PUTFIELD,
                this.context.owner(),
                SurfaceEmissionContext.BIOME_FALLBACKS_FIELD,
                array
        );
        method.visitLabel(ready);
        this.emitFallbackBinding(method);
        method.visitVarInsn(Opcodes.ALOAD, 3);
        method.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                this.context.abi().conditionOwner(),
                this.context.abi().conditionTest(),
                "()Z",
                true
        );
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private void emitFallbackBinding(MethodVisitor method) {
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitInsn(Opcodes.AALOAD);
        method.visitVarInsn(Opcodes.ASTORE, 3);
        Label bound = new Label();
        method.visitVarInsn(Opcodes.ALOAD, 3);
        method.visitJumpInsn(Opcodes.IFNONNULL, bound);
        this.context.loadBinding(method, this.context.layout().biomeTableSlot());
        method.visitVarInsn(Opcodes.ILOAD, 1);
        this.context.loadContext(method);
        method.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                Type.getInternalName(SurfaceBiomeBehaviorTable.class),
                "bindFallback",
                "(ILjava/lang/Object;)Ljava/lang/Object;",
                false
        );
        method.visitTypeInsn(Opcodes.CHECKCAST, this.context.abi().conditionOwner());
        method.visitVarInsn(Opcodes.ASTORE, 3);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 3);
        method.visitInsn(Opcodes.AASTORE);
        method.visitLabel(bound);
    }

    private void publishSupplier(MethodVisitor method) {
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitFieldInsn(
                Opcodes.PUTFIELD,
                this.context.owner(),
                SurfaceEmissionContext.BIOME_SUPPLIER_FIELD,
                SUPPLIER_DESCRIPTOR
        );
    }

    private void loadField(MethodVisitor method, String name, String descriptor) {
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, this.context.owner(), name, descriptor);
    }
}
