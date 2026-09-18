package com.moepus.byepregen.dfc.frontend;

import com.moepus.byepregen.api.dfc.ColumnDensityFunctionRegistry;
import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes;
import com.moepus.byepregen.dfc.ast.AstNodes.*;
import com.moepus.byepregen.dfc.runtime.*;
import java.util.*;
import java.util.function.Function;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.densityfunction.generator.*;
import net.minecraft.world.level.levelgen.densityfunction.op.*;

/** Converts the 26.3 graph while retaining native noise, axis slicing and extension boundaries. */
public final class DensityFunctionFrontend {
    private final Map<DensityFunction, AstNode> memo = new IdentityHashMap<>();
    private final DensityFunction.CompileContext context;
    private final Function<DensityFunction, DensitySampler> boundaryCompiler;

    public DensityFunctionFrontend(DensityFunction.CompileContext context,
                                   Function<DensityFunction, DensitySampler> boundaryCompiler) {
        this.context = context;
        this.boundaryCompiler = boundaryCompiler;
    }

    public AstNode convert(DensityFunction function) {
        AstNode existing = this.memo.get(function);
        if (existing != null) return existing;
        AstNode result = this.convertNew(function);
        if (!hasOpaque(result)) this.memo.put(function, result);
        return result;
    }

    private AstNode convertNew(DensityFunction function) {
        if (function instanceof DensityFunctions.HolderHolder holder) return this.convert(holder.function().value());
        if (function instanceof ConstantFunction value) return new ConstantNode(value.value());
        if (function instanceof BinaryFunction binary) return this.binary(binary);
        if (function instanceof UnaryFunction unary) return this.unary(unary);
        if (function instanceof ClampFunction clamp) return new NativeUnaryNode(this.convert(clamp.input()),
                NativeUnaryOp.CLAMP, clamp.min(), clamp.max());
        if (function instanceof LerpFunction lerp) return new LerpNode(this.convert(lerp.alpha()),
                this.convert(lerp.first()), this.convert(lerp.second()));
        if (function instanceof RangeChoiceFunction range) return this.range(range);
        if (function instanceof IntervalSelectFunction select
                && select.functions().size() == select.thresholds().size() + 1) {
            return new IntervalSelectNode(this.convert(select.input()), select.thresholds(),
                    select.functions().stream().map(this::convert).toList());
        }
        if (function instanceof SplineFunction spline) return this.spline(spline);
        if (function instanceof CacheFunction cache) return this.convert(cache.input());
        DensitySampler sampler = this.boundaryCompiler.apply(function);
        AstNode imported = importKnownSampler(sampler);
        if (imported != null) return imported;
        boolean declared2D = ColumnDensityFunctionRegistry.isYIndependentDelegate(function);
        int axes = declared2D
                ? function.domainAxes() & ~DensityFunction.AXIS_Y : function.domainAxes();
        // An explicit declaration permits collapsed batches and request-local reuse; unregistered
        // extension functions keep their original batch volume and occurrence order.
        boolean opaque = !declared2D && !this.knownPure(function) && !knownPureSampler(sampler);
        NativeDensitySource source = new NativeDensitySource(sampler, axes, opaque);
        return sampler instanceof InterpolatedInput
                ? new SourceNode(source)
                : new DelegateNode(source, (axes & DensityFunction.AXIS_Y) == 0);
    }

    private static AstNode importKnownSampler(DensitySampler sampler) {
        if (sampler instanceof CompiledDensitySampler compiled) return compiled.root();
        if (sampler instanceof NativeDensitySource source && !source.eager()) {
            return new DelegateNode(source, (source.axes() & DensityFunction.AXIS_Y) == 0);
        }
        if (sampler instanceof CachingDensitySampler cached) {
            AstNode input = importKnownSampler(cached.input());
            if (input != null && !hasOpaque(input)) return new CacheNode(cached, CacheKind.CACHE_ONCE, input);
        }
        return null;
    }

    private AstNode binary(BinaryFunction function) {
        AstNode left = this.convert(function.left()), right = this.convert(function.right());
        return switch (function.type()) {
            case ADD -> new AddNode(left, right);
            case SUB -> new SubNode(left, right);
            case MUL -> new MulNode(left, right);
            case DIV -> new DivNode(left, right);
            case MIN -> new MinShortNode(left, right, function.right().range().min());
            case MAX -> new MaxShortNode(left, right, function.right().range().max());
        };
    }

    private AstNode unary(UnaryFunction function) {
        AstNode input = this.convert(function.input());
        return switch (function.type()) {
            case ABS -> new AbsNode(input);
            case SQUARE -> new SquareNode(input);
            case CUBE -> new CubeNode(input);
            case NEGATE -> new NegNode(input);
            case HALF_NEGATIVE -> new NegMulNode(input, 0.5F);
            case QUARTER_NEGATIVE -> new NegMulNode(input, 0.25F);
            case SQUEEZE -> new SqueezeNode(input);
            case RECIPROCAL -> new DivNode(new ConstantNode(1), input);
            case SQRT -> new NativeUnaryNode(input, NativeUnaryOp.SQRT, 0, 0);
            case LOG -> new NativeUnaryNode(input, NativeUnaryOp.LOG, 0, 0);
            case SIGN -> new NativeUnaryNode(input, NativeUnaryOp.SIGN, 0, 0);
        };
    }

    private AstNode range(RangeChoiceFunction function) {
        // Vanilla batches the in-range branch before the selector and the other branch.
        AstNode inside = this.convert(function.whenInRange());
        AstNode input = this.convert(function.input());
        return new RangeChoiceNode(input, function.minInclusive(), function.maxExclusive(),
                inside, this.convert(function.whenOutOfRange()));
    }

    private AstNode spline(SplineFunction function) {
        var keys = AstNodes.collectSplineCoordinates(function.spline());
        List<AstNode> children = new ArrayList<>(keys.size());
        for (SplineFunction.Coordinate key : keys) children.add(this.convert(key.function()));
        return new SplineNode(function.spline(), keys, children);
    }

    public static boolean hasOpaque(AstNode node) {
        Set<AstNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return hasOpaque(node, visited);
    }

    private static boolean hasOpaque(AstNode node, Set<AstNode> visited) {
        if (!visited.add(node)) return false;
        DensitySampler source = node instanceof DelegateNode d ? d.delegate()
                : node instanceof SourceNode s ? s.source() : null;
        if (source instanceof NativeDensitySource nativeSource && nativeSource.eager()) return true;
        for (AstNode child : node.children()) if (hasOpaque(child, visited)) return true;
        return false;
    }

    private static boolean knownPureSampler(DensitySampler sampler) {
        if (sampler instanceof NativeDensitySource source) return !source.eager();
        if (sampler instanceof CompiledDensitySampler compiled) return !hasOpaque(compiled.root());
        if (sampler instanceof CachingDensitySampler cached) return knownPureSampler(cached.input());
        if (sampler instanceof InterpolatedInput interpolation) return knownPureSampler(interpolation.input());
        if (sampler instanceof SliceFunction.YSampler slice) return knownPureSampler(slice.input());
        if (sampler instanceof SliceFunction.XSampler slice) return knownPureSampler(slice.input());
        if (sampler instanceof SliceFunction.ZSampler slice) return knownPureSampler(slice.input());
        if (sampler instanceof SliceFunction.XzSampler slice) return knownPureSampler(slice.input());
        return sampler instanceof ConstantFunction.Sampler || sampler instanceof NoiseFunction.Sampler;
    }

    private boolean knownPure(DensityFunction function) {
        if (function instanceof ConstantFunction || function instanceof GradientFunction
                || function instanceof EndIslandFunction || function instanceof DistanceToPointFunction
                || function instanceof ShiftNoiseFunction) return true;
        if (function instanceof NoiseFunction noise) return this.pureInput(noise.shiftX())
                && this.pureInput(noise.shiftY()) && this.pureInput(noise.shiftZ());
        if (function instanceof SliceFunction s) return this.pureInput(s.input());
        if (function instanceof InterpolatedFunction i) return this.pureInput(i.input());
        // Combinators are represented by AST children and inherit purity through hasOpaque.
        // Unrecognized boundaries keep native behavior unless their sampler carries a proven contract.
        return false;
    }

    private boolean pureInput(DensityFunction function) {
        // Use the converted child's contract so prepared caches retain proven purity.
        return !hasOpaque(this.convert(function));
    }
}
