package com.moepus.byepregen.dfc;

import com.moepus.byepregen.dfc.compile.DensitySamplerCompiler;
import com.moepus.byepregen.dfc.runtime.CompiledDensitySampler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.densityfunction.generator.ConstantFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DensitySamplerCompilerTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void allArithmeticOperationsMatchVanillaPointAndVolumeSemantics() {
        DensityFunction a = DensityFunctions.yClampedGradient(-16, 24, -2.0F, 3.0F);
        DensityFunction b = DensityFunctions.yClampedGradient(-8, 20, 0.25F, 2.0F);
        List<DensityFunction> functions = new ArrayList<>();
        for (UnaryFunction.Type type : UnaryFunction.Type.values()) functions.add(new UnaryFunction(type, a));
        for (BinaryFunction.Type type : BinaryFunction.Type.values()) {
            functions.add(new BinaryFunction(type, a, b));
            functions.add(new BinaryFunction(type, a, DensityFunctions.constant(3.0F)));
            functions.add(new BinaryFunction(type, DensityFunctions.constant(3.0F), a));
        }
        functions.add(a.clamp(-0.5F, 0.75F));
        functions.add(DensityFunctions.lerp(a, b, a.negate()));
        functions.add(DensityFunctions.lerp(a, DensityFunctions.constant(2), b));
        functions.add(DensityFunctions.lerp(a, b, DensityFunctions.constant(2)));
        for (DensityFunction function : functions) {
            DensitySampler vanilla = function.compileSampler(DfcFixtures.CONTEXT);
            DensitySampler compiled = DensitySamplerCompiler.compile(function, DfcFixtures.CONTEXT);
            DfcFixtures.compare(vanilla, compiled);
        }
    }

    @Test void reciprocalCompilesAsOneOverInput() {
        DensityFunction input = DensityFunctions.yClampedGradient(0, 12, 2, 8).reciprocal();
        DensitySampler compiled = DensitySamplerCompiler.compile(input, DfcFixtures.CONTEXT);
        assertInstanceOf(CompiledDensitySampler.class, compiled);
        for (int y = 0; y <= 12; y += 4) {
            assertEquals(1.0F / (2.0F + y / 2.0F), compiled.sampleValue(SamplerContext.EMPTY_UNCACHED, 0, y, 0));
        }
    }

    @Test void constantDivisionPreservesVanillasSelectedReciprocal() {
        DensitySampler compiled = DensitySamplerCompiler.optimize(new BinaryFunction.ConstMulSampler(
                new DfcFixtures.CountingSampler(0.37F), 1.0F / 3.0F));
        assertEquals(0.37F * (1.0F / 3.0F), compiled.sampleValue(SamplerContext.EMPTY_UNCACHED, 0, 0, 0));
    }

    @Test void zeroShortCircuitIsPointOnlyAndUnknownSamplersKeepTheirCalls() {
        for (boolean divide : new boolean[]{false, true}) {
            var left = new DfcFixtures.CountingSampler(0);
            var right = new DfcFixtures.CountingSampler(Float.NaN);
            DensitySampler sampler = divide ? new BinaryFunction.DivSampler(left, right) : new BinaryFunction.MulSampler(left, right);
            DensitySampler compiled = DensitySamplerCompiler.optimize(sampler);
            assertEquals(0, compiled.sampleValue(SamplerContext.EMPTY_UNCACHED, 0, 0, 0));
            assertEquals(0, right.points);
            DensityBuffer buffer = DensityBuffer.createUnpooled(1);
            compiled.sampleVolume(SamplerContext.EMPTY_UNCACHED, buffer, new DensityVolume(1, 1, 1, 0, 0, 0));
            assertTrue(Float.isNaN(buffer.get(0)));
            assertEquals(1, left.volumes);
            assertEquals(1, right.volumes);
        }
    }

    @Test void minMaxAndLerpKeepPointShortCircuitsAndVolumeEagerness() {
        var first = new DfcFixtures.CountingSampler(4);
        var second = new DfcFixtures.CountingSampler(8);
        DensitySampler lerp = DensitySamplerCompiler.optimize(new LerpFunction.Sampler(new ConstantFunction.Sampler(0), first, second));
        assertEquals(4, lerp.sampleValue(SamplerContext.EMPTY_UNCACHED, 0, 0, 0));
        assertEquals(0, second.points);
        DensityBuffer buffer = DensityBuffer.createUnpooled(1);
        lerp.sampleVolume(SamplerContext.EMPTY_UNCACHED, buffer, new DensityVolume(1, 1, 1, 0, 0, 0));
        assertEquals(1, second.volumes);
        DensitySampler min = DensitySamplerCompiler.optimize(new BinaryFunction.MinSampler(first, second, 8));
        assertEquals(4, min.sampleValue(SamplerContext.EMPTY_UNCACHED, 0, 0, 0));
        assertEquals(0, second.points);
    }

    @Test void signedZeroAndNonFiniteInputsFollowTheirRespectiveSamplingPaths() {
        for (float a : new float[]{0.0F, -0.0F, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            var left = new DfcFixtures.CountingSampler(a);
            for (DensitySampler sampler : List.of(new BinaryFunction.ConstMinSampler(left, -0.0F),
                    new BinaryFunction.ConstMaxSampler(left, 0.0F), new UnaryFunction.ReciprocalSampler(left),
                    new UnaryFunction.SqrtSampler(left), new UnaryFunction.SqueezeSampler(left))) {
                DfcFixtures.compare(sampler, DensitySamplerCompiler.optimize(sampler));
            }
        }
    }

    @Test void compilationPreservesInterpolationAndConditionalBoundaries() {
        DensityFunction density = DensityFunctions.yClampedGradient(-20, 31, -0.8F, 1.2F).square().add(0.03F).reciprocal();
        List<DensityFunction> functions = List.of(
                DensityFunctions.interpolated(density, 4, 8).mul(0.7F).squeeze(),
                DensityFunctions.rangeChoice(density, 0.5F, 3.0F, density.negate(), density.add(2.0F)),
                DensityFunctions.sliceY(density.mul(2).add(3), 5));
        for (DensityFunction function : functions) {
            DfcFixtures.compare(function.compileSampler(DfcFixtures.CONTEXT), DensitySamplerCompiler.compile(function, DfcFixtures.CONTEXT));
        }
    }
}
