/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.codegen;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.*;
import com.moepus.byepregen.dfc.codegen.BindingRegistry.FieldRef;
import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import com.moepus.byepregen.dfc.runtime.ColumnMath;
import com.moepus.byepregen.dfc.runtime.SplineProgram;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class PointMethodEmitter {
    static final String DESC = Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.INT_TYPE,
            Type.INT_TYPE, Type.INT_TYPE, Type.getType(ColumnEvaluationContext.class));
    private static final String CONTEXT = Type.getInternalName(ColumnEvaluationContext.class);
    private static final String COLUMN_MATH = Type.getInternalName(ColumnMath.class);
    private static final String NOISE_HOLDER = Type.getInternalName(DensityFunction.NoiseHolder.class);
    private static final String DENSITY_FUNCTION = Type.getInternalName(DensityFunction.class);
    private static final String SPLINE_PROGRAM = Type.getInternalName(SplineProgram.class);

    private final String owner;
    private final ClassWriter writer;
    private final BindingRegistry bindings;
    private final Map<AstNode, String> methods = new IdentityHashMap<>();
    private final Map<DensityFunction, Integer> interpolationSlots = new IdentityHashMap<>();
    private final Map<SplineNode, Integer> splineSlots = new IdentityHashMap<>();
    private final Map<SplineNode, SplineProgram> splinePrograms = new IdentityHashMap<>();

    PointMethodEmitter(String owner, ClassWriter writer, BindingRegistry bindings) {
        this.owner = owner;
        this.writer = writer;
        this.bindings = bindings;
    }

    String method(AstNode node) {
        String existing = this.methods.get(node);
        if (existing != null) return existing;
        String name = "point" + this.methods.size() + "_" + node.getClass().getSimpleName();
        this.methods.put(node, name);
        this.generate(node, name);
        return name;
    }

    int interpolationCount() {
        return this.interpolationSlots.size();
    }

    private void generate(AstNode node, String name) {
        MethodVisitor method = this.writer.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                name, DESC, null, null);
        method.visitCode();
        this.emit(node, method);
        method.visitInsn(Opcodes.DRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private void emit(AstNode node, MethodVisitor method) {
        if (node instanceof RootNode root) this.call(method, root.next());
        else if (node instanceof ConstantNode constant) method.visitLdcInsn(constant.value());
        else if (node instanceof CoordinateNode coordinate) emitCoordinate(method, coordinate.axis());
        else if (node instanceof Memoized2DNode memoized) this.emitMemoized(method, memoized);
        else if (node instanceof SourceNode source) this.emitSource(method, source);
        else if (node instanceof DelegateNode delegate) this.emitDelegate(method, delegate);
        else if (node instanceof RangeChoiceNode range) this.emitRange(method, range);
        else if (node instanceof YClampedGradientNode gradient) emitGradient(method, gradient);
        else if (node instanceof NoiseNode noise) this.emitNoise(method, noise);
        else if (node instanceof WeirdScaledNode weird) this.emitWeird(method, weird);
        else if (node instanceof SplineNode spline) this.emitSpline(method, spline);
        else if (node instanceof UnaryNode unary) this.emitUnary(method, unary);
        else if (node instanceof BinaryNode binary) this.emitBinary(method, binary);
        else throw new UnsupportedOperationException("Unsupported column AST node " + node.getClass().getName());
    }

    private void emitUnary(MethodVisitor method, UnaryNode node) {
        this.call(method, node.operand());
        if (node instanceof AbsNode) invokeUnaryMath(method, "abs");
        else if (node instanceof NegNode) method.visitInsn(Opcodes.DNEG);
        else if (node instanceof SquareNode) {
            method.visitInsn(Opcodes.DUP2);
            method.visitInsn(Opcodes.DMUL);
        } else if (node instanceof CubeNode) {
            method.visitInsn(Opcodes.DUP2);
            method.visitInsn(Opcodes.DUP2);
            method.visitInsn(Opcodes.DMUL);
            method.visitInsn(Opcodes.DMUL);
        } else if (node instanceof SqueezeNode) {
            method.visitMethodInsn(Opcodes.INVOKESTATIC, COLUMN_MATH, "squeeze", "(D)D", false);
        } else if (node instanceof NegMulNode negMul) {
            emitNegMul(method, negMul.multiplier());
        } else {
            throw new UnsupportedOperationException("Unsupported unary node " + node.getClass().getName());
        }
    }

    private static void emitNegMul(MethodVisitor method, double multiplier) {
        Label positive = new Label();
        Label end = new Label();
        method.visitVarInsn(Opcodes.DSTORE, 5);
        method.visitVarInsn(Opcodes.DLOAD, 5);
        method.visitInsn(Opcodes.DCONST_0);
        method.visitInsn(Opcodes.DCMPL);
        method.visitJumpInsn(Opcodes.IFGT, positive);
        method.visitVarInsn(Opcodes.DLOAD, 5);
        method.visitLdcInsn(multiplier);
        method.visitInsn(Opcodes.DMUL);
        method.visitJumpInsn(Opcodes.GOTO, end);
        method.visitLabel(positive);
        method.visitVarInsn(Opcodes.DLOAD, 5);
        method.visitLabel(end);
    }

    private void emitBinary(MethodVisitor method, BinaryNode node) {
        if (node instanceof MinShortNode min) {
            this.emitShortMin(method, min);
            return;
        }
        if (node instanceof MaxShortNode max) {
            this.emitShortMax(method, max);
            return;
        }
        this.call(method, node.left());
        this.call(method, node.right());
        if (node instanceof AddNode) method.visitInsn(Opcodes.DADD);
        else if (node instanceof MulNode) method.visitInsn(Opcodes.DMUL);
        else if (node instanceof DivNode) method.visitInsn(Opcodes.DDIV);
        else if (node instanceof MinNode) invokeBinaryMath(method, "min");
        else if (node instanceof MaxNode) invokeBinaryMath(method, "max");
        else throw new UnsupportedOperationException("Unsupported binary node " + node.getClass().getName());
    }

    private void emitShortMin(MethodVisitor method, MinShortNode node) {
        Label cached = new Label();
        Label end = new Label();
        this.call(method, node.left());
        method.visitVarInsn(Opcodes.DSTORE, 5);
        method.visitVarInsn(Opcodes.DLOAD, 5);
        method.visitLdcInsn(node.rightMin());
        method.visitInsn(Opcodes.DCMPG);
        method.visitJumpInsn(Opcodes.IFLT, cached);
        method.visitVarInsn(Opcodes.DLOAD, 5);
        this.call(method, node.right());
        invokeBinaryMath(method, "min");
        method.visitJumpInsn(Opcodes.GOTO, end);
        method.visitLabel(cached);
        method.visitVarInsn(Opcodes.DLOAD, 5);
        method.visitLabel(end);
    }

    private void emitShortMax(MethodVisitor method, MaxShortNode node) {
        Label cached = new Label();
        Label end = new Label();
        this.call(method, node.left());
        method.visitVarInsn(Opcodes.DSTORE, 5);
        method.visitVarInsn(Opcodes.DLOAD, 5);
        method.visitLdcInsn(node.rightMax());
        method.visitInsn(Opcodes.DCMPL);
        method.visitJumpInsn(Opcodes.IFGT, cached);
        method.visitVarInsn(Opcodes.DLOAD, 5);
        this.call(method, node.right());
        invokeBinaryMath(method, "max");
        method.visitJumpInsn(Opcodes.GOTO, end);
        method.visitLabel(cached);
        method.visitVarInsn(Opcodes.DLOAD, 5);
        method.visitLabel(end);
    }

    private void emitRange(MethodVisitor method, RangeChoiceNode node) {
        Label outside = new Label();
        Label end = new Label();
        this.call(method, node.input());
        method.visitVarInsn(Opcodes.DSTORE, 5);
        method.visitVarInsn(Opcodes.DLOAD, 5);
        method.visitLdcInsn(node.minInclusive());
        method.visitInsn(Opcodes.DCMPL);
        method.visitJumpInsn(Opcodes.IFLT, outside);
        method.visitVarInsn(Opcodes.DLOAD, 5);
        method.visitLdcInsn(node.maxExclusive());
        method.visitInsn(Opcodes.DCMPG);
        method.visitJumpInsn(Opcodes.IFGE, outside);
        this.callOrInput(method, node.whenInRange(), node.input());
        method.visitJumpInsn(Opcodes.GOTO, end);
        method.visitLabel(outside);
        this.callOrInput(method, node.whenOutOfRange(), node.input());
        method.visitLabel(end);
    }

    private void callOrInput(MethodVisitor method, AstNode selected, AstNode input) {
        if (selected == input) method.visitVarInsn(Opcodes.DLOAD, 5);
        else this.call(method, selected);
    }

    private void emitNoise(MethodVisitor method, NoiseNode node) {
        FieldRef field = this.bindings.field(node.noise(), DensityFunction.NoiseHolder.class, false);
        this.loadField(method, field);
        this.call(method, node.inputX());
        this.call(method, node.inputY());
        this.call(method, node.inputZ());
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, NOISE_HOLDER, "getValue", "(DDD)D", false);
    }

    private void emitWeird(MethodVisitor method, WeirdScaledNode node) {
        this.call(method, node.input());
        method.visitVarInsn(Opcodes.DSTORE, 5);
        String mapper = Type.getInternalName(DensityFunctions.WeirdScaledSampler.RarityValueMapper.class);
        method.visitFieldInsn(Opcodes.GETSTATIC, mapper, node.mapper().name(), 'L' + mapper + ';');
        method.visitVarInsn(Opcodes.DLOAD, 5);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, COLUMN_MATH, "rarity", "(L" + mapper + ";D)D", false);
        method.visitVarInsn(Opcodes.DSTORE, 7);
        FieldRef field = this.bindings.field(node.noise(), DensityFunction.NoiseHolder.class, false);
        this.loadField(method, field);
        emitCoordinateDividedBy(method, 1, 7);
        emitCoordinateDividedBy(method, 2, 7);
        emitCoordinateDividedBy(method, 3, 7);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, NOISE_HOLDER, "getValue", "(DDD)D", false);
        invokeUnaryMath(method, "abs");
        method.visitVarInsn(Opcodes.DLOAD, 7);
        method.visitInsn(Opcodes.DMUL);
    }

    private void emitDelegate(MethodVisitor method, DelegateNode node) {
        FieldRef field = this.bindings.field(node.delegate(), DensityFunction.class, true);
        method.visitVarInsn(Opcodes.ALOAD, 4);
        this.loadField(method, field);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "delegateValue",
                "(L" + DENSITY_FUNCTION + ";III)D", false);
    }

    private void emitSource(MethodVisitor method, SourceNode node) {
        method.visitVarInsn(Opcodes.ALOAD, 4);
        if (node.mode() == SourceMode.INTERPOLATED) {
            int slot = this.interpolationSlot(node.source());
            this.ensureInterpolationToken(node.source());
            FieldRef field = this.bindings.interpolatedField(node.source(), slot);
            ColumnClassBuilder.pushInt(method, slot);
            this.loadField(method, field);
            method.visitVarInsn(Opcodes.ILOAD, 2);
            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "interpolatedValue",
                    "(IL" + DENSITY_FUNCTION + ";I)D", false);
            return;
        }
        FieldRef field = this.bindings.field(node.source(), DensityFunction.class, true);
        this.loadField(method, field);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "flatValue",
                "(L" + DENSITY_FUNCTION + ";III)D", false);
    }

    private void emitSpline(MethodVisitor method, SplineNode node) {
        SplineProgram program = this.splinePrograms.computeIfAbsent(node, SplineProgram::compile);
        FieldRef field = this.bindings.field(program, SplineProgram.class, false);
        method.visitVarInsn(Opcodes.ALOAD, 4);
        ColumnClassBuilder.pushInt(method, this.splineSlot(node));
        ColumnClassBuilder.pushInt(method, program.coordinateCount());
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "splineCoordinates", "(II)[F", false);
        method.visitVarInsn(Opcodes.ASTORE, 5);
        AstNode[] coordinates = node.children();
        for (int i = 0; i < coordinates.length; ++i) {
            method.visitVarInsn(Opcodes.ALOAD, 5);
            ColumnClassBuilder.pushInt(method, i);
            this.call(method, coordinates[i]);
            method.visitInsn(Opcodes.D2F);
            method.visitInsn(Opcodes.FASTORE);
        }
        this.loadField(method, field);
        method.visitVarInsn(Opcodes.ALOAD, 5);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SPLINE_PROGRAM, "sample", "([F)F", false);
        method.visitInsn(Opcodes.F2D);
    }

    private void emitMemoized(MethodVisitor method, Memoized2DNode node) {
        Label cached = new Label();
        Label end = new Label();
        method.visitVarInsn(Opcodes.ALOAD, 4);
        ColumnClassBuilder.pushInt(method, node.slot());
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "memoizedValueMiss", "(I)Z", false);
        method.visitJumpInsn(Opcodes.IFEQ, cached);
        method.visitVarInsn(Opcodes.ALOAD, 4);
        ColumnClassBuilder.pushInt(method, node.slot());
        this.call(method, node.delegate());
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "setMemoizedValue", "(ID)D", false);
        method.visitJumpInsn(Opcodes.GOTO, end);
        method.visitLabel(cached);
        method.visitVarInsn(Opcodes.ALOAD, 4);
        ColumnClassBuilder.pushInt(method, node.slot());
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT, "memoizedValue", "(I)D", false);
        method.visitLabel(end);
    }

    private static void emitGradient(MethodVisitor method, YClampedGradientNode node) {
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitInsn(Opcodes.I2D);
        method.visitLdcInsn((double) node.fromY());
        method.visitLdcInsn((double) node.toY());
        method.visitLdcInsn(node.fromValue());
        method.visitLdcInsn(node.toValue());
        method.visitMethodInsn(Opcodes.INVOKESTATIC, COLUMN_MATH, "clampedMap", "(DDDDD)D", false);
    }

    private void call(MethodVisitor method, AstNode node) {
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitVarInsn(Opcodes.ALOAD, 4);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, this.owner, this.method(node), DESC, false);
    }

    private void loadField(MethodVisitor method, FieldRef field) {
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, this.owner, field.name(), Type.getDescriptor(field.type()));
    }

    int interpolationSlot(DensityFunction source) {
        return this.interpolationSlots.computeIfAbsent(source, ignored -> this.interpolationSlots.size());
    }

    private int splineSlot(SplineNode node) {
        return this.splineSlots.computeIfAbsent(node, ignored -> this.splineSlots.size());
    }

    void ensureInterpolationToken(DensityFunction source) {
        if (!(source instanceof com.moepus.byepregen.worldgen.arena.InterpolatedMarkerAccess access)) {
            throw new IllegalArgumentException("Interpolated marker is not token-capable: "
                    + source.getClass().getName());
        }
        if (access.byepregen$getInterpolationToken() == null) {
            access.byepregen$setInterpolationToken(new Object());
        }
    }

    private static void emitCoordinate(MethodVisitor method, Axis axis) {
        method.visitVarInsn(Opcodes.ILOAD, axis == Axis.X ? 1 : axis == Axis.Y ? 2 : 3);
        method.visitInsn(Opcodes.I2D);
    }

    private static void emitCoordinateDividedBy(MethodVisitor method, int local, int divisor) {
        method.visitVarInsn(Opcodes.ILOAD, local);
        method.visitInsn(Opcodes.I2D);
        method.visitVarInsn(Opcodes.DLOAD, divisor);
        method.visitInsn(Opcodes.DDIV);
    }

    private static void invokeUnaryMath(MethodVisitor method, String name) {
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", name, "(D)D", false);
    }

    private static void invokeBinaryMath(MethodVisitor method, String name) {
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", name, "(DD)D", false);
    }
}
