/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.codegen;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import com.moepus.byepregen.dfc.runtime.ColumnTemplate;
import com.moepus.byepregen.dfc.runtime.CompiledColumnEvaluator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class ColumnClassBuilder {
    private static final AtomicLong CLASS_IDS = new AtomicLong();
    private static final String GENERATED_PACKAGE = "com/moepus/byepregen/dfc/codegen/";
    private static final String CONTEXT = Type.getInternalName(ColumnEvaluationContext.class);
    private static final String EVALUATOR = Type.getInternalName(CompiledColumnEvaluator.class);
    private static final String EVAL_DESC = Type.getMethodDescriptor(
            Type.VOID_TYPE, Type.getType(ColumnEvaluationContext.class));

    private final String className = GENERATED_PACKAGE + "GeneratedColumn$" + CLASS_IDS.incrementAndGet();
    private final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    private final BindingRegistry bindings = new BindingRegistry(this.writer);
    private final PointMethodEmitter points;
    private final ColumnMethodEmitter columns;
    private final int memoizedSlots;

    public ColumnClassBuilder(int memoizedSlots) {
        this.memoizedSlots = memoizedSlots;
        GenerationContext context = new GenerationContext(
                this.className, this.writer, this.bindings);
        this.points = new PointMethodEmitter(context);
        this.columns = new ColumnMethodEmitter(context, this.points);
        this.writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                this.className, null, "java/lang/Object", new String[]{EVALUATOR});
    }

    public BuildResult build(AstNode root) {
        String rootMethod = this.columns.method(root);
        this.emitEvalColumn(rootMethod);
        this.emitConstructor();
        this.writer.visitEnd();
        return new BuildResult(this.className, this.writer.toByteArray(), this.bindings.bindings());
    }

    private void emitConstructor() {
        MethodVisitor method = this.writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "([Ljava/lang/Object;)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        List<BindingRegistry.FieldRef> fields = this.bindings.fields();
        for (int i = 0; i < fields.size(); ++i) this.initializeField(method, fields.get(i), i);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private void initializeField(MethodVisitor method, BindingRegistry.FieldRef field, int index) {
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        pushInt(method, index);
        method.visitInsn(Opcodes.AALOAD);
        method.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(field.type()));
        method.visitFieldInsn(Opcodes.PUTFIELD, this.className, field.name(), Type.getDescriptor(field.type()));
    }

    private void emitEvalColumn(String rootMethod) {
        MethodVisitor method = this.writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                "evalColumn", EVAL_DESC, null, null);
        method.visitCode();
        this.prepareContext(method);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "output", "()[D", false);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, this.className, rootMethod,
                ColumnMethodEmitter.DESC, false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private void prepareContext(MethodVisitor method) {
        method.visitVarInsn(Opcodes.ALOAD, 1);
        pushInt(method, this.memoizedSlots);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "prepareMemoizedCount", "(I)V", false);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        pushInt(method, this.points.interpolationCount());
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "prepareInterpolationCount", "(I)V", false);
    }

    static void pushInt(MethodVisitor method, int value) {
        method.visitLdcInsn(value);
    }

    public record BuildResult(
            String internalClassName,
            byte[] classBytes,
            List<ColumnTemplate.Binding> bindings
    ) {
    }
}
