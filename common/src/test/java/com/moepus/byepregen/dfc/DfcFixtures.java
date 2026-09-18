package com.moepus.byepregen.dfc;

import com.mojang.serialization.MapCodec;
import java.lang.reflect.Constructor;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Interval;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import static org.junit.jupiter.api.Assertions.*;

final class DfcFixtures {
    static final DensityFunction.CompileContext CONTEXT = new DensityFunction.CompileContext() {
        @Override public Noise createNoiseSampler(Holder<NormalNoise> parameters) {
            throw new AssertionError("This fixture has no noise bindings");
        }
        @Override public RandomSource createRandom(Identifier seed) { return RandomSource.create(seed.hashCode()); }
        @Override public RandomSource createEndIslandRandom() { return RandomSource.create(1234); }
    };
    static final DensityVolume[] VOLUMES = {
            new DensityVolume(3, 17, 5, -9, -15, -3, 1, 1, 1),
            new DensityVolume(3, 5, 2, -8, -16, 0, 4, 8, 4),
            new DensityVolume(3, 7, 2, -7, -11, 5, 2, 3, 5)
    };

    private DfcFixtures() { }

    static DensityFunction prepared(DensityFunction input, DensityFunction.CompileContext context, int id)
            throws ReflectiveOperationException {
        Class<?> type = Class.forName("net.minecraft.world.level.levelgen.densityfunction.DensityFunctionCompiler$PreparedCache");
        Constructor<?> constructor = type.getDeclaredConstructor(int.class, Interval.class, int.class, DensitySampler.class);
        constructor.setAccessible(true);
        var sampler = new CachingDensitySampler(id,
                com.moepus.byepregen.dfc.compile.DensitySamplerCompiler.compile(input, context));
        return (DensityFunction) constructor.newInstance(id, input.range(), input.domainAxes(), sampler);
    }

    static void compare(DensitySampler expected, DensitySampler actual) {
        for (DensityVolume volume : VOLUMES) {
            DensityBuffer a = DensityBuffer.createUnpooled(volume.size());
            DensityBuffer b = DensityBuffer.createUnpooled(volume.size());
            expected.sampleVolume(SamplerContext.EMPTY_UNCACHED, a, volume);
            actual.sampleVolume(SamplerContext.EMPTY_UNCACHED, b, volume);
            for (int i = 0; i < volume.size(); ++i) near(a.get(i), b.get(i));
            for (int y = -19; y < 24; ++y) {
                near(expected.sampleValue(SamplerContext.EMPTY_UNCACHED, -7, y, 13),
                        actual.sampleValue(SamplerContext.EMPTY_UNCACHED, -7, y, 13));
            }
        }
    }

    static void near(float expected, float actual) {
        if (!Float.isFinite(expected)) { assertEquals(expected, actual); return; }
        float tolerance = 2.0e-5F * Math.max(1.0F, Math.abs(expected));
        assertEquals(expected, actual, tolerance);
    }

    static final class CountingSampler implements DensitySampler {
        int points;
        int volumes;
        final float value;
        boolean fail;
        CountingSampler(float value) { this.value = value; }
        @Override public float sampleValue(SamplerContext context, int x, int y, int z) {
            ++this.points;
            if (this.fail) throw new IllegalStateException("fixture failure");
            return this.value;
        }
        @Override public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
            ++this.volumes;
            if (this.fail) throw new IllegalStateException("fixture failure");
            output.fill(this.value);
        }
    }

    record Function(DensitySampler sampler) implements DensityFunction {
        @Override public DensitySampler compileSampler(CompileContext context) { return this.sampler; }
        @Override public DensityFunction rewriteChildren(DfRewriteRule rule) { return this; }
        @Override public Interval range() { return Interval.INFINITE; }
        @Override public int domainAxes() { return ALL_AXES; }
        @Override public MapCodec<? extends DensityFunction> codec() { return MapCodec.unit(this); }
    }
}
