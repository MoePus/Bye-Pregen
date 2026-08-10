package com.moepus.byepregen.worldgen.surface;

import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

final class SurfaceEmissionContext {
    static final String CONTEXT_FIELD = "context";
    static final String BIOME_BITS_METHOD = "biomeBits";
    static final String BIOME_BITS_SLOW_METHOD = "biomeBitsSlow";
    static final String BIOME_FALLBACK_METHOD = "biomeFallback";
    static final String COLUMN_EPOCH_FIELD = "columnEpoch";
    static final String BIOME_SUPPLIER_FIELD = "biomeSupplier";
    static final String BIOME_HOLDER_FIELD = "biomeHolder";
    static final String BIOME_BITS_FIELD = "biomeBits";
    static final String BIOME_FALLBACKS_FIELD = "biomeFallbacks";

    private final String owner;
    private final SurfaceRuntimeAbi abi;
    private final SurfaceScalarLayout layout;
    private final SurfaceRegionPlan regions;
    private final SurfaceScalarTarget target;

    SurfaceEmissionContext(
            String owner,
            SurfaceRuntimeAbi abi,
            SurfaceScalarLayout layout,
            SurfaceRegionPlan regions,
            SurfaceScalarTarget target
    ) {
        this.owner = owner;
        this.abi = abi;
        this.layout = layout;
        this.regions = regions;
        this.target = target;
    }

    String owner() {
        return this.owner;
    }

    SurfaceRuntimeAbi abi() {
        return this.abi;
    }

    SurfaceScalarLayout layout() {
        return this.layout;
    }

    SurfaceRegionPlan regions() {
        return this.regions;
    }

    SurfaceScalarTarget target() {
        return this.target;
    }

    String contextDescriptor() {
        return Type.getDescriptor(this.abi.contextClass());
    }

    String bindingDescriptor(SurfaceRulePlan.BindingSlotId slot) {
        return this.abi.bindingDescriptor(this.layout.bindings().storedSlot(slot).kind());
    }

    String bindingField(SurfaceRulePlan.BindingSlotId slot) {
        return this.layout.bindings().storedSlot(slot).fieldName();
    }

    void loadContext(MethodVisitor method) {
        method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(GETFIELD, this.owner, CONTEXT_FIELD, this.contextDescriptor());
    }

    void loadBinding(MethodVisitor method, SurfaceRulePlan.BindingSlotId slot) {
        method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(
                GETFIELD,
                this.owner,
                this.bindingField(slot),
                this.bindingDescriptor(slot)
        );
    }

    void invokeContext(MethodVisitor method, String name, String descriptor) {
        method.visitMethodInsn(INVOKEVIRTUAL, this.abi.contextOwner(), name, descriptor, false);
    }

    void loadContextInt(MethodVisitor method, String accessor) {
        this.loadContext(method);
        this.invokeContext(method, accessor, "()I");
    }

    void loadWorldGenerationContext(MethodVisitor method) {
        this.loadContext(method);
        this.invokeContext(
                method,
                SurfaceRuntimeAbi.WORLD_CONTEXT,
                Type.getMethodDescriptor(Type.getType(WorldGenerationContext.class))
        );
    }

    void loadSurfaceSystem(MethodVisitor method) {
        this.loadContext(method);
        this.invokeContext(
                method,
                SurfaceRuntimeAbi.SURFACE_SYSTEM,
                Type.getMethodDescriptor(Type.getType(SurfaceSystem.class))
        );
    }

    static String sampledField(int bank) {
        return "columnSampled$" + bank;
    }

    static String valuesField(int bank) {
        return "columnValues$" + bank;
    }

    static String noiseSampleMethod(int sample) {
        return "sampleNoise$" + sample;
    }
}
