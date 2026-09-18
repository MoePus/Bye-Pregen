package com.moepus.byepregen.dfc;

import com.moepus.byepregen.dfc.compile.DensitySamplerCompiler;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Interval;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.densityfunction.generator.ShiftNoiseFunction;
import net.minecraft.world.level.levelgen.densityfunction.generator.NoiseFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.IntervalSelectFunction;
import com.moepus.byepregen.dfc.runtime.ColumnDensitySampler;
import net.minecraft.world.level.levelgen.synth.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class NativeWrapperPurityTest {
    private static final DensityVolume VOLUME = new DensityVolume(4, 16, 3, -4, 0, 8);
    private static DensityFunction y;

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        y = DensityFunctions.yClampedGradient(0, 15, 0, 15);
    }

    @Test void unselectedPureWrappersDoNotSampleTheirNoiseIncludingPreparedChildren() throws Exception {
        CountingNoise noise = new CountingNoise();
        var context = context(noise);
        DensityFunction shift = shift();
        DensityFunction cached = DfcFixtures.prepared(shift, context, 0);
        for (DensityFunction child : List.of(shift, cached)) {
            for (DensityFunction wrapper : List.of(
                    select(y, child, child.add(1)),
                    DensityFunctions.lerp(child, y, y.add(1)),
                    DensityFunctions.rangeChoice(y, 4, 8, child, child.add(1)))) {
                // A slice is a native boundary that must propagate its child's purity too.
                DensityFunction branch = DensityFunctions.sliceY(wrapper, 6);
                var graph = DensityFunctions.rangeChoice(y, 20, 25, branch, DensityFunctions.zero());
                var output = ColumnTestSupport.sample(DensitySamplerCompiler.compile(graph, context),
                        SamplerContext.EMPTY_UNCACHED, VOLUME);
                assertEquals(0, noise.positions, wrapper.toString());
                for (int i = 0; i < output.size(); ++i) assertEquals(0, output.get(i));
            }
        }
    }

    @Test void twoDimensionalPreparedIntervalRootUsesOneSamplePerColumn() throws Exception {
        CountingNoise noise = new CountingNoise();
        var context = context(noise);
        DensityFunction cached = DfcFixtures.prepared(shift(), context, 0);
        DensityFunction graph = select(cached, DensityFunctions.constant(2), DensityFunctions.constant(3));
        var compiled = DensitySamplerCompiler.compile(graph, context);
        var output = ColumnTestSupport.sample(compiled, SamplerContext.EMPTY_UNCACHED, VOLUME);
        assertEquals(VOLUME.sizeX() * VOLUME.sizeZ(), noise.positions);
        DfcFixtures.compare(graph.compileSampler(context), compiled);
        for (int i = 0; i < output.size(); ++i) assertEquals(3, output.get(i));
    }

    @Test void unknownAndContextBoundChildrenRetainEagerNativeOrdering() {
        for (boolean bound : new boolean[]{false, true}) {
            var source = new DfcFixtures.CountingSampler(2);
            DensitySampler sampler = bound
                    ? new ContextBoundSampler(ContextKey.vanilla("wrapper_purity"), source) : source;
            var branch = select(y, new DfcFixtures.Function(sampler), DensityFunctions.zero());
            var graph = DensityFunctions.rangeChoice(y, 20, 25, branch, DensityFunctions.zero());
            var nativeOutput = ColumnTestSupport.sample(graph.compileSampler(DfcFixtures.CONTEXT),
                    SamplerContext.EMPTY_UNCACHED, VOLUME);
            int expectedCalls = source.volumes;
            assertTrue(expectedCalls > 0);
            source.volumes = 0;
            var compiled = DensitySamplerCompiler.compile(graph, DfcFixtures.CONTEXT);
            for (int request = 0; request < 2; ++request) {
                source.volumes = 0;
                ColumnTestSupport.compare(nativeOutput,
                        ColumnTestSupport.sample(compiled, SamplerContext.EMPTY_UNCACHED, VOLUME));
                assertEquals(expectedCalls, source.volumes);
            }
        }
    }

    @Test void selectedIntervalBranchesShareTheParentRequestAndOnlySampleTheirActiveRun() {
        CountingNoise noise = new CountingNoise();
        DensityFunction source = new NoiseFunction(null, 1, 1, DensityFunctions.zero(),
                DensityFunctions.zero(), DensityFunctions.zero());
        DensityFunction branch = select(y, source.add(1), source.add(2));
        DensityFunction graph = DensityFunctions.rangeChoice(y, 2, 8, branch, DensityFunctions.zero());
        var compiled = (ColumnDensitySampler) DensitySamplerCompiler.compile(graph, context(noise));
        float[] first = new float[VOLUME.sizeY()], next = first.clone(), again = first.clone();
        try (var session = compiled.openColumns(SamplerContext.EMPTY_UNCACHED, VOLUME)) {
            assertEquals(0, noise.batches);
            session.evalColumn(0, 0, first);
            assertEquals(1, noise.batches);
            assertEquals(6, noise.positions, "Only Y=2..7 in the selected branch is needed");
            session.evalColumn(0, 0, again);
            assertEquals(1, noise.batches, "Repeated column evaluation shares the parent request samples");
            assertArrayEquals(first, again);
            session.evalColumn(1, 0, next);
            assertEquals(2, noise.batches);
            assertEquals(12, noise.positions);
        }
        DfcFixtures.compare(graph.compileSampler(context(noise)), compiled);
    }

    private static DensityFunction select(DensityFunction input, DensityFunction low, DensityFunction high) {
        return new IntervalSelectFunction(input, FloatArrayList.of(0), List.of(low, high));
    }

    private static DensityFunction shift() {
        return new ShiftNoiseFunction.ShiftA(Holder.direct(NormalNoise.createParity(0, 1)));
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
        private int batches;
        @Override public Interval range() { return Interval.of(-1, 1); }
        @Override public float get(double x, double z) { return this.get(x, 0, z); }
        @Override public float get(double x, double y, double z) { ++this.positions; return 0.25F; }
        @Override public void addToVolume(DensityBuffer output, DensityVolume volume, double xz, double y, float amplitude) {
            ++this.batches;
            Noise.super.addToVolume(output, volume, xz, y, amplitude);
        }
    }
}
