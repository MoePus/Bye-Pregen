package com.moepus.byepregen.dfc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moepus.byepregen.dfc.ast.AstNodes.ConstantNode;
import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.CoordinateNode;
import com.moepus.byepregen.dfc.ast.AstNodes.Axis;
import com.moepus.byepregen.dfc.ast.AstNodes.MaxShortNode;
import com.moepus.byepregen.dfc.ast.AstNodes.Memoized2DNode;
import com.moepus.byepregen.dfc.ast.AstNodes.MinShortNode;
import com.moepus.byepregen.dfc.ast.AstNodes.MulNode;
import com.moepus.byepregen.dfc.ast.AstNodes.RangeChoiceNode;
import com.moepus.byepregen.dfc.ast.AstNodes.SplineNode;
import com.moepus.byepregen.dfc.codegen.ColumnClassBuilder;
import com.moepus.byepregen.dfc.codegen.ColumnClassDefiner;
import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import com.moepus.byepregen.dfc.runtime.CompiledColumnEvaluator;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
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
    void mulHasNoValueDependentBranchAndMinShortDoes() {
        ColumnClassBuilder.BuildResult generated = new ColumnClassBuilder(0).build(
                new MinShortNode(new MulNode(new ConstantNode(2.0D), new ConstantNode(3.0D)),
                        new ConstantNode(4.0D), 4.0D));
        ClassNode type = new ClassNode();
        new ClassReader(generated.classBytes()).accept(type, 0);
        MethodNode mul = find(type, "point", "MulNode");
        MethodNode min = find(type, "point", "MinShortNode");

        assertNotNull(mul);
        assertNotNull(min);
        assertFalse(containsJump(mul), "Mul point helper must be eager and branch-free");
        assertTrue(containsJump(min), "MinShort must retain lazy right-branch evaluation");
        assertEquals(2, countPointCalls(mul));
    }

    @Test
    void maxShortAndRangeChoiceRetainConditionalLazyBranches() {
        AstNode range = new RangeChoiceNode(
                new CoordinateNode(Axis.X), 0.0D, 1.0D,
                new MaxShortNode(new CoordinateNode(Axis.Z), new ConstantNode(2.0D), 2.0D),
                new MinShortNode(new CoordinateNode(Axis.Z), new ConstantNode(-2.0D), -2.0D));
        ClassNode type = read(new ColumnClassBuilder(0).build(range).classBytes());

        MethodNode max = find(type, "point", "MaxShortNode");
        MethodNode min = find(type, "point", "MinShortNode");
        MethodNode choice = find(type, "point", "RangeChoiceNode");
        assertNotNull(max);
        assertNotNull(min);
        assertNotNull(choice);
        assertTrue(containsJump(max), "MaxShort must retain lazy right-branch evaluation");
        assertTrue(containsJump(min), "MinShort must retain lazy right-branch evaluation");
        assertTrue(containsJump(choice), "RangeChoice must retain conditional branch evaluation");
        assertEquals(3, countPointCalls(choice),
                "RangeChoice must evaluate input and one selected branch");
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
        assertTrue(reachable.stream().anyMatch(name -> name.startsWith("point")));
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

    private static boolean containsTableSwitch(MethodNode method) {
        for (var instruction : method.instructions) {
            if (instruction instanceof TableSwitchInsnNode) return true;
        }
        return false;
    }
}
