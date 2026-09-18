package com.moepus.byepregen.dfc;

import com.moepus.byepregen.dfc.runtime.ColumnPlan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.densityfunction.op.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static com.moepus.byepregen.dfc.ColumnTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

final class ColumnSamplingTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void alignedVolumesUseCoarseInputsAndColumnSizedScratch() {
        for (int[] shape : new int[][]{{4, 8, 16, 384, 16}, {8, 4, 16, 128, 16}, {3, 5, 9, 30, 6}, {2, 2, 2, 2, 2}}) {
            List<Call> trace = new ArrayList<>();
            var function = noodle(shape[0], shape[1], trace::add);
            var volume = new DensityVolume(shape[2], shape[3], shape[4], -shape[2], -shape[3], -shape[4]);
            DensityBuffer expected = sample(function.compileSampler(DfcFixtures.CONTEXT), SamplerContext.EMPTY_UNCACHED, volume);
            List<Call> calls = List.copyOf(trace);
            trace.clear();
            TrackingArena arena = new TrackingArena();
            compare(expected, sample(compile(function), arena.context(false), volume));
            assertEquals(calls, trace);
            assertEquals(3, trace.size());
            assertEquals(new ColumnPlan(shape[0], shape[1]).gridVolume(volume), trace.getFirst().volume());
            assertTrue(arena.requests.stream().allMatch(size -> size < volume.size() || volume.size() == 8));
            assertEquals(arena.acquired, arena.released);
        }
        TrackingArena arena = new TrackingArena();
        sample(compile(noodle(4, 8, call -> { })), arena.context(false), FULL);
        assertTrue(arena.peak < FULL.size() / 10, "Column workspace must not retain full interpolation volumes");
    }

    @Test void nonlinearityRemainsAfterInterpolationAndAllArithmeticKeepsVolumeSemantics() {
        var a = interpolated(new Source(1, call -> { }), 4, 8);
        var b = interpolated(new Source(2, call -> { }), 4, 8);
        var volume = new DensityVolume(8, 32, 8, -8, -32, 8);
        List<DensityFunction> functions = new ArrayList<>();
        for (UnaryFunction.Type type : UnaryFunction.Type.values()) functions.add(new UnaryFunction(type, a).add(b));
        for (BinaryFunction.Type type : BinaryFunction.Type.values()) functions.add(new BinaryFunction(type, a, b));
        functions.add(DensityFunctions.lerp(a, b, a.square()));
        functions.add(a.clamp(-0.2F, 0.7F).mul(b));
        for (DensityFunction function : functions) {
            compare(sample(function.compileSampler(DfcFixtures.CONTEXT), SamplerContext.EMPTY_UNCACHED, volume),
                    sample(compile(function), SamplerContext.EMPTY_UNCACHED, volume));
            DfcFixtures.compare(function.compileSampler(DfcFixtures.CONTEXT), compile(function));
        }
    }

    @Test void nonFiniteCornersAndSignedZeroFollowNativeInterpolationAndArithmetic() {
        float[] values = {0.0F, -0.0F, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.MAX_VALUE, -Float.MAX_VALUE, Float.MIN_VALUE, -Float.MIN_VALUE};
        for (float value : values) {
            var first = interpolated(new DfcFixtures.CountingSampler(value), 4, 8);
            var second = interpolated(new DfcFixtures.CountingSampler(-0.0F), 4, 8);
            var volume = new DensityVolume(8, 16, 8, -8, -16, 8);
            for (BinaryFunction.Type type : BinaryFunction.Type.values()) {
                DensityFunction graph = new BinaryFunction(type, first, second);
                compare(sample(graph.compileSampler(DfcFixtures.CONTEXT), SamplerContext.EMPTY_UNCACHED, volume),
                        sample(compile(graph), SamplerContext.EMPTY_UNCACHED, volume));
            }
        }
    }

    @Test void nonAlignedStridedAndMixedGeometriesKeepTheirNativeVolumes() {
        for (DensityVolume volume : DfcFixtures.VOLUMES) {
            List<Call> trace = new ArrayList<>();
            var graph = noodle(4, 8, trace::add);
            DensityBuffer expected = sample(graph.compileSampler(DfcFixtures.CONTEXT), SamplerContext.EMPTY_UNCACHED, volume);
            List<Call> calls = List.copyOf(trace);
            trace.clear();
            compare(expected, sample(compile(graph), SamplerContext.EMPTY_UNCACHED, volume));
            assertEquals(calls, trace);
        }
        for (int[] geometry : new int[][]{{4, 4}, {8, 8}, {1, 1}, {1, 8}, {4, 1}}) {
            var graph = interpolated(new Source(1, call -> { }), 4, 8)
                    .add(interpolated(new Source(2, call -> { }), geometry[0], geometry[1]));
            compare(sample(graph.compileSampler(DfcFixtures.CONTEXT), SamplerContext.EMPTY_UNCACHED, FULL),
                    sample(compile(graph), SamplerContext.EMPTY_UNCACHED, FULL));
        }
        for (int[] geometry : new int[][]{{1, 1}, {1, 8}, {4, 1}}) {
            var graph = noodle(geometry[0], geometry[1], call -> { });
            compare(sample(graph.compileSampler(DfcFixtures.CONTEXT), SamplerContext.EMPTY_UNCACHED, FULL),
                    sample(compile(graph), SamplerContext.EMPTY_UNCACHED, FULL));
        }
    }

    @Test void customOutputBuffersKeepTheirOverriddenStoresAndBadSizesFailBeforeSampling() {
        List<Call> trace = new ArrayList<>();
        var graph = noodle(4, 8, trace::add);
        DensitySampler sampler = compile(graph);
        var volume = new DensityVolume(4, 16, 4, 0, 0, 0);
        RecordingBuffer output = new RecordingBuffer(volume.size());
        sampler.sampleVolume(SamplerContext.EMPTY_UNCACHED, output, volume);
        assertEquals(volume.size(), output.stores);
        compare(sample(graph.compileSampler(DfcFixtures.CONTEXT), SamplerContext.EMPTY_UNCACHED, volume), output);
        trace.clear();
        assertThrows(IllegalArgumentException.class, () -> sampler.sampleVolume(SamplerContext.EMPTY_UNCACHED,
                DensityBuffer.createUnpooled(volume.size() - 1), volume));
        assertTrue(trace.isEmpty());
    }

    private static final class RecordingBuffer extends DensityBuffer {
        int stores;
        RecordingBuffer(int size) { super(size); }
        @Override public void set(int index, float value) { ++this.stores; super.set(index, value); }
    }
}
