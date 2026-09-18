package com.moepus.byepregen.dfc;

import com.moepus.byepregen.dfc.compile.DensitySamplerCompiler;
import com.moepus.byepregen.dfc.runtime.NativeDensitySource;
import java.util.ArrayList;
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
import net.minecraft.world.level.levelgen.synth.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.moepus.byepregen.dfc.DfcFixtures.prepared;

final class NativeLeafCacheTest {
    private static final DensityVolume VOLUME = new DensityVolume(4, 16, 3, -4, 0, 8);
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void preparedShiftACacheRetainsCompactBatchesAndNativeRootCacheReuse() throws Exception {
        CountingNoise noise = new CountingNoise();
        DensityFunction shift = new ShiftNoiseFunction.ShiftA(Holder.direct(NormalNoise.createParity(0, 1)));
        DensityFunction.CompileContext context = context(noise);
        DensityFunction prepared = prepared(shift, context, 0);
        DensityFunction parent = prepared.add(DensityFunctions.yClampedGradient(0, 15, 0, 1));
        ColumnTestSupport.sample(DensitySamplerCompiler.compile(parent, context), SamplerContext.EMPTY_UNCACHED, VOLUME);
        assertEquals(VOLUME.sizeX() * VOLUME.sizeZ(), noise.positions);
        assertEquals(1, noise.requests.size());
        assertEquals(1, noise.requests.getFirst().sizeY());

        noise.reset();
        DensitySampler cachedRoot = DensitySamplerCompiler.compile(prepared, context);
        SamplerContext samplerContext = SamplerContext.builder().enableCaches().build();
        DensityBuffer values = ColumnTestSupport.sample(cachedRoot, samplerContext, VOLUME);
        ColumnTestSupport.compare(values, ColumnTestSupport.sample(cachedRoot, samplerContext, VOLUME));
        assertEquals(values.get(2), cachedRoot.sampleValue(samplerContext, VOLUME.minBlockX(), 2, VOLUME.minBlockZ()));
        assertEquals(VOLUME.sizeX() * VOLUME.sizeZ(), noise.positions);
    }

    @Test void purePreparedNativeLeafIsNotEvaluatedInAnUnselectedBranch() throws Exception {
        CountingNoise noise = new CountingNoise();
        DensityFunction.CompileContext context = context(noise);
        DensityFunction shift = new ShiftNoiseFunction.ShiftA(Holder.direct(NormalNoise.createParity(0, 1)));
        DensityFunction branch = prepared(shift, context, 1);
        DensityFunction y = DensityFunctions.yClampedGradient(0, 15, 0, 15);
        DensityFunction graph = DensityFunctions.rangeChoice(y, 20, 25, branch, DensityFunctions.zero());
        DensityBuffer values = ColumnTestSupport.sample(DensitySamplerCompiler.compile(graph, context),
                SamplerContext.EMPTY_UNCACHED, VOLUME);
        assertEquals(0, noise.positions);
        for (int i = 0; i < values.size(); ++i) assertEquals(0, values.get(i));
    }

    @Test void preparedGradientRetainsTrustedMetadataInsteadOfBecomingOpaque() throws Exception {
        DensityFunction gradient = DensityFunctions.yClampedGradient(0, 15, -1, 1);
        DensitySampler leaf = DensitySamplerCompiler.compile(gradient, DfcFixtures.CONTEXT);
        NativeDensitySource source = assertInstanceOf(NativeDensitySource.class, leaf);
        assertFalse(source.eager());
        assertEquals(DensityFunction.AXIS_Y, source.axes());
        DensityFunction cached = prepared(gradient, DfcFixtures.CONTEXT, 2);
        var arena = new ColumnTestSupport.TrackingArena();
        ColumnTestSupport.sample(DensitySamplerCompiler.compile(cached.add(0.5F), DfcFixtures.CONTEXT),
                arena.context(false), VOLUME);
        assertEquals(List.of(VOLUME.sizeY()), arena.requests, "A Y-only gradient retains only its Y samples");
        assertEquals(arena.acquired, arena.released);
        DfcFixtures.compare(gradient.add(0.5F).compileSampler(DfcFixtures.CONTEXT),
                DensitySamplerCompiler.compile(cached.add(0.5F), DfcFixtures.CONTEXT));
    }

    @Test void unknownContextBoundNativeWrapperRemainsOpaque() {
        var source = new DfcFixtures.CountingSampler(2);
        var nativeWrapper = new ContextBoundSampler(ContextKey.vanilla("dfc_leaf_metadata_test"), source);
        var function = new DfcFixtures.Function(nativeWrapper);
        assertSame(nativeWrapper, DensitySamplerCompiler.compile(function, DfcFixtures.CONTEXT));
        ColumnTestSupport.sample(DensitySamplerCompiler.compile(function.add(1), DfcFixtures.CONTEXT),
                SamplerContext.EMPTY_UNCACHED, VOLUME);
        assertEquals(1, source.volumes);
    }

    @Test void alreadyCompactedLeafWritesIntoTheCallerBufferWithoutAnotherWorkspace() {
        DensityFunction gradient = DensityFunctions.yClampedGradient(0, 15, -1, 1);
        var compiled = DensitySamplerCompiler.compile(gradient, DfcFixtures.CONTEXT);
        var volume = new DensityVolume(1, 8, 1, -11, -3, 9, 1, 3, 1);
        var arena = new ColumnTestSupport.TrackingArena();
        var actual = ColumnTestSupport.sample(compiled, arena.context(false), volume);
        assertEquals(0, arena.acquired, "The already sliced volume needs no intermediate sample buffer");
        ColumnTestSupport.compare(ColumnTestSupport.sample(gradient.compileSampler(DfcFixtures.CONTEXT),
                SamplerContext.EMPTY_UNCACHED, volume), actual);
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
        private final List<DensityVolume> requests = new ArrayList<>();
        @Override public Interval range() { return Interval.of(-1, 1); }
        @Override public float get(double x, double z) { return this.get(x, 0, z); }
        @Override public float get(double x, double y, double z) {
            ++this.positions;
            return (float) Math.sin(x + y + z);
        }
        @Override public void addToVolume(DensityBuffer output, DensityVolume volume, double xz, double y, float amplitude) {
            this.requests.add(volume);
            Noise.super.addToVolume(output, volume, xz, y, amplitude);
        }
        private void reset() { this.positions = 0; this.requests.clear(); }
    }
}
