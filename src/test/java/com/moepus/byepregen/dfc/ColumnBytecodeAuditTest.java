package com.moepus.byepregen.dfc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moepus.byepregen.dfc.ast.AstNodes.ConstantNode;
import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.CoordinateNode;
import com.moepus.byepregen.dfc.ast.AstNodes.Axis;
import com.moepus.byepregen.dfc.ast.AstNodes.MaxShortNode;
import com.moepus.byepregen.dfc.ast.AstNodes.Memoized2DNode;
import com.moepus.byepregen.dfc.ast.AstNodes.MinShortNode;
import com.moepus.byepregen.dfc.ast.AstNodes.MulNode;
import com.moepus.byepregen.dfc.ast.AstNodes.NoiseNode;
import com.moepus.byepregen.dfc.ast.AstNodes.RangeChoiceNode;
import com.moepus.byepregen.dfc.ast.AstNodes.SplineNode;
import com.moepus.byepregen.dfc.codegen.ColumnClassBuilder;
import com.moepus.byepregen.dfc.codegen.ColumnClassDefiner;
import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import com.moepus.byepregen.dfc.runtime.CompiledColumnEvaluator;
import com.moepus.byepregen.dfc.runtime.NoiseHolderRuntimeAbi;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;

final class ColumnBytecodeAuditTest {
    @Test
    void hiddenClassExecutesColumnEntry() throws Throwable {
        AstNode graph = new MulNode(
                new com.moepus.byepregen.dfc.ast.AstNodes.AddNode(
                        new CoordinateNode(Axis.Y), new ConstantNode(2.0D)),
                new ConstantNode(3.0D));
        ColumnClassBuilder.BuildResult generated = new ColumnClassBuilder(0).build(graph);
        CompiledColumnEvaluator evaluator = (CompiledColumnEvaluator) ColumnClassDefiner
                .defineConstructor(generated.classBytes()).invoke((Object) new Object[0]);
        ColumnEvaluationContext context = new ColumnEvaluationContext();
        double[] output = new double[3];
        context.prepare(output, 1, 2, -4, 4, source -> new double[3]);
        try {
            evaluator.evalColumn(context);
        } finally {
            context.clear();
        }
        assertTrue(java.util.Arrays.equals(new double[]{-6.0D, 6.0D, 18.0D}, output));
    }

    @Test
    void gradientColumnHelperAvoidsPointFallback() throws Throwable {
        AstNode graph = new com.moepus.byepregen.dfc.ast.AstNodes.YClampedGradientNode(
                -4, 4, -1.0D, 1.0D);
        ColumnClassBuilder.BuildResult generated = new ColumnClassBuilder(0).build(graph);
        ClassNode type = read(generated.classBytes());
        MethodNode gradient = find(type, "column", "YClampedGradientNode");
        assertNotNull(gradient);
        assertEquals(0, countPointCalls(gradient));
        assertTrue(calls(gradient, com.moepus.byepregen.dfc.runtime.ColumnMath.class, "clampedMap"));

        CompiledColumnEvaluator evaluator = instantiate(generated);
        double[] output = new double[3];
        ColumnEvaluationContext context = new ColumnEvaluationContext();
        context.prepare(output, 0, 0, -4, 4, source -> output);
        try {
            evaluator.evalColumn(context);
        } finally {
            context.clear();
        }
        assertTrue(java.util.Arrays.equals(new double[]{-1.0D, 0.0D, 1.0D}, output));
    }

    @Test
    void noiseCallUsesResolvedRuntimeAbi() {
        DensityFunction.NoiseHolder holder = new DensityFunction.NoiseHolder(
                Holder.direct(new NormalNoise.NoiseParameters(0, 1.0D))
        );
        AstNode graph = new NoiseNode(
                new CoordinateNode(Axis.X),
                new CoordinateNode(Axis.Y),
                new CoordinateNode(Axis.Z),
                holder
        );
        ClassNode generated = read(new ColumnClassBuilder(0).build(graph).classBytes());
        MethodNode noise = find(generated, "point", "NoiseNode");

        assertNotNull(noise);
        assertTrue(calls(noise, DensityFunction.NoiseHolder.class,
                NoiseHolderRuntimeAbi.valueMethodName()));
    }

    @Test
    void shortMinUsesHybridColumnAndInlinesConstants() {
        ColumnClassBuilder.BuildResult generated = new ColumnClassBuilder(0).build(
                new MinShortNode(new MulNode(new ConstantNode(0.64D), new CoordinateNode(Axis.X)),
                        new CoordinateNode(Axis.Y), 4.0D));
        ClassNode type = read(generated.classBytes());
        MethodNode mul = find(type, "column", "MulNode");
        MethodNode min = find(type, "column", "MinShortNode");

        assertNotNull(mul);
        assertNotNull(min);
        assertTrue(containsOpcode(mul, org.objectweb.asm.Opcodes.DMUL));
        assertTrue(containsJump(min), "MinShort column helper must retain lane short-circuiting");
        assertTrue(countCallsWithPrefix(min, "column") > 1,
                "MinShort must call both sides through range-aware column helpers");
        assertEquals(0, countCallsWithPrefix(min, "point"));
        assertTrue(containsDoubleConstant(mul, 0.64D));
        assertFalse(calls(mul, ColumnEvaluationContext.class, "borrowDoubleArray"),
                "constant-sided arithmetic should update the output in one loop");
        assertEquals(0, type.methods.stream()
                .filter(method -> method.name.startsWith("point")
                        && method.name.endsWith("MinShortNode"))
                .count());
        assertTrue(type.methods.stream().noneMatch(method -> method.name.endsWith("ConstantNode")));
    }

    @Test
    void shortMinHybridColumnEvaluatesOnlySelectedLanes() throws Throwable {
        AstNode graph = new MinShortNode(new CoordinateNode(Axis.Y),
                new CoordinateNode(Axis.X), 0.0D);
        ColumnClassBuilder.BuildResult generated = new ColumnClassBuilder(0).build(graph);
        CompiledColumnEvaluator evaluator = instantiate(generated);
        double[] output = new double[]{0.0D, 0.0D, 0.0D, 0.0D};
        ColumnEvaluationContext context = new ColumnEvaluationContext();
        context.prepare(output, 10, 0, -4, 4, source -> output);
        try {
            evaluator.evalColumn(context);
        } finally {
            context.clear();
        }
        assertEquals(-4.0D, output[0]);
        assertEquals(0.0D, output[1]);
        assertEquals(4.0D, output[2]);
        assertEquals(8.0D, output[3]);
    }

    @Test
    void shortMaxUsesHybridColumn() {
        ClassNode type = read(new ColumnClassBuilder(0).build(
                new MaxShortNode(new CoordinateNode(Axis.Y),
                        new CoordinateNode(Axis.X), 0.0D)).classBytes());
        MethodNode max = find(type, "column", "MaxShortNode");
        assertNotNull(max);
        assertTrue(containsJump(max));
        assertTrue(countCallsWithPrefix(max, "column") > 1);
        assertEquals(0, countCallsWithPrefix(max, "point"));
        assertTrue(type.methods.stream().noneMatch(method -> method.name.startsWith("point")
                && method.name.endsWith("MaxShortNode")));
    }

    @Test
    void constantMinShortConditionsAreSpecializedWithoutPointHelpers() {
        ClassNode eagerType = read(new ColumnClassBuilder(0).build(
                new MinShortNode(new ConstantNode(0.0D),
                        new CoordinateNode(Axis.Y), -4.9294D)).classBytes());
        MethodNode eager = find(eagerType, "column", "MinShortNode");
        assertNotNull(eager);
        assertTrue(countCallsWithPrefix(eager, "column") > 0);
        assertEquals(0, countCallsWithPrefix(eager, "point"));
        assertTrue(eagerType.methods.stream().noneMatch(method -> method.name.startsWith("point")
                && method.name.endsWith("MinShortNode")));

        ClassNode shortedType = read(new ColumnClassBuilder(0).build(
                new MinShortNode(new ConstantNode(-5.0D),
                        new CoordinateNode(Axis.Y), -4.9294D)).classBytes());
        MethodNode shorted = find(shortedType, "column", "MinShortNode");
        assertNotNull(shorted);
        assertFalse(calls(shorted, Math.class, "min"));
        assertTrue(containsDoubleConstant(shorted, -5.0D));
        assertEquals(0, countCallsWithPrefix(shorted, "point"));
    }

    @Test
    void rangeChoiceBoundsAreEmittedAsDoubleConstants() {
        ClassNode type = read(new ColumnClassBuilder(0).build(new RangeChoiceNode(
                new CoordinateNode(Axis.Y), -1000000.0D, 0.0D,
                new ConstantNode(1.0D), new ConstantNode(2.0D))).classBytes());
        MethodNode choice = find(type, "column", "RangeChoiceNode");
        assertNotNull(choice);
        assertTrue(containsDoubleConstant(choice, -1000000.0D));
        assertTrue(containsDoubleConstant(choice, 0.0D));
        assertFalse(containsOpcode(choice, org.objectweb.asm.Opcodes.F2D));
    }

    @Test
    void maxShortAndRangeChoiceRetainConditionalLazyBranches() {
        AstNode range = new RangeChoiceNode(
                new CoordinateNode(Axis.X), 0.0D, 1.0D,
                new MaxShortNode(new CoordinateNode(Axis.Z), new ConstantNode(2.0D), 2.0D),
                new MinShortNode(new CoordinateNode(Axis.Z), new ConstantNode(-2.0D), -2.0D));
        ClassNode type = read(new ColumnClassBuilder(0).build(range).classBytes());

        MethodNode max = find(type, "column", "MaxShortNode");
        MethodNode min = find(type, "column", "MinShortNode");
        MethodNode choice = find(type, "column", "RangeChoiceNode");
        assertNotNull(max);
        assertNotNull(min);
        assertNotNull(choice);
        assertTrue(containsJump(max), "MaxShort must retain lazy right-branch evaluation");
        assertTrue(containsJump(min), "MinShort must retain lazy right-branch evaluation");
        assertTrue(containsJump(choice), "RangeChoice must retain conditional branch evaluation");
        assertEquals(0, countPointCalls(choice));
        assertEquals(3, countCallsWithPrefix(choice, "column"),
                "RangeChoice must retain column calls for input and both lazy branches");
    }

    @Test
    void rangeChoiceEvaluatesArithmeticBranchesAsColumnRuns() throws Throwable {
        AstNode graph = new RangeChoiceNode(new CoordinateNode(Axis.Y), 0.0D, 8.0D,
                new com.moepus.byepregen.dfc.ast.AstNodes.AddNode(
                        new CoordinateNode(Axis.X), new CoordinateNode(Axis.Y)),
                new MulNode(new ConstantNode(2.0D), new CoordinateNode(Axis.Y)));
        ColumnClassBuilder.BuildResult generated = new ColumnClassBuilder(0).build(graph);
        ClassNode type = read(generated.classBytes());
        MethodNode choice = find(type, "column", "RangeChoiceNode");
        MethodNode add = find(type, "column", "AddNode");
        MethodNode mul = find(type, "column", "MulNode");
        assertNotNull(choice);
        assertNotNull(add);
        assertNotNull(mul);
        assertEquals(0, countCallsWithPrefix(choice, "point"));
        assertTrue(add.desc.endsWith("[DII)V"));
        assertTrue(mul.desc.endsWith("[DII)V"));

        CompiledColumnEvaluator evaluator = instantiate(generated);
        double[] output = new double[5];
        ColumnEvaluationContext context = new ColumnEvaluationContext();
        context.prepare(output, 10, 0, -4, 4, source -> output);
        try {
            evaluator.evalColumn(context);
        } finally {
            context.clear();
        }
        assertTrue(java.util.Arrays.equals(new double[]{-8.0D, 10.0D, 14.0D, 16.0D, 24.0D}, output));
    }

    @Test
    void evalColumnUsesDedicatedArrayHelpersForArithmetic() {
        AstNode graph = new MulNode(new CoordinateNode(Axis.Y), new ConstantNode(2.0D));
        ClassNode type = read(new ColumnClassBuilder(0).build(graph).classBytes());
        MethodNode entry = type.methods.stream()
                .filter(method -> method.name.equals("evalColumn"))
                .findFirst().orElseThrow();
        MethodNode mul = find(type, "column", "MulNode");

        assertNotNull(mul);
        assertEquals(1, countCallsWithPrefix(entry, "column"));
        assertEquals(0, countCallsWithPrefix(entry, "point"));
        assertTrue(containsOpcode(mul, org.objectweb.asm.Opcodes.DALOAD));
        assertTrue(containsOpcode(mul, org.objectweb.asm.Opcodes.DASTORE));
        assertTrue(containsOpcode(mul, org.objectweb.asm.Opcodes.DMUL));
    }

    @Test
    void zeroMemoizedSlotsDoNotEmitMemoPreparation() {
        MethodNode entry = find(read(new ColumnClassBuilder(0)
                .build(new CoordinateNode(Axis.Y)).classBytes()), "evalColumn", "");
        assertTrue(calls(entry, ColumnEvaluationContext.class, "assertActive"));
        assertFalse(calls(entry, ColumnEvaluationContext.class, "prepareMemoizedCount"));

        MethodNode memoizedEntry = find(read(new ColumnClassBuilder(1)
                .build(new Memoized2DNode(new CoordinateNode(Axis.X), 0)).classBytes()),
                "evalColumn", "");
        assertTrue(calls(memoizedEntry, ColumnEvaluationContext.class, "prepareMemoizedCount"));
    }

    @Test
    void negativeMemoizedSlotsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ColumnClassBuilder(-1));
    }

    @Test
    void inactiveContextIsRejectedAtColumnEntry() throws Throwable {
        CompiledColumnEvaluator evaluator = instantiate(new ColumnClassBuilder(0)
                .build(new CoordinateNode(Axis.Y)));
        assertThrows(IllegalStateException.class,
                () -> evaluator.evalColumn(new ColumnEvaluationContext()));
    }

    @Test
    void evalColumnOwnsTheOnlyCleanupHandlerAndRethrows() throws Throwable {
        AstNode graph = new MulNode(new CoordinateNode(Axis.Y),
                new Memoized2DNode(new ConstantNode(2.0D), 1));
        ColumnClassBuilder.BuildResult generated = new ColumnClassBuilder(0).build(graph);
        ClassNode type = read(generated.classBytes());
        MethodNode entry = type.methods.stream()
                .filter(method -> method.name.equals("evalColumn"))
                .findFirst().orElseThrow();
        assertEquals(1, entry.tryCatchBlocks.size());
        assertEquals(1, type.methods.stream()
                .mapToInt(method -> method.tryCatchBlocks.size()).sum());
        assertTrue(containsOpcode(entry, org.objectweb.asm.Opcodes.ATHROW));

        CompiledColumnEvaluator evaluator = instantiate(generated);
        ColumnEvaluationContext context = new ColumnEvaluationContext();
        context.prepare(new double[3], 0, 0, 0, 4, source -> new double[3]);
        IndexOutOfBoundsException failure = assertThrows(
                IndexOutOfBoundsException.class, () -> evaluator.evalColumn(context));
        assertTrue(failure.getMessage().contains("memoized index"));
        context.clear();
    }

    @Test
    void directSplineHelpersExecuteAndUseTableSwitch() throws Throwable {
        SplineFixture fixture = splineFixture();
        ColumnClassBuilder.BuildResult generated = new ColumnClassBuilder(0).build(fixture.node());
        CompiledColumnEvaluator evaluator = instantiate(generated);
        double[] actual = new double[7];
        ColumnEvaluationContext context = new ColumnEvaluationContext();
        context.prepare(actual, 3, 5, -6, 2, source -> new double[7]);
        try {
            evaluator.evalColumn(context);
        } finally {
            context.clear();
        }
        for (int lane = 0; lane < actual.length; ++lane) {
            int y = -6 + lane * 2;
            double expected = sampleSpline(fixture.spline(), y);
            assertEquals(expected, actual[lane], 1.0E-6D * (1.0D + Math.abs(expected)));
        }

        byte[] bytes = generated.classBytes();
        assertFalse(new String(bytes, StandardCharsets.ISO_8859_1).contains("SplineProgram"));
        ClassNode type = read(bytes);
        assertTrue(type.methods.stream()
                .filter(method -> method.name.startsWith("spline"))
                .anyMatch(ColumnBytecodeAuditTest::containsTableSwitch));
    }

    @Test
    void memoizedHelperUsesRawSentinelMissContract() throws Exception {
        ClassNode generated = read(new ColumnClassBuilder(1).build(
                new Memoized2DNode(new CoordinateNode(Axis.X), 0)).classBytes());
        MethodNode memoized = find(generated, "point", "Memoized2DNode");
        assertNotNull(memoized);
        assertTrue(calls(memoized, ColumnEvaluationContext.class, "memoizedValueMiss"));
        assertFalse(calls(memoized, ColumnEvaluationContext.class, "memoizedValueReady"));

        ClassNode context = new ClassNode();
        try (var input = ColumnEvaluationContext.class.getResourceAsStream(
                "/" + ColumnEvaluationContext.class.getName().replace('.', '/') + ".class")) {
            new ClassReader(input).accept(context, 0);
        }
        MethodNode miss = context.methods.stream()
                .filter(method -> method.name.equals("memoizedValueMiss"))
                .findFirst().orElseThrow();
        assertTrue(calls(miss, Double.class, "doubleToRawLongBits"));
        assertFalse(calls(miss, Double.class, "doubleToLongBits"));
        assertFalse(calls(miss, Double.class, "isNaN"));
    }

    @Test
    void generatedReachableDescriptorsDoNotContainC2meOrGenericMultiAbi() {
        AstNode graph = new RangeChoiceNode(new CoordinateNode(Axis.Y), 0.0D, 1.0D,
                new MulNode(new CoordinateNode(Axis.X), new ConstantNode(2.0D)),
                new ConstantNode(1.0D));
        byte[] bytes = new ColumnClassBuilder(0).build(graph).classBytes();
        String constants = new String(bytes, StandardCharsets.ISO_8859_1);
        assertFalse(constants.contains("com/ishland/c2me"));
        assertFalse(constants.contains("evalMulti"));
        ClassNode type = new ClassNode();
        new ClassReader(bytes).accept(type, 0);
        type.fields.forEach(field -> assertFalse(field.desc.contains("[I")));
        for (MethodNode method : type.methods) {
            assertFalse(method.desc.contains("[I"));
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    assertFalse(call.owner.contains("com/ishland/c2me"));
                    assertFalse(call.desc.contains("[I"));
                    assertFalse(call.name.equals("evalMulti"));
                    assertFalse(call.owner.contains("NoiseChunk$Cache"));
                }
            }
        }
        Set<String> reachable = reachableMethods(type);
        assertTrue(reachable.stream().anyMatch(name -> name.startsWith("column")));
        assertFalse(reachable.stream().anyMatch(name -> name.startsWith("point")));
        reachable.stream()
                .filter(name -> !name.equals("evalColumn"))
                .forEach(name -> assertTrue(name.startsWith("column") || name.startsWith("point"),
                        "unexpected generated helper reachable from evalColumn: " + name));
    }

    private static Set<String> reachableMethods(ClassNode type) {
        Map<String, MethodNode> methods = new HashMap<>();
        type.methods.forEach(method -> methods.put(method.name + method.desc, method));
        MethodNode entry = type.methods.stream()
                .filter(method -> method.name.equals("evalColumn"))
                .findFirst().orElseThrow();
        Set<String> names = new HashSet<>();
        Set<String> visited = new HashSet<>();
        ArrayDeque<MethodNode> pending = new ArrayDeque<>();
        pending.add(entry);
        while (!pending.isEmpty()) {
            MethodNode method = pending.removeFirst();
            if (!visited.add(method.name + method.desc)) continue;
            names.add(method.name);
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(type.name)) {
                    MethodNode target = methods.get(call.name + call.desc);
                    if (target != null) pending.add(target);
                }
            }
        }
        return names;
    }

    private static MethodNode find(ClassNode type, String prefix, String suffix) {
        return type.methods.stream()
                .filter(method -> method.name.startsWith(prefix) && method.name.endsWith(suffix))
                .findFirst().orElse(null);
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode type = new ClassNode();
        new ClassReader(bytes).accept(type, 0);
        return type;
    }

    private static CompiledColumnEvaluator instantiate(
            ColumnClassBuilder.BuildResult generated
    ) throws Throwable {
        Object[] values = generated.bindings().stream()
                .map(com.moepus.byepregen.dfc.runtime.ColumnTemplate.Binding::value)
                .toArray();
        return (CompiledColumnEvaluator) ColumnClassDefiner
                .defineConstructor(generated.classBytes()).invoke((Object) values);
    }

    private static SplineFixture splineFixture() {
        DensityFunctions.Spline.Coordinate coordinate = SplineTestFixtures.coordinate();
        CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> child =
                new CubicSpline.Multipoint<>(coordinate, new float[]{-2.0F, 2.0F},
                        List.of(CubicSpline.constant(-1.0F), CubicSpline.constant(3.0F)),
                        new float[]{0.25F, -0.5F}, -1.0F, 3.0F);
        CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> root =
                new CubicSpline.Multipoint<>(coordinate, new float[]{-4.0F, 0.0F, 4.0F},
                        List.of(CubicSpline.constant(-5.0F), child, CubicSpline.constant(7.0F)),
                        new float[]{0.0F, 0.5F, 0.0F}, -5.0F, 7.0F);
        return new SplineFixture(
                new SplineNode(root, List.of(coordinate),
                        List.of(new CoordinateNode(Axis.Y))),
                root);
    }

    private static float sampleSpline(
            CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline,
            float point
    ) {
        if (spline instanceof CubicSpline.Constant<DensityFunctions.Spline.Point,
                DensityFunctions.Spline.Coordinate> constant) return constant.value();
        CubicSpline.Multipoint<DensityFunctions.Spline.Point,
                DensityFunctions.Spline.Coordinate> values = (CubicSpline.Multipoint<
                DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate>) spline;
        int range = referenceRange(values.locations(), point);
        int last = values.locations().length - 1;
        if (range < 0 || range == last) {
            int index = range < 0 ? 0 : last;
            return sampleSpline(values.values().get(index), point)
                    + values.derivatives()[index] * (point - values.locations()[index]);
        }
        return sampleInside(values, point, range);
    }

    private static float sampleInside(
            CubicSpline.Multipoint<DensityFunctions.Spline.Point,
                    DensityFunctions.Spline.Coordinate> spline,
            float point,
            int range
    ) {
        float leftLocation = spline.locations()[range];
        float span = spline.locations()[range + 1] - leftLocation;
        float alpha = (point - leftLocation) / span;
        float left = sampleSpline(spline.values().get(range), point);
        float right = sampleSpline(spline.values().get(range + 1), point);
        float delta = right - left;
        float leftSlope = spline.derivatives()[range] * span - delta;
        float rightSlope = -spline.derivatives()[range + 1] * span + delta;
        return left + alpha * (delta + (1.0F - alpha)
                * (leftSlope + alpha * (rightSlope - leftSlope)));
    }

    private static int referenceRange(float[] locations, float point) {
        int range = -1;
        while (range + 1 < locations.length && point >= locations[range + 1]) ++range;
        return range;
    }

    private record SplineFixture(
            SplineNode node,
            CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline
    ) {
    }

    private static int countPointCalls(MethodNode method) {
        return countCallsWithPrefix(method, "point");
    }

    private static int countCallsWithPrefix(MethodNode method, String prefix) {
        int count = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.startsWith(prefix)) count++;
        }
        return count;
    }

    private static boolean containsOpcode(MethodNode method, int opcode) {
        for (var instruction : method.instructions) {
            if (instruction instanceof InsnNode && instruction.getOpcode() == opcode) return true;
        }
        return false;
    }

    private static boolean calls(MethodNode method, Class<?> owner, String name) {
        String internalOwner = org.objectweb.asm.Type.getInternalName(owner);
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(internalOwner) && call.name.equals(name)) return true;
        }
        return false;
    }

    private static boolean containsJump(MethodNode method) {
        for (var instruction : method.instructions) {
            if (instruction instanceof JumpInsnNode) return true;
        }
        return false;
    }

    private static boolean containsDoubleConstant(MethodNode method, double value) {
        for (var instruction : method.instructions) {
            if (instruction instanceof LdcInsnNode constant
                    && constant.cst instanceof Double number
                    && Double.doubleToRawLongBits(number)
                    == Double.doubleToRawLongBits(value)) return true;
        }
        return value == 0.0D && containsOpcode(method, org.objectweb.asm.Opcodes.DCONST_0);
    }

    private static boolean containsTableSwitch(MethodNode method) {
        for (var instruction : method.instructions) {
            if (instruction instanceof TableSwitchInsnNode) return true;
        }
        return false;
    }
}
