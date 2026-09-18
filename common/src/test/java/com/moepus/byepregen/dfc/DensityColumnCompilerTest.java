package com.moepus.byepregen.dfc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.MapCodec;
import com.moepus.byepregen.api.dfc.ColumnDensityFunctionRegistry;
import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.AddNode;
import com.moepus.byepregen.dfc.compile.DensityColumnCompiler;
import com.moepus.byepregen.dfc.codegen.ColumnClassBuilder;
import com.moepus.byepregen.dfc.frontend.DensityFunctionFrontend;
import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import com.moepus.byepregen.dfc.runtime.ColumnTemplate;
import com.moepus.byepregen.dfc.runtime.ColumnTestFrames;
import com.moepus.byepregen.dfc.runtime.NativeDensitySource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.ContextBoundSampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class DensityColumnCompilerTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    /**
     * 26.3: {@code DensityColumnCompiler.compile} now takes the optimized AST instead of a
     * {@code DensityFunction}; the frontend half of the old entry point is explicit.
     */
    private static final Function<DensityFunction, DensitySampler> DELEGATE =
            function -> function.compileSampler(DfcFixtures.CONTEXT);

    @Test
    void runtimeTemplateDoesNotRetainGeneratedClassBytes() {
        assertTrue(Arrays.stream(ColumnTemplate.class.getDeclaredFields())
                .noneMatch(field -> field.getType() == byte[].class));
    }

    @Test
    void unknownDelegateKeepsFullVolumeSemantics() {
        CountingDelegate delegate = new CountingDelegate();
        float[] output = evaluate(delegate, 4, 4);

        assertArrayEquals(new float[]{0.0F, 4.0F, 8.0F, 12.0F}, output);
        // 26.3: an undeclared extension stays opaque, so it becomes one eager full-volume native
        // sample for the whole request rather than one point call per lane.
        assertEquals(0, delegate.sampler.points.get());
        assertEquals(1, delegate.sampler.volumes.size());
        assertEquals(4, delegate.sampler.volumes.get(0).sizeY());
    }

    @Test
    void registeredDelegateUsesOneLazyColumnSlot() {
        RegisteredDelegate delegate = new RegisteredDelegate();
        ColumnDensityFunctionRegistry.registerYIndependentDelegate(RegisteredDelegate.class);
        float[] output = evaluate(delegate, 5, 1);

        assertArrayEquals(new float[]{7.0F, 7.0F, 7.0F, 7.0F, 7.0F}, output);
        // 26.3: a declared Y-independent delegate collapses to a single 1x1x1 native sample and
        // the resulting value is broadcast down the column.
        assertEquals(0, delegate.sampler.points.get());
        assertEquals(1, delegate.sampler.volumes.size());
        assertEquals(1, delegate.sampler.volumes.get(0).sizeY());
    }

    @Test
    void compilerLeavesBeardifierForPointEvaluation() {
        AtomicInteger beardifierBindings = new AtomicInteger();
        AstNode constant = convert(DensityFunctions.constant(2.0F));
        for (ColumnTemplate.Binding binding : bindings(constant)) {
            if (binding.value() instanceof NativeDensitySource source
                    && source.sampler() instanceof ContextBoundSampler) {
                beardifierBindings.incrementAndGet();
            }
        }
        assertEquals(0, beardifierBindings.get());
        assertArrayEquals(new float[]{2.0F, 2.0F, 2.0F, 2.0F}, evaluate(constant, 4, 4));

        // 26.3: the beardifier is a context-bound native sampler leaf, so the compiler must keep
        // binding it for point evaluation instead of expanding it into generated column code.
        AstNode beardified = new AddNode(constant, convert(DensityFunctions.beardifier()));
        List<ColumnTemplate.Binding> reachable = bindings(beardified);
        assertEquals(1, reachable.size());
        NativeDensitySource bound = assertInstanceOf(NativeDensitySource.class, reachable.get(0).value());
        assertInstanceOf(ContextBoundSampler.class, bound.sampler());
    }

    @Test
    void invertedFunctionCompilesAsReciprocal() {
        // 26.3: DensityFunctions.map/Mapped no longer exist; the reciprocal operation is
        // DensityFunction.reciprocal().
        DensityFunction inverted = DensityFunctions.yClampedGradient(0, 12, 2.0F, 8.0F).reciprocal();

        assertArrayEquals(new float[]{1.0F / 2.0F, 1.0F / 4.0F, 1.0F / 6.0F, 1.0F / 8.0F},
                evaluate(inverted, 4, 4));
    }

    @Test
    void templateCarriesAnalyzedYDependency() {
        ColumnTemplate fixed = DensityColumnCompiler.compile(convert(DensityFunctions.constant(2.0F)));
        ColumnTemplate varying = DensityColumnCompiler.compile(convert(new CountingDelegate()));

        assertTrue(fixed.yIndependent());
        assertFalse(varying.yIndependent());
    }

    private static AstNode convert(DensityFunction function) {
        return new DensityFunctionFrontend(DfcFixtures.CONTEXT, DELEGATE).convert(function);
    }

    private static List<ColumnTemplate.Binding> bindings(AstNode ast) {
        return new ColumnClassBuilder(0).build(ast).bindings();
    }

    private static float[] evaluate(DensityFunction function, int length, int cellHeight) {
        return evaluate(convert(function), length, cellHeight);
    }

    private static float[] evaluate(AstNode ast, int length, int cellHeight) {
        ColumnTemplate template = DensityColumnCompiler.compile(ast);
        float[] output = new float[length];
        // 26.3: column frames are float based and are opened on the request-owned workspace.
        ColumnEvaluationContext context = ColumnTestFrames.prepared(output, 3, 9, 0, cellHeight);
        try {
            template.evaluator().evalColumn(context);
        } finally {
            context.clear();
        }
        return output;
    }

    private abstract static class TestDelegate implements DensityFunction {
        @Override public DensityFunction rewriteChildren(DfRewriteRule rule) { return this; }
        @Override public Interval range() { return Interval.INFINITE; }
        @Override public int domainAxes() { return ALL_AXES; }
        @Override public MapCodec<? extends DensityFunction> codec() { return MapCodec.unit(this); }
    }

    /** Records how the native leaf was sampled: per point or once per request volume. */
    private static final class RecordingSampler implements DensitySampler {
        private final boolean blockY;
        private final float constant;
        private final AtomicInteger points = new AtomicInteger();
        private final List<DensityVolume> volumes = new ArrayList<>();

        RecordingSampler(boolean blockY, float constant) {
            this.blockY = blockY;
            this.constant = constant;
        }

        @Override public float sampleValue(SamplerContext context, int x, int y, int z) {
            this.points.incrementAndGet();
            return this.blockY ? y : this.constant;
        }

        @Override public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
            this.volumes.add(volume);
            for (int z = 0; z < volume.sizeZ(); ++z) {
                for (int x = 0; x < volume.sizeX(); ++x) {
                    for (int y = 0; y < volume.sizeY(); ++y) {
                        output.set(volume.indexUnchecked(x, y, z),
                                this.blockY ? volume.blockY(y) : this.constant);
                    }
                }
            }
        }
    }

    /** An extension type the registry does not know about: full volume semantics. */
    private static final class CountingDelegate extends TestDelegate {
        final RecordingSampler sampler = new RecordingSampler(true, 0.0F);

        @Override public DensitySampler compileSampler(CompileContext context) { return this.sampler; }
    }

    /** Registered as Y-independent: the compiler may collapse it to one column slot. */
    private static final class RegisteredDelegate extends TestDelegate {
        final RecordingSampler sampler = new RecordingSampler(false, 7.0F);

        @Override public DensitySampler compileSampler(CompileContext context) { return this.sampler; }
    }
}
