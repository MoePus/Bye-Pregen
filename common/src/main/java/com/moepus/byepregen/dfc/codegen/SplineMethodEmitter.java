/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.codegen;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.SplineNode;
import com.moepus.byepregen.dfc.codegen.BindingRegistry.FieldRef;
import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import com.moepus.byepregen.dfc.runtime.ColumnMath;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Emits the selected spline path directly, following C2ME's SplineAstNode codegen. */
final class SplineMethodEmitter {
    private static final String DESC = Type.getMethodDescriptor(Type.FLOAT_TYPE, Type.INT_TYPE,
            Type.INT_TYPE, Type.INT_TYPE, Type.getType(ColumnEvaluationContext.class));
    private static final String CONTEXT = Type.getInternalName(ColumnEvaluationContext.class);
    private static final String COLUMN_MATH = Type.getInternalName(ColumnMath.class);

    private final String owner;
    private final ClassWriter writer;
    private final BindingRegistry bindings;
    private final BiConsumer<MethodVisitor, AstNode> pointCaller;
    private final Map<SplineNode, Map<CubicSpline<?, ?>, SplineValue>> methods = new IdentityHashMap<>();
    private int methodCount;

    SplineMethodEmitter(GenerationContext context, BiConsumer<MethodVisitor, AstNode> pointCaller) {
        this.owner = context.owner();
        this.writer = context.writer();
        this.bindings = context.bindings();
        this.pointCaller = pointCaller;
    }

    void emitSample(SplineNode node, MethodVisitor method) {
        this.callValue(method, this.method(node, node.spline()));
        method.visitInsn(Opcodes.F2D);
    }

    private SplineValue method(
            SplineNode root,
            CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline
    ) {
        if (spline instanceof CubicSpline.Constant<DensityFunctions.Spline.Point,
                DensityFunctions.Spline.Coordinate> constant) {
            return new SplineValue(constant.value(), null);
        }
        Map<CubicSpline<?, ?>, SplineValue> rootMethods =
                this.methods.computeIfAbsent(root, ignored -> new HashMap<>());
        SplineValue existing = rootMethods.get(spline);
        if (existing != null) return existing;
        String name = "spline" + this.methodCount++;
        SplineValue value = new SplineValue(0.0F, name);
        rootMethods.put(spline, value);
        this.generate(root, spline, name);
        return value;
    }

    private void generate(
            SplineNode root,
            CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline,
            String name
    ) {
        if (!(spline instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Point,
                DensityFunctions.Spline.Coordinate> points)) {
            throw new IllegalArgumentException("Unsupported spline " + spline.getClass().getName());
        }
        MethodVisitor method = this.writer.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                name, DESC, null, null);
        method.visitCode();
        this.emitMultipoint(root, points, method);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private void emitMultipoint(
            SplineNode root,
            CubicSpline.Multipoint<DensityFunctions.Spline.Point,
                    DensityFunctions.Spline.Coordinate> points,
            MethodVisitor method
    ) {
        this.callPoint(method, root.coordinateNode(points.coordinate()));
        method.visitInsn(Opcodes.D2F);
        method.visitVarInsn(Opcodes.FSTORE, 5);
        FieldRef locations = this.bindings.field(points.locations().clone(), float[].class, false);
        FieldRef derivatives = this.bindings.field(points.derivatives().clone(), float[].class, false);
        this.loadField(method, locations);
        method.visitVarInsn(Opcodes.ASTORE, 6);
        this.loadField(method, derivatives);
        method.visitVarInsn(Opcodes.ASTORE, 7);
        SplineValue[] values = new SplineValue[points.values().size()];
        for (int i = 0; i < values.length; ++i) {
            values[i] = this.method(root, points.values().get(i));
        }
        if (values.length == 1) {
            this.emitOutside(method, values[0], 0);
            return;
        }
        method.visitVarInsn(Opcodes.ALOAD, 6);
        method.visitVarInsn(Opcodes.FLOAD, 5);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, COLUMN_MATH,
                "findSplineRange", "([FF)I", false);
        method.visitVarInsn(Opcodes.ISTORE, 8);
        this.emitRangeSwitch(method, values);
    }

    private void emitRangeSwitch(MethodVisitor method, SplineValue[] values) {
        int last = values.length - 1;
        Label lower = new Label();
        Label upper = new Label();
        Label invalid = new Label();
        Label inside = new Label();
        Label[] middle = new Label[last];
        Label[] targets = new Label[last + 2];
        targets[0] = lower;
        targets[last + 1] = upper;
        for (int i = 0; i < last; ++i) targets[i + 1] = middle[i] = new Label();
        method.visitVarInsn(Opcodes.ILOAD, 8);
        method.visitTableSwitchInsn(-1, last, invalid, targets);
        method.visitLabel(lower);
        this.emitOutside(method, values[0], 0);
        method.visitLabel(upper);
        this.emitOutside(method, values[last], last);
        this.emitValueCases(method, values, middle, inside);
        method.visitLabel(invalid);
        emitInvalidRange(method);
        method.visitLabel(inside);
        this.emitInside(method);
    }

    private void emitValueCases(
            MethodVisitor method,
            SplineValue[] values,
            Label[] labels,
            Label inside
    ) {
        boolean[] emitted = new boolean[labels.length];
        for (int i = 0; i < labels.length; ++i) {
            if (emitted[i]) continue;
            method.visitLabel(labels[i]);
            emitted[i] = true;
            for (int j = i + 1; j < labels.length; ++j) {
                if (values[i].equals(values[j]) && values[i + 1].equals(values[j + 1])) {
                    method.visitLabel(labels[j]);
                    emitted[j] = true;
                }
            }
            this.callValue(method, values[i]);
            if (values[i].equals(values[i + 1])) method.visitInsn(Opcodes.DUP);
            else this.callValue(method, values[i + 1]);
            method.visitVarInsn(Opcodes.FSTORE, 10);
            method.visitVarInsn(Opcodes.FSTORE, 9);
            method.visitJumpInsn(Opcodes.GOTO, inside);
        }
    }

    private void emitOutside(MethodVisitor method, SplineValue value, int index) {
        this.callValue(method, value);
        method.visitVarInsn(Opcodes.FSTORE, 9);
        loadFloat(method, 7, index);
        method.visitVarInsn(Opcodes.FSTORE, 10);
        Label constant = new Label();
        method.visitVarInsn(Opcodes.FLOAD, 10);
        method.visitInsn(Opcodes.FCONST_0);
        method.visitInsn(Opcodes.FCMPL);
        method.visitJumpInsn(Opcodes.IFEQ, constant);
        method.visitVarInsn(Opcodes.FLOAD, 9);
        method.visitVarInsn(Opcodes.FLOAD, 10);
        method.visitVarInsn(Opcodes.FLOAD, 5);
        loadFloat(method, 6, index);
        method.visitInsn(Opcodes.FSUB);
        method.visitInsn(Opcodes.FMUL);
        method.visitInsn(Opcodes.FADD);
        method.visitInsn(Opcodes.FRETURN);
        method.visitLabel(constant);
        method.visitVarInsn(Opcodes.FLOAD, 9);
        method.visitInsn(Opcodes.FRETURN);
    }

    private void emitInside(MethodVisitor method) {
        loadFloat(method, 6, 8, 0);
        method.visitVarInsn(Opcodes.FSTORE, 11);
        loadFloat(method, 6, 8, 1);
        method.visitVarInsn(Opcodes.FSTORE, 12);
        method.visitVarInsn(Opcodes.FLOAD, 12);
        method.visitVarInsn(Opcodes.FLOAD, 11);
        method.visitInsn(Opcodes.FSUB);
        method.visitVarInsn(Opcodes.FSTORE, 13);
        method.visitVarInsn(Opcodes.FLOAD, 5);
        method.visitVarInsn(Opcodes.FLOAD, 11);
        method.visitInsn(Opcodes.FSUB);
        method.visitVarInsn(Opcodes.FLOAD, 13);
        method.visitInsn(Opcodes.FDIV);
        method.visitVarInsn(Opcodes.FSTORE, 14);
        this.emitHermite(method);
    }

    private void emitHermite(MethodVisitor method) {
        method.visitVarInsn(Opcodes.FLOAD, 10);
        method.visitVarInsn(Opcodes.FLOAD, 9);
        method.visitInsn(Opcodes.FSUB);
        method.visitVarInsn(Opcodes.FSTORE, 15);
        loadFloat(method, 7, 8, 0);
        method.visitVarInsn(Opcodes.FLOAD, 13);
        method.visitInsn(Opcodes.FMUL);
        method.visitVarInsn(Opcodes.FLOAD, 15);
        method.visitInsn(Opcodes.FSUB);
        method.visitVarInsn(Opcodes.FSTORE, 16);
        loadFloat(method, 7, 8, 1);
        method.visitInsn(Opcodes.FNEG);
        method.visitVarInsn(Opcodes.FLOAD, 13);
        method.visitInsn(Opcodes.FMUL);
        method.visitVarInsn(Opcodes.FLOAD, 15);
        method.visitInsn(Opcodes.FADD);
        method.visitVarInsn(Opcodes.FSTORE, 17);
        method.visitVarInsn(Opcodes.FLOAD, 9);
        method.visitVarInsn(Opcodes.FLOAD, 14);
        method.visitVarInsn(Opcodes.FLOAD, 15);
        method.visitInsn(Opcodes.FCONST_1);
        method.visitVarInsn(Opcodes.FLOAD, 14);
        method.visitInsn(Opcodes.FSUB);
        method.visitVarInsn(Opcodes.FLOAD, 16);
        method.visitVarInsn(Opcodes.FLOAD, 14);
        method.visitVarInsn(Opcodes.FLOAD, 17);
        method.visitVarInsn(Opcodes.FLOAD, 16);
        method.visitInsn(Opcodes.FSUB);
        method.visitInsn(Opcodes.FMUL);
        method.visitInsn(Opcodes.FADD);
        method.visitInsn(Opcodes.FMUL);
        method.visitInsn(Opcodes.FADD);
        method.visitInsn(Opcodes.FMUL);
        method.visitInsn(Opcodes.FADD);
        method.visitInsn(Opcodes.FRETURN);
    }

    private void callPoint(MethodVisitor method, AstNode node) {
        this.pointCaller.accept(method, node);
    }

    private void callValue(MethodVisitor method, SplineValue value) {
        if (value.method() == null) {
            method.visitLdcInsn(value.constant());
            return;
        }
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitVarInsn(Opcodes.ALOAD, 4);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, this.owner, value.method(), DESC, false);
    }

    private void loadField(MethodVisitor method, FieldRef field) {
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, this.owner, field.name(), Type.getDescriptor(field.type()));
    }

    private static void loadFloat(MethodVisitor method, int array, int index) {
        method.visitVarInsn(Opcodes.ALOAD, array);
        ColumnClassBuilder.pushInt(method, index);
        method.visitInsn(Opcodes.FALOAD);
    }

    private static void loadFloat(MethodVisitor method, int array, int index, int offset) {
        method.visitVarInsn(Opcodes.ALOAD, array);
        method.visitVarInsn(Opcodes.ILOAD, index);
        if (offset != 0) {
            ColumnClassBuilder.pushInt(method, offset);
            method.visitInsn(Opcodes.IADD);
        }
        method.visitInsn(Opcodes.FALOAD);
    }

    private static void emitInvalidRange(MethodVisitor method) {
        method.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
        method.visitInsn(Opcodes.DUP);
        method.visitLdcInsn("Invalid density column spline range");
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException",
                "<init>", "(Ljava/lang/String;)V", false);
        method.visitInsn(Opcodes.ATHROW);
    }

    private record SplineValue(float constant, String method) {
    }
}
