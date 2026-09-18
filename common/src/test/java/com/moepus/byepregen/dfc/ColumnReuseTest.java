package com.moepus.byepregen.dfc;

import com.moepus.byepregen.dfc.compile.DensitySamplerCompiler;
import com.moepus.byepregen.dfc.runtime.*;
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
import net.minecraft.world.level.levelgen.synth.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ColumnReuseTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void pureNoiseBranchesOnlySampleSelectedYRunsAndSkipEmptyBranches() {
        CountingNoise noise = new CountingNoise();
        DensityFunction y = DensityFunctions.yClampedGradient(0, 10, 0, 10);
        DensityFunction source = noiseFunction(1);
        var volume = new DensityVolume(2, 10, 1, 0, 0, 0);
        DensityFunction graph = DensityFunctions.rangeChoice(y, 3, 7, source, DensityFunctions.zero());
        DensitySampler compiled = DensitySamplerCompiler.compile(graph, context(noise));
        ColumnTestSupport.sample(compiled, SamplerContext.EMPTY_UNCACHED, volume);
        assertEquals(8, noise.positions);
        assertEquals(2, noise.volumes.size());
        for (DensityVolume request : noise.volumes) {
            assertEquals(3, request.minBlockY());
            assertEquals(4, request.sizeY());
        }
        noise.reset();
        graph = DensityFunctions.rangeChoice(y, 11, 12, source, DensityFunctions.zero());
        ColumnTestSupport.sample(DensitySamplerCompiler.compile(graph, context(noise)), SamplerContext.EMPTY_UNCACHED, volume);
        assertEquals(0, noise.positions);
    }

    @Test void overlappingSelectedRunsReuseAlreadyComputedSamples() {
        CountingNoise noise = new CountingNoise();
        DensityFunction y = DensityFunctions.yClampedGradient(0, 10, 0, 10), source = noiseFunction(1);
        DensityFunction graph = DensityFunctions.rangeChoice(y, 2, 6, source, DensityFunctions.zero())
                .add(DensityFunctions.rangeChoice(y, 4, 8, source, DensityFunctions.zero()));
        var volume = new DensityVolume(2, 10, 1, 0, 0, 0);
        ColumnTestSupport.sample(DensitySamplerCompiler.compile(graph, context(noise)), SamplerContext.EMPTY_UNCACHED, volume);
        assertEquals(12, noise.positions, "Six unique Y samples per XZ column");
    }

    @Test void twoDimensionalNoiseSurvivesAtoBtoAWithoutYRecomputation() {
        CountingNoise noise = new CountingNoise();
        DensityFunction graph = noiseFunction(0).add(DensityFunctions.yClampedGradient(0, 31, 0, 1));
        var compiled = (ColumnDensitySampler) DensitySamplerCompiler.compile(graph, context(noise));
        var volume = new DensityVolume(4, 32, 3, -4, 0, 8);
        float[] first = new float[32], second = new float[32], again = new float[32];
        try (ColumnSession session = compiled.openColumns(SamplerContext.EMPTY_UNCACHED, volume)) {
            session.evalColumn(0, 0, first);
            session.evalColumn(1, 0, second);
            session.evalColumn(0, 0, again);
        }
        assertArrayEquals(first, again);
        assertEquals(12, noise.positions);
        assertEquals(1, noise.volumes.size());
        assertEquals(1, noise.volumes.getFirst().sizeY());
    }

    @Test void requestedCacheRootPreservesNativeVolumeAndPointReuse() {
        CountingNoise noise = new CountingNoise();
        DensitySampler child = DensitySamplerCompiler.compile(noiseFunction(1).add(2), context(noise));
        DensitySampler cached = new CachingDensitySampler(7, child);
        DensitySampler compiled = DensitySamplerCompiler.compile(new DfcFixtures.Function(cached), context(noise));
        var volume = new DensityVolume(2, 8, 2, 0, 0, 0);
        SamplerContext context = SamplerContext.builder().enableCaches().build();
        DensityBuffer first = ColumnTestSupport.sample(compiled, context, volume);
        int samples = noise.positions;
        ColumnTestSupport.compare(first, ColumnTestSupport.sample(compiled, context, volume));
        assertEquals(first.get(3), compiled.sampleValue(context, 0, 3, 0));
        assertEquals(samples, noise.positions);
    }

    @Test void generatedPointCallsPreserveNativeDoubleNoiseCoordinates() {
        CountingNoise noise = new CountingNoise();
        DensityFunction graph = noiseFunction(0.000000317).mul(0.7F).add(0.1F);
        DensitySampler compiled = DensitySamplerCompiler.compile(graph, context(noise));
        compiled.sampleValue(SamplerContext.EMPTY_UNCACHED, 29_999_999, 257, -29_999_999);
        assertEquals(29_999_999 * 0.000000913, noise.lastX);
        assertEquals(257 * 0.000000317, noise.lastY);
        assertEquals(-29_999_999 * 0.000000913, noise.lastZ);
    }

    private static NoiseFunction noiseFunction(double yScale) {
        return new NoiseFunction(null, 0.000000913, yScale,
                DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero());
    }

    private static DensityFunction.CompileContext context(CountingNoise noise) {
        return new DensityFunction.CompileContext() {
            @Override public Noise createNoiseSampler(Holder<NormalNoise> parameters) { return noise; }
            @Override public RandomSource createRandom(Identifier seed) { return RandomSource.create(1); }
            @Override public RandomSource createEndIslandRandom() { return RandomSource.create(1); }
        };
    }

    private static final class CountingNoise implements Noise {
        private int positions;
        private double lastX, lastY, lastZ;
        private final List<DensityVolume> volumes = new ArrayList<>();
        @Override public Interval range() { return Interval.of(-1, 1); }
        @Override public float get(double x, double z) { return this.get(x, 0, z); }
        @Override public float get(double x, double y, double z) {
            ++this.positions;
            this.lastX = x; this.lastY = y; this.lastZ = z;
            return (float) Math.sin(x + y * 0.13 + z);
        }
        @Override public void addToVolume(DensityBuffer output, DensityVolume volume, double xz, double y, float amplitude) {
            this.volumes.add(volume);
            Noise.super.addToVolume(output, volume, xz, y, amplitude);
        }
        private void reset() { this.positions = 0; this.volumes.clear(); }
    }
}
