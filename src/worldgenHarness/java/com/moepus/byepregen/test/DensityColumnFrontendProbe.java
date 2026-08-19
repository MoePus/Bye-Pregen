package com.moepus.byepregen.test;

import com.moepus.byepregen.api.dfc.ColumnDensityFunctionRegistry;
import com.moepus.byepregen.dfc.analysis.ColumnSpecializer;
import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.AbsNode;
import com.moepus.byepregen.dfc.ast.AstNodes.AddNode;
import com.moepus.byepregen.dfc.ast.AstNodes.CacheKind;
import com.moepus.byepregen.dfc.ast.AstNodes.CacheNode;
import com.moepus.byepregen.dfc.ast.AstNodes.ConstantNode;
import com.moepus.byepregen.dfc.ast.AstNodes.CoordinateNode;
import com.moepus.byepregen.dfc.ast.AstNodes.CubeNode;
import com.moepus.byepregen.dfc.ast.AstNodes.DelegateNode;
import com.moepus.byepregen.dfc.ast.AstNodes.MaxNode;
import com.moepus.byepregen.dfc.ast.AstNodes.MaxShortNode;
import com.moepus.byepregen.dfc.ast.AstNodes.Memoized2DNode;
import com.moepus.byepregen.dfc.ast.AstNodes.MinNode;
import com.moepus.byepregen.dfc.ast.AstNodes.MinShortNode;
import com.moepus.byepregen.dfc.ast.AstNodes.MulNode;
import com.moepus.byepregen.dfc.ast.AstNodes.NegMulNode;
import com.moepus.byepregen.dfc.ast.AstNodes.NoiseNode;
import com.moepus.byepregen.dfc.ast.AstNodes.RangeChoiceNode;
import com.moepus.byepregen.dfc.ast.AstNodes.RootNode;
import com.moepus.byepregen.dfc.ast.AstNodes.SourceMode;
import com.moepus.byepregen.dfc.ast.AstNodes.SourceNode;
import com.moepus.byepregen.dfc.ast.AstNodes.SplineNode;
import com.moepus.byepregen.dfc.ast.AstNodes.SquareNode;
import com.moepus.byepregen.dfc.ast.AstNodes.SqueezeNode;
import com.moepus.byepregen.dfc.ast.AstNodes.WeirdScaledNode;
import com.moepus.byepregen.dfc.ast.AstNodes.YClampedGradientNode;
import com.moepus.byepregen.dfc.ast.AstNodes.Axis;
import com.moepus.byepregen.dfc.frontend.DensityFunctionFrontend;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

final class DensityColumnFrontendProbe {
    private DensityColumnFrontendProbe() {
    }

    static void verify() {
        DensityFunctionFrontend frontend = new DensityFunctionFrontend();
        DensityFunction constant = DensityFunctions.constant(2.0D);
        verifyArithmetic(frontend, constant);
        verifySources(frontend, constant);
        verifyNoiseAndControlFlow(frontend, constant);
        verifySplineAndFallbacks(frontend, constant);
        verifyCacheSpecialization();
        verifyDelegateIdentity(frontend);
        verifyGeneratedReachableGraph();
    }

    private static void verifyArithmetic(DensityFunctionFrontend frontend, DensityFunction value) {
        requireNode(frontend, value, ConstantNode.class);
        requireNode(frontend, DensityFunctions.add(value, value), AddNode.class);
        requireNode(frontend, DensityFunctions.mul(value, value), MulNode.class);
        requireNode(frontend, DensityFunctions.min(value, value), MinShortNode.class);
        requireNode(frontend, DensityFunctions.max(value, value), MaxShortNode.class);
        requireNode(frontend, DensityFunctions.map(value, DensityFunctions.Mapped.Type.ABS), AbsNode.class);
        requireNode(frontend, DensityFunctions.map(value, DensityFunctions.Mapped.Type.SQUARE), SquareNode.class);
        requireNode(frontend, DensityFunctions.map(value, DensityFunctions.Mapped.Type.CUBE), CubeNode.class);
        requireNode(frontend, DensityFunctions.map(value,
                DensityFunctions.Mapped.Type.HALF_NEGATIVE), NegMulNode.class);
        requireNode(frontend, DensityFunctions.map(value,
                DensityFunctions.Mapped.Type.QUARTER_NEGATIVE), NegMulNode.class);
        requireNode(frontend, DensityFunctions.map(value, DensityFunctions.Mapped.Type.SQUEEZE), SqueezeNode.class);
        AstNode clamp = frontend.convert(value.clamp(-1.0D, 1.0D));
        require(clamp instanceof MaxNode max && max.right() instanceof MinNode,
                "Clamp did not lower to Max(Min)");
        DensityFunctions.HolderHolder holder = new DensityFunctions.HolderHolder(Holder.direct(value));
        require(frontend.convert(holder) == frontend.convert(value), "Holder did not preserve frontend DAG identity");
    }

    private static void verifySources(DensityFunctionFrontend frontend, DensityFunction value) {
        requireCache(frontend, DensityFunctions.cache2d(value), CacheKind.CACHE_2D);
        requireCache(frontend, DensityFunctions.cacheOnce(value), CacheKind.CACHE_ONCE);
        requireCache(frontend, DensityFunctions.cacheAllInCell(value), CacheKind.CACHE_ALL_IN_CELL);
        requireCache(frontend, DensityFunctions.flatCache(value), CacheKind.FLAT_CACHE);
        requireCache(frontend, DensityFunctions.interpolated(value), CacheKind.INTERPOLATED);
        requireNode(frontend, DensityFunctions.blendAlpha(), ConstantNode.class);
        requireNode(frontend, DensityFunctions.blendOffset(), ConstantNode.class);
        require(frontend.convert(DensityFunctions.blendDensity(value)) == frontend.convert(value),
                "BlendDensity did not lower to its input");
        requireNode(frontend, DensityFunctions.BeardifierMarker.INSTANCE, DelegateNode.class);
    }

    private static void verifyNoiseAndControlFlow(
            DensityFunctionFrontend frontend,
            DensityFunction value
    ) {
        Holder<NormalNoise.NoiseParameters> parameters = Holder.direct(
                new NormalNoise.NoiseParameters(-3, 1.0D));
        requireNode(frontend, DensityFunctions.noise(parameters), NoiseNode.class);
        requireNode(frontend, DensityFunctions.mappedNoise(
                parameters, 0.25D, 0.5D, -1.0D, 1.0D), AddNode.class);
        requireNode(frontend, DensityFunctions.shiftA(parameters), MulNode.class);
        requireNode(frontend, DensityFunctions.shiftB(parameters), MulNode.class);
        requireNode(frontend, DensityFunctions.shift(parameters), MulNode.class);
        requireNode(frontend, DensityFunctions.shiftedNoise2d(value, value, 0.25D, parameters),
                NoiseNode.class);
        requireNode(frontend, DensityFunctions.weirdScaledSampler(value, parameters,
                DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE1), WeirdScaledNode.class);
        requireNode(frontend, DensityFunctions.rangeChoice(value, -1.0D, 1.0D, value, value),
                RangeChoiceNode.class);
        requireNode(frontend, DensityFunctions.yClampedGradient(-64, 320, -1.0D, 1.0D),
                YClampedGradientNode.class);
        requireNode(frontend, DensityFunctions.lerp(value, value, value), AddNode.class);
        requireNode(frontend, DensityFunctions.lerp(value, 0.25D, value), AddNode.class);
    }

    private static void verifySplineAndFallbacks(
            DensityFunctionFrontend frontend,
            DensityFunction value
    ) {
        requireNode(frontend, DensityFunctions.spline(CubicSpline.constant(0.25F)), SplineNode.class);
        requireNode(frontend, DensityFunctions.endIslands(1L), DelegateNode.class);
        UnsupportedDelegate unsupported = new UnsupportedDelegate(3.0D);
        DelegateNode generic = requireNode(frontend, unsupported, DelegateNode.class);
        require(!generic.yIndependent(), "Unknown delegate was incorrectly promoted to 2D");

        ColumnDensityFunctionRegistry.registerYIndependentDelegate(RegisteredDelegate.class);
        DelegateNode registered = requireNode(frontend, new RegisteredDelegate(4.0D), DelegateNode.class);
        require(registered.yIndependent(), "Registered delegate was not promoted to 2D");
        require(frontend.convert(value) instanceof ConstantNode, "Frontend memo was corrupted by fallback nodes");
    }

    private static void verifyCacheSpecialization() {
        DensityFunction value = DensityFunctions.constant(3.0D);
        DensityFunctionFrontend frontend = new DensityFunctionFrontend();
        AstNode cache2d = specialize(frontend.convert(DensityFunctions.cache2d(value)));
        Memoized2DNode cache2dSlot = requireNode(cache2d, Memoized2DNode.class);
        require(cache2dSlot.delegate() instanceof ConstantNode, "Cache2D child was not preserved");

        require(specialize(frontend.convert(DensityFunctions.cacheOnce(value))) instanceof ConstantNode,
                "CacheOnce was not removed");
        require(specialize(frontend.convert(DensityFunctions.cacheAllInCell(value))) instanceof ConstantNode,
                "CacheAllInCell was not removed");

        DensityFunction flat = DensityFunctions.flatCache(value);
        SourceNode flatSource = requireNode(unwrapMemo(specialize(frontend.convert(flat))), SourceNode.class);
        require(flatSource.mode() == SourceMode.FLAT && flatSource.source() == flat,
                "FlatCache source identity was not preserved");

        DensityFunction interpolated = DensityFunctions.interpolated(value);
        SourceNode boundary = requireNode(specialize(frontend.convert(interpolated)), SourceNode.class);
        require(boundary.mode() == SourceMode.INTERPOLATED && boundary.source() == interpolated,
                "Interpolated marker did not become an identity-preserving boundary source");

        verifyCacheSlotPlacement(frontend);
    }

    private static void verifyCacheSlotPlacement(DensityFunctionFrontend frontend) {
        AstNode first = frontend.convert(DensityFunctions.cache2d(DensityFunctions.add(
                DensityFunctions.constant(1.0D), DensityFunctions.shiftedNoise2d(
                        DensityFunctions.constant(0.0D), DensityFunctions.constant(0.0D),
                        1.0D, Holder.direct(new NormalNoise.NoiseParameters(-3, 1.0D))))));
        AstNode second = frontend.convert(DensityFunctions.cache2d(DensityFunctions.add(
                DensityFunctions.constant(2.0D), DensityFunctions.constant(3.0D))));
        ColumnSpecializer.Result parentResult = ColumnSpecializer.specialize(
                new RootNode(new AddNode(first, second)));
        Memoized2DNode parent = requireNode(
                requireNode(parentResult.root(), RootNode.class).next(), Memoized2DNode.class);
        AddNode parentSum = requireNode(parent.delegate(), AddNode.class);
        require(!(parentSum.left() instanceof Memoized2DNode)
                        && !(parentSum.right() instanceof Memoized2DNode),
                "higher 2D parent did not absorb nested Cache2D slots");
        require(parentResult.memoizedSlots() == 1, "higher 2D parent allocated redundant slots");

        DensityFunction sameValue = DensityFunctions.add(
                DensityFunctions.constant(1.0D), DensityFunctions.constant(2.0D));
        AstNode equalA = frontend.convert(DensityFunctions.cache2d(sameValue));
        AstNode equalB = new DensityFunctionFrontend().convert(DensityFunctions.cache2d(sameValue));
        AstNode graph = new AddNode(new CoordinateNode(Axis.Y), new AddNode(equalA, equalB));
        ColumnSpecializer.Result sharedResult = ColumnSpecializer.specialize(new RootNode(graph));
        AddNode ySum = requireNode(requireNode(sharedResult.root(), RootNode.class).next(), AddNode.class);
        Memoized2DNode cachedParent = requireNode(ySum.right(), Memoized2DNode.class);
        AddNode cachedSum = requireNode(cachedParent.delegate(), AddNode.class);
        require(cachedSum.left() == cachedSum.right(), "equivalent Cache2D children did not share a slot");
        require(sharedResult.memoizedSlots() == 1, "equivalent Cache2D children allocated multiple slots");

        AstNode flatA = frontend.convert(DensityFunctions.flatCache(DensityFunctions.constant(4.0D)));
        AstNode flatB = frontend.convert(DensityFunctions.flatCache(DensityFunctions.constant(4.0D)));
        ColumnSpecializer.Result sourceResult = ColumnSpecializer.specialize(new RootNode(
                new AddNode(new CoordinateNode(Axis.Y), new AddNode(flatA, flatB))));
        AddNode sourceRoot = requireNode(
                requireNode(sourceResult.root(), RootNode.class).next(), AddNode.class);
        Memoized2DNode sourceParent = requireNode(sourceRoot.right(), Memoized2DNode.class);
        AddNode sourceSum = requireNode(sourceParent.delegate(), AddNode.class);
        SourceNode sourceA = requireNode(sourceSum.left(), SourceNode.class);
        SourceNode sourceB = requireNode(sourceSum.right(), SourceNode.class);
        require(sourceA != sourceB && sourceA.source() != sourceB.source(),
                "distinct FlatCache sources were structurally merged");
        require(sourceResult.memoizedSlots() == 1, "FlatCache parent allocated redundant child slots");
    }

    private static void verifyDelegateIdentity(DensityFunctionFrontend frontend) {
        UnsupportedDelegate first = new UnsupportedDelegate(1.0D);
        UnsupportedDelegate second = new UnsupportedDelegate(1.0D);
        AstNode firstNode = frontend.convert(first);
        require(frontend.convert(first) == firstNode, "Same delegate object did not share a frontend node");
        AstNode secondNode = frontend.convert(second);
        require(firstNode != secondNode, "Different delegate objects were merged by the frontend");

        AstNode specialized = specialize(new AddNode(firstNode, secondNode));
        AddNode sum = requireNode(specialized, AddNode.class);
        require(sum.left() != sum.right(), "Structural CSE merged different delegate objects");
    }

    private static void verifyGeneratedReachableGraph() {
        Holder<NormalNoise.NoiseParameters> parameters = Holder.direct(
                new NormalNoise.NoiseParameters(-3, 1.0D));
        DensityFunction graph = DensityFunctions.add(
                DensityFunctions.interpolated(DensityFunctions.noise(parameters)),
                DensityFunctions.add(
                        DensityFunctions.spline(CubicSpline.constant(0.25F)),
                        new UnsupportedDelegate(2.0D)));
        var template = com.moepus.byepregen.dfc.compile.DensityColumnCompiler.compile(graph);
        require(template.available(), "reachable graph audit did not compile: " + template.disabledReason());
        byte[] bytes = template.classBytes();
        String constants = new String(bytes, StandardCharsets.ISO_8859_1);
        require(!constants.contains("com/ishland/c2me"), "generated graph references C2ME DFC");
        require(!constants.contains("evalMulti"), "generated graph contains evalMulti");

        ClassNode type = new ClassNode();
        new ClassReader(bytes).accept(type, 0);
        type.fields.forEach(field -> require(!field.desc.contains("[I"),
                "generated field contains a coordinate array"));
        type.methods.forEach(method -> method.instructions.forEach(instruction -> {
            if (!(instruction instanceof MethodInsnNode call)) return;
            require(!call.owner.contains("com/ishland/c2me"), "generated call references C2ME DFC");
            require(!call.desc.contains("[I"), "generated call contains a coordinate array");
            require(!call.name.equals("evalMulti"), "generated graph calls evalMulti");
            require(!call.owner.contains("NoiseChunk$Cache"), "generated graph calls a cache wrapper");
        }));
        Set<String> reachable = reachableMethods(type);
        require(reachable.stream().anyMatch(name -> name.startsWith("column")),
                "evalColumn does not reach a column array helper");
        require(reachable.stream().anyMatch(name -> name.startsWith("point")),
                "real graph did not retain required point helpers");
        reachable.stream().filter(name -> !name.equals("evalColumn")).forEach(name ->
                require(name.startsWith("column") || name.startsWith("point"),
                        "unexpected generated helper reachable from evalColumn: " + name));
        type.methods.stream()
                .filter(method -> (method.access & org.objectweb.asm.Opcodes.ACC_PUBLIC) != 0)
                .forEach(method -> require(method.name.equals("<init>") || method.name.equals("evalColumn"),
                        "generated class exposes an unexpected public method: " + method.name));
    }

    private static Set<String> reachableMethods(ClassNode type) {
        Map<String, MethodNode> methods = new HashMap<>();
        type.methods.forEach(method -> methods.put(method.name + method.desc, method));
        MethodNode entry = type.methods.stream()
                .filter(method -> method.name.equals("evalColumn"))
                .findFirst().orElseThrow();
        Set<String> visited = new HashSet<>();
        Set<String> names = new HashSet<>();
        ArrayDeque<MethodNode> pending = new ArrayDeque<>();
        pending.add(entry);
        while (!pending.isEmpty()) {
            MethodNode method = pending.removeFirst();
            if (!visited.add(method.name + method.desc)) continue;
            names.add(method.name);
            method.instructions.forEach(instruction -> {
                if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals(type.name)) return;
                MethodNode target = methods.get(call.name + call.desc);
                if (target != null) pending.add(target);
            });
        }
        return names;
    }

    private static AstNode specialize(AstNode node) {
        AstNode root = ColumnSpecializer.specialize(new RootNode(node)).root();
        return requireNode(root, RootNode.class).next();
    }

    private static AstNode unwrapMemo(AstNode node) {
        return node instanceof Memoized2DNode memoized ? memoized.delegate() : node;
    }

    private static void requireCache(
            DensityFunctionFrontend frontend,
            DensityFunction function,
            CacheKind expected
    ) {
        CacheNode node = requireNode(frontend, function, CacheNode.class);
        require(node.kind() == expected, "Wrong cache kind: " + node.kind() + " != " + expected);
        require(node.source() == function, "Cache source identity was not preserved");
    }

    private static <T extends AstNode> T requireNode(
            DensityFunctionFrontend frontend,
            DensityFunction function,
            Class<T> type
    ) {
        return requireNode(frontend.convert(function), type);
    }

    private static <T extends AstNode> T requireNode(AstNode node, Class<T> type) {
        if (!type.isInstance(node)) {
            throw new IllegalStateException("Expected " + type.getSimpleName() + ", got "
                    + node.getClass().getSimpleName());
        }
        return type.cast(node);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static class UnsupportedDelegate implements DensityFunction {
        private final double value;

        private UnsupportedDelegate(double value) {
            this.value = value;
        }

        @Override public double compute(FunctionContext context) { return this.value; }
        @Override public void fillArray(double[] values, ContextProvider provider) {
            provider.fillAllDirectly(values, this);
        }
        @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(this); }
        @Override public double minValue() { return this.value; }
        @Override public double maxValue() { return this.value; }
        @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return null; }
    }

    private static final class RegisteredDelegate extends UnsupportedDelegate {
        private RegisteredDelegate(double value) {
            super(value);
        }
    }
}
