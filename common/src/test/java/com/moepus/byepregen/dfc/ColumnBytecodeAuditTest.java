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
import com.moepus.byepregen.dfc.ast.AstNodes.DivNode;
import com.moepus.byepregen.dfc.ast.AstNodes.MaxShortNode;
import com.moepus.byepregen.dfc.ast.AstNodes.Memoized2DNode;
import com.moepus.byepregen.dfc.ast.AstNodes.MinShortNode;
import com.moepus.byepregen.dfc.ast.AstNodes.MulNode;
import com.moepus.byepregen.dfc.ast.AstNodes.RangeChoiceNode;
import com.moepus.byepregen.dfc.ast.AstNodes.SplineNode;
import com.moepus.byepregen.dfc.codegen.ColumnClassBuilder;
import com.moepus.byepregen.dfc.codegen.ColumnClassDefiner;
import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import com.moepus.byepregen.dfc.runtime.ColumnTestFrames;
import com.moepus.byepregen.dfc.runtime.CompiledColumnEvaluator;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.densityfunction.op.SplineFunction;
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
    void gradientColumnHelperAvoidsPointFallback() {
        AstNode graph = new com.moepus.byepregen.dfc.ast.AstNodes.YClampedGradientNode(
                -4, 4, -1.0F, 1.0F);
        ColumnClassBuilder.BuildResult generated = new ColumnClassBuilder(0).build(graph);
        ClassNode type = read(generated.classBytes());
        MethodNode gradient = find(type, "column", "YClampedGradientNode");
        assertNotNull(gradient);
        assertEquals(0, countPointCalls(gradient));
        assertTrue(calls(gradient, com.moepus.byepregen.dfc.runtime.ColumnMath.class, "clampedMap"));
    }

    @Test
    void shortMinUsesHybridColumnAndInlinesConstants() {
        ColumnClassBuilder.BuildResult generated = new ColumnClassBuilder(0).build(
                new MinShortNode(new MulNode(new ConstantNode(0.64F), new CoordinateNode(Axis.X)),
                        new CoordinateNode(Axis.Y), 4.0F));
        ClassNode type = read(generated.classBytes());
        MethodNode mul = find(type, "column", "MulNode");
        MethodNode min = find(type, "column", "MinShortNode");

        assertNotNull(mul);
        assertNotNull(min);
        // 26.3: the AST, emitters and column context are float based (FMUL/FALOAD/borrowFloatArray).
        assertTrue(containsOpcode(mul, org.objectweb.asm.Opcodes.FMUL));
        assertTrue(containsJump(min), "MinShort column helper must retain lane short-circuiting");
        assertTrue(countCallsWithPrefix(min, "column") > 1,
                "MinShort must call both sides through range-aware column helpers");
        assertEquals(0, countCallsWithPrefix(min, "point"));
        assertTrue(containsFloatConstant(mul, 0.64F));
        assertFalse(calls(mul, ColumnEvaluationContext.class, "borrowFloatArray"),
                "constant-sided arithmetic should update the output in one loop");
        assertEquals(0, type.methods.stream()
                .filter(method -> method.name.startsWith("point")
                        && method.name.endsWith("MinShortNode"))
                .count());
        assertTrue(type.methods.stream().noneMatch(method -> method.name.endsWith("ConstantNode")));
    }

    @Test
    void shortMaxUsesHybridColumn() {
        ClassNode type = read(new ColumnClassBuilder(0).build(
                new MaxShortNode(new CoordinateNode(Axis.Y),
                        new CoordinateNode(Axis.X), 0.0F)).classBytes());
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
                new MinShortNode(new ConstantNode(0.0F),
                        new CoordinateNode(Axis.Y), -4.9294F)).classBytes());
        MethodNode eager = find(eagerType, "column", "MinShortNode");
        assertNotNull(eager);
        assertTrue(countCallsWithPrefix(eager, "column") > 0);
        assertEquals(0, countCallsWithPrefix(eager, "point"));
        assertTrue(eagerType.methods.stream().noneMatch(method -> method.name.startsWith("point")
                && method.name.endsWith("MinShortNode")));

        ClassNode shortedType = read(new ColumnClassBuilder(0).build(
                new MinShortNode(new ConstantNode(-5.0F),
                        new CoordinateNode(Axis.Y), -4.9294F)).classBytes());
        MethodNode shorted = find(shortedType, "column", "MinShortNode");
        assertNotNull(shorted);
        assertFalse(calls(shorted, Math.class, "min"));
        assertTrue(containsFloatConstant(shorted, -5.0F));
        assertEquals(0, countCallsWithPrefix(shorted, "point"));
    }

    @Test
    void rangeChoiceBoundsAreEmittedAsFloatConstants() {
        ClassNode type = read(new ColumnClassBuilder(0).build(new RangeChoiceNode(
                new CoordinateNode(Axis.Y), -1000000.0F, 0.0F,
                new ConstantNode(1.0F), new ConstantNode(2.0F))).classBytes());
        MethodNode choice = find(type, "column", "RangeChoiceNode");
        assertNotNull(choice);
        // 26.3: range bounds are float literals now; the guard is that no F2D widening sneaks in.
        assertTrue(containsFloatConstant(choice, -1000000.0F));
        assertTrue(containsFloatConstant(choice, 0.0F));
        assertFalse(containsOpcode(choice, org.objectweb.asm.Opcodes.F2D));
    }

    @Test
    void maxShortAndRangeChoiceRetainConditionalLazyBranches() {
        AstNode range = new RangeChoiceNode(
                new CoordinateNode(Axis.X), 0.0F, 1.0F,
                new MaxShortNode(new CoordinateNode(Axis.Z), new ConstantNode(2.0F), 2.0F),
                new MinShortNode(new CoordinateNode(Axis.Z), new ConstantNode(-2.0F), -2.0F));
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
    void evalColumnUsesDedicatedArrayHelpersForArithmetic() {
        AstNode graph = new MulNode(new CoordinateNode(Axis.Y), new ConstantNode(2.0F));
        ClassNode type = read(new ColumnClassBuilder(0).build(graph).classBytes());
        MethodNode entry = type.methods.stream()
                .filter(method -> method.name.equals("evalColumn"))
                .findFirst().orElseThrow();
        MethodNode mul = find(type, "column", "MulNode");

        assertNotNull(mul);
        assertEquals(1, countCallsWithPrefix(entry, "column"));
        assertEquals(0, countCallsWithPrefix(entry, "point"));
        assertTrue(containsOpcode(mul, org.objectweb.asm.Opcodes.FALOAD));
        assertTrue(containsOpcode(mul, org.objectweb.asm.Opcodes.FASTORE));
        assertTrue(containsOpcode(mul, org.objectweb.asm.Opcodes.FMUL));
    }

    @Test
    void constantDivisorColumnUsesReciprocalMultiplication() {
        ClassNode type = read(new ColumnClassBuilder(0).build(
                new DivNode(new CoordinateNode(Axis.Y), new ConstantNode(4.0F))).classBytes());
        MethodNode div = find(type, "column", "DivNode");

        assertNotNull(div);
        assertTrue(containsOpcode(div, org.objectweb.asm.Opcodes.FMUL));
        assertFalse(containsOpcode(div, org.objectweb.asm.Opcodes.FDIV));
        assertTrue(containsFloatConstant(div, 0.25F));
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
    void evalColumnOwnsTheOnlyCleanupHandlerAndRethrows() throws Throwable {
        AstNode graph = new MulNode(new CoordinateNode(Axis.Y),
                new Memoized2DNode(new ConstantNode(2.0F), 1));
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
        // 26.3: column frames are float based and come from the request-owned workspace.
        ColumnEvaluationContext context = ColumnTestFrames.prepared(new float[3], 0, 0, 0, 4);
        IndexOutOfBoundsException failure = assertThrows(
                IndexOutOfBoundsException.class, () -> evaluator.evalColumn(context));
        assertTrue(failure.getMessage().contains("memoized index"));
        context.clear();
    }

    @Test
    void directSplineHelpersUseTableSwitch() {
        ColumnClassBuilder.BuildResult generated = new ColumnClassBuilder(0).build(splineNode());
        byte[] bytes = generated.classBytes();
        assertFalse(new String(bytes, StandardCharsets.ISO_8859_1).contains("SplineProgram"));
        ClassNode type = read(bytes);
        // 26.3: spline helpers carry the volume/scalar prefix, so they are named volumeSpline0.
        List<MethodNode> splines = type.methods.stream()
                .filter(method -> method.name.contains("Spline"))
                .toList();
        assertFalse(splines.isEmpty(), "spline helper methods must be generated");
        assertTrue(splines.stream().anyMatch(ColumnBytecodeAuditTest::containsTableSwitch));
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
        // 26.3: the sentinel is a float payload compared through floatToRawIntBits.
        assertTrue(calls(miss, Float.class, "floatToRawIntBits"));
        assertFalse(calls(miss, Float.class, "floatToIntBits"));
        assertFalse(calls(miss, Float.class, "isNaN"));
    }

    @Test
    void generatedReachableDescriptorsDoNotContainC2meOrGenericMultiAbi() {
        AstNode graph = new RangeChoiceNode(new CoordinateNode(Axis.Y), 0.0F, 1.0F,
                new MulNode(new CoordinateNode(Axis.X), new ConstantNode(2.0F)),
                new ConstantNode(1.0F));
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

    private static SplineNode splineNode() {
        // 26.3: DensityFunctions.Spline.Coordinate became SplineFunction.Coordinate, and
        // CubicSpline.Multipoint dropped its explicit minValue/maxValue constructor arguments.
        SplineFunction.Coordinate coordinate = SplineTestFixtures.coordinate();
        CubicSpline<SplineFunction.Coordinate> child =
                new CubicSpline.Multipoint<>(coordinate, new float[]{-2.0F, 2.0F},
                        List.of(CubicSpline.constant(-1.0F), CubicSpline.constant(3.0F)),
                        new float[]{0.25F, -0.5F});
        CubicSpline<SplineFunction.Coordinate> root =
                new CubicSpline.Multipoint<>(coordinate, new float[]{-4.0F, 0.0F, 4.0F},
                        List.of(CubicSpline.constant(-5.0F), child, CubicSpline.constant(7.0F)),
                        new float[]{0.0F, 0.5F, 0.0F});
        return new SplineNode(root, List.of(coordinate),
                List.of(new CoordinateNode(Axis.Y)));
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

    private static boolean containsFloatConstant(MethodNode method, float value) {
        for (var instruction : method.instructions) {
            if (instruction instanceof LdcInsnNode constant
                    && constant.cst instanceof Float number
                    && Float.floatToRawIntBits(number)
                    == Float.floatToRawIntBits(value)) return true;
        }
        return value == 0.0F && containsOpcode(method, org.objectweb.asm.Opcodes.FCONST_0);
    }

    private static boolean containsTableSwitch(MethodNode method) {
        for (var instruction : method.instructions) {
            if (instruction instanceof TableSwitchInsnNode) return true;
        }
        return false;
    }
}
