/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.column;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.spline.SplineAstNode;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeGen;
import com.ishland.c2me.opts.dfc.common.gen.jvm.InvocationShim;
import com.ishland.c2me.opts.dfc.common.gen.jvm.SplineSupport;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.commons.InstructionAdapter;

final class ColumnSplineBytecodeGen {

    private static final String DESC = Type.getMethodDescriptor(Type.FLOAT_TYPE, Type.INT_TYPE, Type.INT_TYPE,
            Type.INT_TYPE, Type.getType(ColumnEvaluationContext.class));

    private final BytecodeGen.Context owner;
    private final Function<AstNode, String> pointMethods;
    private final Map<CubicSpline<?, ?>, SplineValue> methods = new IdentityHashMap<>();

    ColumnSplineBytecodeGen(BytecodeGen.Context owner, Function<AstNode, String> pointMethods) {
        this.owner = owner;
        this.pointMethods = pointMethods;
    }

    void emitSample(SplineAstNode node, InstructionAdapter m) {
        this.call(m, this.method(node, node.spline));
        m.cast(Type.FLOAT_TYPE, Type.DOUBLE_TYPE);
    }

    private SplineValue method(SplineAstNode root,
                               CubicSpline<DensityFunctions.Spline.Point,
                                       DensityFunctions.Spline.Coordinate> spline) {
        if (spline instanceof CubicSpline.Constant<DensityFunctions.Spline.Point,
                DensityFunctions.Spline.Coordinate> fixed) {
            return new SplineValue(fixed.value(), null);
        }
        SplineValue existing = this.methods.get(spline);
        if (existing != null) return existing;
        String name = this.owner.nextMethodName("ColumnSpline_Spline");
        SplineValue value = new SplineValue(0.0F, name);
        this.methods.put(spline, value);
        this.generate(root, spline, name);
        return value;
    }

    private void generate(SplineAstNode root,
                          CubicSpline<DensityFunctions.Spline.Point,
                                  DensityFunctions.Spline.Coordinate> spline,
                          String name) {
        if (!(spline instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Point,
                DensityFunctions.Spline.Coordinate> impl)) {
            throw new UnsupportedOperationException("Unsupported Column spline: " + spline.getClass().getName());
        }
        InstructionAdapter m = new InstructionAdapter(new AnalyzerAdapter(this.owner.className,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, name, DESC,
                this.owner.classWriter.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, name, DESC, null, null)));
        ColumnPointBytecodeGen.LocalAllocator locals = new ColumnPointBytecodeGen.LocalAllocator(5);
        this.emitImplementation(root, impl, m, locals);
        m.visitMaxs(0, 0);
    }

    private void emitImplementation(SplineAstNode root,
                                    CubicSpline.Multipoint<DensityFunctions.Spline.Point,
                                            DensityFunctions.Spline.Coordinate> impl,
                                    InstructionAdapter m, ColumnPointBytecodeGen.LocalAllocator locals) {
        String locationsField = this.owner.newField(float[].class, impl.locations());
        String derivativesField = this.owner.newField(float[].class, impl.derivatives());
        int point = locals.allocate(Type.FLOAT_TYPE);
        this.callPoint(m, root.children.get(impl.coordinate()));
        m.cast(Type.DOUBLE_TYPE, Type.FLOAT_TYPE);
        m.store(point, Type.FLOAT_TYPE);

        int locations = locals.allocate(Type.getType(float[].class));
        this.loadField(m, locationsField, float[].class); m.store(locations, InstructionAdapter.OBJECT_TYPE);
        int derivatives = locals.allocate(Type.getType(float[].class));
        this.loadField(m, derivativesField, float[].class); m.store(derivatives, InstructionAdapter.OBJECT_TYPE);

        SplineValue[] values = impl.values().stream()
                .map(value -> this.method(root, value))
                .toArray(SplineValue[]::new);
        if (values.length == 1) {
            this.emitOutside(m, point, locations, derivatives, values[0], 0);
            return;
        }
        int range = locals.allocate(Type.INT_TYPE);
        m.load(locations, InstructionAdapter.OBJECT_TYPE);
        m.load(point, Type.FLOAT_TYPE);
        m.invokestatic(Type.getInternalName(SplineSupport.class), "findRangeForLocation",
                Type.getMethodDescriptor(Type.INT_TYPE, Type.getType(float[].class), Type.FLOAT_TYPE), false);
        m.store(range, Type.INT_TYPE);
        this.emitRangeSwitch(m, locals, point, range, locations, derivatives, values);
    }

    private void emitOutside(InstructionAdapter m, int point, int locations, int derivatives,
                             SplineValue value, int index) {
        m.load(point, Type.FLOAT_TYPE);
        m.load(locations, InstructionAdapter.OBJECT_TYPE);
        this.call(m, value);
        m.load(derivatives, InstructionAdapter.OBJECT_TYPE);
        m.iconst(index);
        m.invokestatic(Type.getInternalName(SplineSupport.class), "sampleOutsideRange",
                Type.getMethodDescriptor(Type.FLOAT_TYPE, Type.FLOAT_TYPE, Type.getType(float[].class),
                        Type.FLOAT_TYPE, Type.getType(float[].class), Type.INT_TYPE), false);
        m.areturn(Type.FLOAT_TYPE);
    }

    private void emitRangeSwitch(InstructionAdapter m, ColumnPointBytecodeGen.LocalAllocator locals,
                                 int point, int range, int locations, int derivatives, SplineValue[] values) {
        int last = values.length - 1;
        Label lowerOutside = new Label();
        Label upperOutside = new Label();
        Label invalid = new Label();
        Label inside = new Label();
        Label[] middle = new Label[last];
        Label[] targets = new Label[last + 2];
        targets[0] = lowerOutside;
        targets[last + 1] = upperOutside;
        for (int i = 0; i < last; ++i) targets[i + 1] = middle[i] = new Label();
        m.load(range, Type.INT_TYPE);
        m.tableswitch(-1, last, invalid, targets);

        m.visitLabel(lowerOutside);
        this.emitOutside(m, point, locations, derivatives, values[0], 0);
        m.visitLabel(upperOutside);
        this.emitOutside(m, point, locations, derivatives, values[last], last);

        int value0 = locals.allocate(Type.FLOAT_TYPE);
        int value1 = locals.allocate(Type.FLOAT_TYPE);
        this.emitValueCases(m, values, middle, inside, value0, value1);
        m.visitLabel(invalid);
        emitInvalidRange(m);
        m.visitLabel(inside);
        this.emitInside(m, locals, point, range, locations, derivatives, value0, value1);
    }

    private void emitValueCases(InstructionAdapter m, SplineValue[] values, Label[] labels,
                                Label inside, int value0, int value1) {
        boolean[] emitted = new boolean[labels.length];
        for (int i = 0; i < labels.length; ++i) {
            if (emitted[i]) continue;
            m.visitLabel(labels[i]);
            emitted[i] = true;
            for (int j = i + 1; j < labels.length; ++j) {
                if (values[i].equals(values[j]) && values[i + 1].equals(values[j + 1])) {
                    m.visitLabel(labels[j]);
                    emitted[j] = true;
                }
            }
            this.call(m, values[i]);
            if (values[i].equals(values[i + 1])) m.dup();
            else this.call(m, values[i + 1]);
            m.store(value1, Type.FLOAT_TYPE); m.store(value0, Type.FLOAT_TYPE);
            m.goTo(inside);
        }
    }

    private void emitInside(InstructionAdapter m, ColumnPointBytecodeGen.LocalAllocator locals,
                            int point, int range, int locations, int derivatives, int value0, int value1) {
        int loc0 = locals.allocate(Type.FLOAT_TYPE);
        int loc1 = locals.allocate(Type.FLOAT_TYPE);
        int distance = locals.allocate(Type.FLOAT_TYPE);
        int k = locals.allocate(Type.FLOAT_TYPE);
        this.loadFloatAt(m, locations, range, 0); m.store(loc0, Type.FLOAT_TYPE);
        this.loadFloatAt(m, locations, range, 1); m.store(loc1, Type.FLOAT_TYPE);
        m.load(loc1, Type.FLOAT_TYPE); m.load(loc0, Type.FLOAT_TYPE); m.sub(Type.FLOAT_TYPE);
        m.store(distance, Type.FLOAT_TYPE);
        m.load(point, Type.FLOAT_TYPE); m.load(loc0, Type.FLOAT_TYPE); m.sub(Type.FLOAT_TYPE);
        m.load(distance, Type.FLOAT_TYPE); m.div(Type.FLOAT_TYPE); m.store(k, Type.FLOAT_TYPE);
        this.emitHermite(m, locals, range, derivatives, distance, k, value0, value1);
    }

    private void emitHermite(InstructionAdapter m, ColumnPointBytecodeGen.LocalAllocator locals, int range,
                             int derivatives, int distance, int k, int n, int o) {
        int delta = locals.allocate(Type.FLOAT_TYPE);
        int p = locals.allocate(Type.FLOAT_TYPE);
        int q = locals.allocate(Type.FLOAT_TYPE);
        m.load(o, Type.FLOAT_TYPE); m.load(n, Type.FLOAT_TYPE); m.sub(Type.FLOAT_TYPE); m.store(delta, Type.FLOAT_TYPE);
        this.loadFloatAt(m, derivatives, range, 0); m.load(distance, Type.FLOAT_TYPE); m.mul(Type.FLOAT_TYPE);
        m.load(delta, Type.FLOAT_TYPE); m.sub(Type.FLOAT_TYPE); m.store(p, Type.FLOAT_TYPE);
        this.loadFloatAt(m, derivatives, range, 1); m.neg(Type.FLOAT_TYPE);
        m.load(distance, Type.FLOAT_TYPE); m.mul(Type.FLOAT_TYPE);
        m.load(delta, Type.FLOAT_TYPE); m.add(Type.FLOAT_TYPE); m.store(q, Type.FLOAT_TYPE);
        m.load(k, Type.FLOAT_TYPE); m.load(n, Type.FLOAT_TYPE); m.load(o, Type.FLOAT_TYPE);
        m.invokestatic(Type.getInternalName(InvocationShim.class), "invokeMathHelperLerp", "(FFF)F", false);
        m.load(k, Type.FLOAT_TYPE); m.fconst(1.0F); m.load(k, Type.FLOAT_TYPE); m.sub(Type.FLOAT_TYPE);
        m.mul(Type.FLOAT_TYPE); m.load(k, Type.FLOAT_TYPE); m.load(p, Type.FLOAT_TYPE); m.load(q, Type.FLOAT_TYPE);
        m.invokestatic(Type.getInternalName(InvocationShim.class), "invokeMathHelperLerp", "(FFF)F", false);
        m.mul(Type.FLOAT_TYPE); m.add(Type.FLOAT_TYPE); m.areturn(Type.FLOAT_TYPE);
    }

    private void callPoint(InstructionAdapter m, AstNode node) {
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.load(1, Type.INT_TYPE); m.load(2, Type.INT_TYPE); m.load(3, Type.INT_TYPE);
        m.load(4, InstructionAdapter.OBJECT_TYPE);
        m.invokevirtual(this.owner.className, this.pointMethods.apply(node), ColumnPointBytecodeGen.DESC, false);
    }

    private void call(InstructionAdapter m, SplineValue value) {
        if (value.method() == null) {
            m.fconst(value.constant());
            return;
        }
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.load(1, Type.INT_TYPE); m.load(2, Type.INT_TYPE); m.load(3, Type.INT_TYPE);
        m.load(4, InstructionAdapter.OBJECT_TYPE);
        m.invokevirtual(this.owner.className, value.method(), DESC, false);
    }

    private void loadFloatAt(InstructionAdapter m, int array, int index, int offset) {
        m.load(array, InstructionAdapter.OBJECT_TYPE);
        m.load(index, Type.INT_TYPE);
        if (offset != 0) { m.iconst(offset); m.add(Type.INT_TYPE); }
        m.aload(Type.FLOAT_TYPE);
    }

    private void loadField(InstructionAdapter m, String field, Class<?> type) {
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(this.owner.className, field, Type.getDescriptor(type));
    }

    private static void emitInvalidRange(InstructionAdapter m) {
        m.anew(Type.getType(IllegalStateException.class));
        m.dup();
        m.aconst("Invalid Column spline range");
        m.invokespecial(Type.getInternalName(IllegalStateException.class), "<init>",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class)), false);
        m.athrow();
    }

    private record SplineValue(float constant, String method) {
    }
}
