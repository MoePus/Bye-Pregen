package com.moepus.byepregen.dfc;

import com.moepus.byepregen.dfc.compile.DensitySamplerCompiler;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction;
import static org.junit.jupiter.api.Assertions.*;

final class ColumnTestSupport {
    static final DensityVolume FULL = new DensityVolume(16, 384, 16, -32, -64, 16);
    private ColumnTestSupport() { }

    static DensityFunction interpolated(DensitySampler sampler, int xz, int y) {
        return DensityFunctions.interpolated(new DfcFixtures.Function(sampler), xz, y);
    }

    static DensityFunction noodle(int xz, int y, Consumer<Call> trace) {
        DensityFunction a = interpolated(new Source(0, trace), xz, y);
        DensityFunction b = interpolated(new Source(1, trace), xz, y);
        DensityFunction c = interpolated(new Source(2, trace), xz, y);
        return a.add(new BinaryFunction(BinaryFunction.Type.MAX, b.abs(), c.abs()).mul(1.5F));
    }

    static DensitySampler compile(DensityFunction function) {
        return DensitySamplerCompiler.compile(function, DfcFixtures.CONTEXT);
    }

    static DensityBuffer sample(DensitySampler sampler, SamplerContext context, DensityVolume volume) {
        DensityBuffer output = DensityBuffer.createUnpooled(volume.size());
        sampler.sampleVolume(context, output, volume);
        return output;
    }

    static void compare(DensityBuffer expected, DensityBuffer actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); ++i) {
            DfcFixtures.near(expected.get(i), actual.get(i));
            if (expected.get(i) == 0.0F) assertEquals(Float.floatToRawIntBits(expected.get(i)),
                    Float.floatToRawIntBits(actual.get(i)), "Zero sign at " + i);
        }
    }

    record Call(int source, DensityVolume volume) { }

    static class Source implements DensitySampler {
        final int salt;
        final Consumer<Call> trace;
        boolean fail;
        Source(int salt, Consumer<Call> trace) { this.salt = salt; this.trace = trace; }
        @Override public float sampleValue(SamplerContext context, int x, int y, int z) {
            int hash = x * 73 + y * 31 + z * 97 + this.salt * 311;
            return (float) Math.sin(hash * 0.003) * 1.3F;
        }
        @Override public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
            this.trace.accept(new Call(this.salt, volume));
            if (this.fail) throw new IllegalStateException("Column source failure");
            DensitySampler.sampleVolumeNaive(context, output, volume, this);
        }
    }

    static final class TrackingArena implements DensityBufferArena {
        final Constructor<ScopedDensityBuffer> constructor;
        final List<Integer> requests = new ArrayList<>();
        int acquired, released, live, peak;
        TrackingArena() {
            try {
                this.constructor = ScopedDensityBuffer.class.getDeclaredConstructor(DensityBufferArena.class, int.class, int.class);
                this.constructor.setAccessible(true);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        @Override public ScopedDensityBuffer acquire(int size) {
            try {
                ScopedDensityBuffer buffer = this.constructor.newInstance(this, size, size);
                this.requests.add(size);
                ++this.acquired;
                this.live += size;
                this.peak = Math.max(this.peak, this.live);
                return buffer;
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        @Override public void release(ScopedDensityBuffer buffer) {
            Arrays.fill(buffer.values, Float.NaN);
            ++this.released;
            this.live -= buffer.size();
        }
        SamplerContext context(boolean caches) {
            var builder = SamplerContext.builder().useBufferArena(this);
            if (caches) builder.enableCaches();
            return builder.build();
        }
    }
}
