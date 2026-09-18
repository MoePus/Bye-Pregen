package com.moepus.byepregen.dfc;

import com.moepus.byepregen.dfc.ColumnTestSupport.TrackingArena;
import com.moepus.byepregen.dfc.compile.DensitySamplerCompiler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction;
import net.minecraft.util.context.ContextKey;
import net.minecraft.util.context.ContextMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CompiledBufferTest {
    @Test void cachedVolumesAndContextBindingsStayScopedToTheirContext() {
        var fallback = new DfcFixtures.CountingSampler(-3);
        var overridden = new DfcFixtures.CountingSampler(7);
        ContextKey<DensitySampler> key = ContextKey.vanilla("dfc_test");
        var bound = new ContextBoundSampler(key, fallback);
        var cached = new CachingDensitySampler(3, bound);
        var compiled = DensitySamplerCompiler.optimize(new BinaryFunction.AddSampler(cached, cached));
        SamplerContext first = SamplerContext.builder().enableCaches().build();
        SamplerContext second = SamplerContext.builder().enableCaches()
                .setUserFields(ContextMap.builder().set(key, overridden).build()).build();
        DensityVolume volume = DfcFixtures.VOLUMES[0];
        DensityBuffer buffer = DensityBuffer.createUnpooled(volume.size());
        compiled.sampleVolume(first, buffer, volume);
        assertEquals(-6, buffer.get(0));
        compiled.sampleVolume(second, buffer, volume);
        assertEquals(14, buffer.get(0));
        assertEquals(1, fallback.volumes);
        assertEquals(1, overridden.volumes);
        assertEquals(14, compiled.sampleValue(second, volume.minBlockX(), volume.minBlockY(), volume.minBlockZ()));
        assertEquals(0, overridden.points);
        compiled.sampleVolume(second, buffer, volume);
        assertEquals(1, overridden.volumes);
    }

    @Test void unknownSamplersAreNotMergedByCommonSubexpressionElimination() {
        var source = new DfcFixtures.CountingSampler(3);
        var compiled = DensitySamplerCompiler.optimize(new BinaryFunction.AddSampler(source, source));
        assertEquals(6, compiled.sampleValue(SamplerContext.EMPTY_UNCACHED, 0, 0, 0));
        assertEquals(2, source.points);
        DensityVolume volume = DfcFixtures.VOLUMES[0];
        compiled.sampleVolume(SamplerContext.EMPTY_UNCACHED, DensityBuffer.createUnpooled(volume.size()), volume);
        assertEquals(2, source.volumes);
    }

    @Test void fusedArithmeticUsesOneOutputPassAndReleasesScratchAfterFailure() throws Exception {
        var first = new DfcFixtures.CountingSampler(2);
        var second = new DfcFixtures.CountingSampler(3);
        var last = new DfcFixtures.CountingSampler(4);
        DensitySampler expression = new BinaryFunction.ConstMulSampler(new BinaryFunction.AddSampler(
                new BinaryFunction.ConstAddSampler(first, 2), new BinaryFunction.MulSampler(second, last)), 0.5F);
        DensitySampler compiled = DensitySamplerCompiler.optimize(expression);
        TrackingArena arena = new TrackingArena();
        SamplerContext context = SamplerContext.builder().useBufferArena(arena).build();
        DensityVolume volume = DfcFixtures.VOLUMES[0];
        CountingBuffer buffer = new CountingBuffer(volume.size());
        compiled.sampleVolume(context, buffer, volume);
        assertEquals(volume.size(), buffer.stores);
        assertEquals(3, arena.acquired);
        assertEquals(arena.acquired, arena.released);
        for (int i = 0; i < buffer.size(); ++i) assertEquals(8, buffer.get(i));
        last.fail = true;
        assertThrows(IllegalStateException.class, () -> compiled.sampleVolume(context, buffer, volume));
        assertEquals(arena.acquired, arena.released);
        last.fail = false;
        compiled.sampleVolume(context, buffer, volume);
        assertEquals(arena.acquired, arena.released);
    }

    @Test void oneCompiledSamplerCanServeIndependentConcurrentContexts() throws Exception {
        DensitySampler source = new DensitySampler() {
            @Override public float sampleValue(SamplerContext context, int x, int y, int z) { return x + y * 0.25F - z; }
            @Override public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
                DensitySampler.sampleVolumeNaive(context, output, volume, this);
            }
        };
        DensitySampler vanilla = new BinaryFunction.ConstMulSampler(new BinaryFunction.AddSampler(source, source), 0.5F);
        DensitySampler compiled = DensitySamplerCompiler.optimize(vanilla);
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 12; ++i) futures.add(executor.submit(() -> DfcFixtures.compare(vanilla, compiled)));
            for (var future : futures) future.get();
        }
    }

    private static final class CountingBuffer extends DensityBuffer {
        int stores;
        CountingBuffer(int size) { super(size); }
        @Override public void set(int index, float value) { ++this.stores; super.set(index, value); }
    }
}
