package com.moepus.byepregen.dfc;

import com.moepus.byepregen.dfc.compile.DensitySamplerCompiler;
import com.moepus.byepregen.dfc.runtime.ColumnDensitySampler;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Interval;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.densityfunction.generator.NoiseFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.IntervalSelectFunction;
import net.minecraft.world.level.levelgen.synth.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class IntervalSelectCompilerTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void thresholdEqualityDuplicatesAndNonFiniteValuesMatchNativePointAndVolume() {
        for (float[] thresholds : List.of(new float[0], new float[]{0},
                new float[]{-2, -0.0F, 0.0F, 2, 2},
                new float[]{Float.NEGATIVE_INFINITY, 0, Float.POSITIVE_INFINITY},
                new float[]{Float.NaN, 1})) {
            List<DensityFunction> branches = new ArrayList<>();
            for (int i = 0; i <= thresholds.length; ++i) branches.add(DensityFunctions.constant(i + 1));
            for (float value : new float[]{Float.NEGATIVE_INFINITY, -3, -2, -Float.MIN_VALUE,
                    -0.0F, 0.0F, Float.MIN_VALUE, 1, 2, 3, Float.POSITIVE_INFINITY, Float.NaN}) {
                // Keep this selector Y-dependent to exercise the column emitter as well as scalar code.
                var input = new DfcFixtures.Function(new DfcFixtures.CountingSampler(value));
                var graph = select(input, thresholds, branches);
                DfcFixtures.compare(graph.compileSampler(DfcFixtures.CONTEXT),
                        DensitySamplerCompiler.compile(graph, DfcFixtures.CONTEXT));
            }
        }
    }

    @Test void scalarSelectorIsEvaluatedOnceAcrossMultipleThresholds() {
        CountingNoise noise = new CountingNoise();
        DensityFunction input = noise();
        var graph = select(input, new float[]{-2, -1, 0, 1, 2}, List.of(
                DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(),
                DensityFunctions.constant(4), DensityFunctions.zero(), DensityFunctions.zero()));
        var compiled = DensitySamplerCompiler.compile(graph, context(noise));
        assertEquals(4, compiled.sampleValue(SamplerContext.EMPTY_UNCACHED, 0, 7, 0));
        assertEquals(1, noise.positions);
    }

    @Test void nestedIntervalsSampleOnlyActiveRunsInTheSameRequest() {
        CountingNoise noise = new CountingNoise();
        DensityFunction y = DensityFunctions.yClampedGradient(0, 15, 0, 15);
        DensityFunction source = noise();
        DensityFunction inner = select(y, new float[]{4, 6},
                List.of(source.add(1), source.add(2), source.add(3)));
        DensityFunction graph = DensityFunctions.rangeChoice(y, 2, 8, inner, DensityFunctions.zero());
        var volume = new DensityVolume(2, 16, 1, 0, 0, 0);
        var compiled = DensitySamplerCompiler.compile(graph, context(noise));
        DensityBuffer result = ColumnTestSupport.sample(compiled, SamplerContext.EMPTY_UNCACHED, volume);
        assertEquals(12, noise.positions);
        assertEquals(6, noise.requests.size());
        assertEquals(List.of(2, 4, 6, 2, 4, 6), noise.requests.stream().map(DensityVolume::minBlockY).toList());
        assertTrue(noise.requests.stream().allMatch(request -> request.sizeY() == 2));
        for (int x = 0; x < 2; ++x) {
            for (int i = 0; i < 16; ++i) {
                float expected = i < 2 || i >= 8 ? 0 : i < 4 ? 1.25F : i < 6 ? 2.25F : 3.25F;
                assertEquals(expected, result.get(volume.indexUnchecked(x, i, 0)));
            }
        }
    }

    @Test void opaqueSelectorAndBranchesKeepNativeBatchOrderEvenWhenNotSelected() {
        var calls = new ArrayList<String>();
        DensityFunction input = traced("input", 5, calls), first = traced("first", 11, calls);
        DensityFunction middle = traced("middle", 22, calls), last = traced("last", 33, calls);
        var graph = select(input, new float[]{0, 10}, List.of(first, middle, last));
        var nativeSampler = graph.compileSampler(DfcFixtures.CONTEXT);
        var compiled = DensitySamplerCompiler.compile(graph, DfcFixtures.CONTEXT);
        var volume = new DensityVolume(2, 8, 2, 0, 0, 0);
        var expected = ColumnTestSupport.sample(nativeSampler, SamplerContext.EMPTY_UNCACHED, volume);
        assertEquals(List.of("input", "first", "middle", "last"), calls);
        for (int request = 0; request < 2; ++request) {
            calls.clear();
            ColumnTestSupport.compare(expected, ColumnTestSupport.sample(compiled, SamplerContext.EMPTY_UNCACHED, volume));
            assertEquals(List.of("input", "first", "middle", "last"), calls);
        }
        calls.clear();
        assertEquals(22, compiled.sampleValue(SamplerContext.EMPTY_UNCACHED, 0, 0, 0));
        assertEquals(List.of("input", "middle"), calls);
    }

    @Test void repeatedPreparedTwoDimensionalBranchesReuseTheColumnMemo() throws Exception {
        CountingNoise noise = new CountingNoise();
        var context = context(noise);
        DensityFunction y = DensityFunctions.yClampedGradient(-8, 8, -8, 8);
        DensityFunction source = new NoiseFunction(Holder.direct(NormalNoise.createParity(0, 1)), 1, 0, DensityFunctions.zero(),
                DensityFunctions.zero(), DensityFunctions.zero());
        DensityFunction branch = DfcFixtures.prepared(source.add(1), context, 0);
        DensityFunction graph = select(y, new float[]{-2, 2}, List.of(branch, DensityFunctions.zero(), branch));
        var volume = new DensityVolume(2, 16, 1, 0, -8, 0);
        var compiled = DensitySamplerCompiler.compile(graph, context);
        var output = ColumnTestSupport.sample(compiled, SamplerContext.EMPTY_UNCACHED, volume);
        assertEquals(2, noise.positions, "Disjoint Y runs reuse each XZ value");
        ColumnTestSupport.compare(ColumnTestSupport.sample(graph.compileSampler(context),
                SamplerContext.EMPTY_UNCACHED, volume), output);
    }

    @Test void inputUsedAsABranchAndStridedColumnsRetainTheirValues() {
        DensityFunction y = DensityFunctions.yClampedGradient(-32, 64, -32, 64);
        DensityFunction graph = select(y, new float[]{-5, 3}, List.of(y, y.square(), y.add(1)));
        var compiled = (ColumnDensitySampler) DensitySamplerCompiler.compile(graph, DfcFixtures.CONTEXT);
        var volume = new DensityVolume(3, 12, 2, -4, -11, -7, 2, 3, 5);
        var expected = ColumnTestSupport.sample(graph.compileSampler(DfcFixtures.CONTEXT), SamplerContext.EMPTY_UNCACHED, volume);
        float[] column = new float[volume.sizeY()];
        try (var session = compiled.openColumns(SamplerContext.EMPTY_UNCACHED, volume)) {
            for (int z = 0; z < volume.sizeZ(); ++z) {
                for (int x = 0; x < volume.sizeX(); ++x) {
                    session.evalColumn(x, z, column);
                    for (int i = 0; i < column.length; ++i) {
                        assertEquals(expected.get(volume.indexUnchecked(x, i, z)), column[i]);
                    }
                }
            }
        }
        DfcFixtures.compare(graph.compileSampler(DfcFixtures.CONTEXT), compiled);
    }

    private static DensityFunction select(DensityFunction input, float[] thresholds, List<DensityFunction> branches) {
        return new IntervalSelectFunction(input, new FloatArrayList(thresholds), branches);
    }

    private static DensityFunction noise() {
        return new NoiseFunction(null, 1, 1, DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero());
    }

    private static DensityFunction traced(String name, float value, List<String> trace) {
        return new DfcFixtures.Function(new DensitySampler() {
            @Override public float sampleValue(SamplerContext context, int x, int y, int z) {
                trace.add(name);
                return value;
            }
            @Override public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
                trace.add(name);
                output.fill(value);
            }
        });
    }

    private static DensityFunction.CompileContext context(Noise noise) {
        return new DensityFunction.CompileContext() {
            @Override public Noise createNoiseSampler(Holder<NormalNoise> parameters) { return noise; }
            @Override public RandomSource createRandom(Identifier seed) { return RandomSource.create(1); }
            @Override public RandomSource createEndIslandRandom() { return RandomSource.create(1); }
        };
    }

    private static final class CountingNoise implements Noise {
        private int positions;
        private final List<DensityVolume> requests = new ArrayList<>();
        @Override public Interval range() { return Interval.of(-1, 1); }
        @Override public float get(double x, double z) { return this.get(x, 0, z); }
        @Override public float get(double x, double y, double z) { ++this.positions; return 0.25F; }
        @Override public void addToVolume(DensityBuffer output, DensityVolume volume, double xz, double y, float amplitude) {
            this.requests.add(volume);
            Noise.super.addToVolume(output, volume, xz, y, amplitude);
        }
    }
}
