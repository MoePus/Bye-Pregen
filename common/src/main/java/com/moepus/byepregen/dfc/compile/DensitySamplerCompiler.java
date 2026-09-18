package com.moepus.byepregen.dfc.compile;

import com.mojang.serialization.MapCodec;
import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.*;
import com.moepus.byepregen.dfc.frontend.DensityFunctionFrontend;
import com.moepus.byepregen.dfc.frontend.SamplerFrontend;
import com.moepus.byepregen.dfc.runtime.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.densityfunction.generator.ConstantFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Native compiler lifecycle outside, transplanted column compiler inside. */
public final class DensitySamplerCompiler {
    private static final Logger LOGGER = LoggerFactory.getLogger("ByePregen DFC");
    private static final String TRUST_THIRD_PARTY = "byepregen.dfc.trustThirdParty";
    private static final Set<Class<?>> REWRITABLE = ConcurrentHashMap.newKeySet();
    private static final Set<Class<?>> OPAQUE = ConcurrentHashMap.newKeySet();
    private final DensityFunction.CompileContext context;
    private final Map<DensityFunction, DensitySampler> compiled = new IdentityHashMap<>();

    private DensitySamplerCompiler(DensityFunction.CompileContext context) { this.context = context; }

    public static DensitySampler compile(DensityFunction function, DensityFunction.CompileContext context) {
        return new DensitySamplerCompiler(context).compile(function);
    }

    private DensitySampler compile(DensityFunction function) {
        DensitySampler existing = this.compiled.get(function);
        if (existing != null) return existing;
        AstNode root = new DensityFunctionFrontend(this.context, this::boundary).convert(function);
        DensitySampler result = root instanceof DelegateNode delegate ? rootSampler(delegate.delegate())
                : new CompiledDensitySampler(root);
        this.compiled.put(function, result);
        return result;
    }

    public static DensitySampler optimize(DensitySampler sampler) {
        AstNode root = new SamplerFrontend().read(sampler);
        return root instanceof DelegateNode ? sampler : new CompiledDensitySampler(root);
    }

    private static DensitySampler rootSampler(DensitySampler sampler) {
        // Keep trusted axes/purity available when vanilla subsequently wraps this leaf in a cache.
        return sampler instanceof NativeDensitySource source && source.eager() ? source.sampler() : sampler;
    }

    private DensitySampler boundary(DensityFunction function) {
        if (function instanceof InterpolatedFunction interpolation) {
            DensitySampler input = this.compile(interpolation.input());
            DensitySampler fallback = interpolation.rewriteChildren(child -> new Region(child, this)).compileSampler(this.context);
            return new InterpolatedInput(input, fallback, interpolation.cellSizeXz(), interpolation.cellSizeY());
        }
        boolean extension = !function.getClass().getName().startsWith("net.minecraft.");
        if (extension && !rewritable(function)) {
            return function.compileSampler(this.context);
        }
        DensityFunction rewritten;
        try {
            rewritten = function.rewriteChildren(child -> child instanceof ConstantFunction ? child : new Region(child, this));
        } catch (RuntimeException | LinkageError failure) {
            if (!extension) throw failure;
            LOGGER.warn("Keeping density function {} opaque: replacing its children failed ({})",
                    function.getClass().getName(), failure.toString());
            return function.compileSampler(this.context);
        }
        // An identity probe cannot prove that a wrapper also accepts substituted children.
        if (extension && (rewritten == null || rewritten.getClass() != function.getClass())) {
            LOGGER.warn("Keeping density function {} opaque: replacing its children changed its type",
                    function.getClass().getName());
            return function.compileSampler(this.context);
        }
        return rewritten.compileSampler(this.context);
    }

    private static boolean rewritable(DensityFunction function) {
        if (!Boolean.parseBoolean(System.getProperty(TRUST_THIRD_PARTY, "true"))) return false;
        Class<?> type = function.getClass();
        if (REWRITABLE.contains(type)) return true;
        if (OPAQUE.contains(type)) return false;
        try {
            DensityFunction probe = function.rewriteChildren(child -> child);
            if (probe == null || probe.getClass() != type) {
                OPAQUE.add(type);
                LOGGER.warn("Keeping density function {} opaque: rewriting its children would change its type",
                        type.getName());
                return false;
            }
            REWRITABLE.add(type);
            return true;
        } catch (RuntimeException | LinkageError failure) {
            OPAQUE.add(type);
            LOGGER.warn("Keeping density function {} opaque: rewriting its children failed ({})",
                    type.getName(), failure.toString());
            return false;
        }
    }

    private record Region(DensityFunction source, DensitySamplerCompiler compiler) implements DensityFunction {
        @Override public DensitySampler compileSampler(CompileContext context) { return this.compiler.compile(this.source); }
        @Override public DensityFunction rewriteChildren(DfRewriteRule rule) { return this; }
        @Override public Interval range() { return this.source.range(); }
        @Override public int domainAxes() { return this.source.domainAxes(); }
        @Override public MapCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException("Compilation regions cannot be serialized");
        }
    }
}
