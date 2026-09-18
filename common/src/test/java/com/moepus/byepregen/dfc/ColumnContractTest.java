package com.moepus.byepregen.dfc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.densityfunction.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static com.moepus.byepregen.dfc.ColumnTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

final class ColumnContractTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void cachedGridSurvivesTheSameCacheBeingReplacedByADifferentVolume() {
        List<Call> trace = new ArrayList<>();
        var source = new Source(0, trace::add);
        var cached = new CachingDensitySampler(0, source);
        var a = interpolated(cached, 4, 8);
        var b = interpolated(new Source(1, trace::add), 4, 8);
        var graph = a.add(new DfcFixtures.Function(cached)).add(b);
        DensitySampler vanilla = graph.compileSampler(DfcFixtures.CONTEXT), compiled = compile(graph);
        TrackingArena arena = new TrackingArena();
        SamplerContext expectedContext = SamplerContext.builder().enableCaches().build();
        SamplerContext context = arena.context(true);
        for (DensityVolume volume : List.of(FULL, new DensityVolume(8, 32, 4, 16, 8, -4), FULL)) {
            trace.clear();
            DensityBuffer expected = sample(vanilla, expectedContext, volume);
            List<Call> calls = List.copyOf(trace);
            trace.clear();
            compare(expected, sample(compiled, context, volume));
            assertEquals(calls, trace);
            assertEquals(1, arena.acquired - arena.released, "Only the native cache owns a live buffer");
            for (int y = volume.minBlockY(); y < volume.minBlockY() + 12; ++y) {
                DfcFixtures.near(vanilla.sampleValue(expectedContext, volume.minBlockX(), y, volume.minBlockZ()),
                        compiled.sampleValue(context, volume.minBlockX(), y, volume.minBlockZ()));
            }
        }
    }

    @Test void failuresReleaseAllAcquiredColumnAndGridScratch() {
        var first = new Source(1, call -> { });
        var second = new Source(2, call -> { });
        var graph = interpolated(first, 4, 8).add(interpolated(second, 4, 8));
        DensitySampler compiled = compile(graph);
        TrackingArena arena = new TrackingArena();
        SamplerContext context = arena.context(false);
        second.fail = true;
        assertThrows(IllegalStateException.class, () -> sample(compiled, context, FULL));
        assertEquals(arena.acquired, arena.released);
        first.fail = true;
        assertThrows(IllegalStateException.class, () -> sample(compiled, context, FULL));
        assertEquals(arena.acquired, arena.released);
        first.fail = second.fail = false;
        compare(sample(graph.compileSampler(DfcFixtures.CONTEXT), SamplerContext.EMPTY_UNCACHED, FULL),
                sample(compiled, context, FULL));
        assertEquals(arena.acquired, arena.released);
    }

    @Test void nativeRangeChoiceRemainsEagerAndOrderedAroundColumnRegions() {
        List<Call> trace = new ArrayList<>();
        var inside = noodle(4, 8, trace::add);
        var selector = new DfcFixtures.Function(new Source(3, trace::add));
        var outside = noodle(4, 8, call -> trace.add(new Call(call.source() + 4, call.volume())));
        var graph = DensityFunctions.rangeChoice(selector, -0.5F, 0.5F, inside, outside);
        var volume = new DensityVolume(8, 32, 8, 0, -32, 0);
        DensityBuffer expected = sample(graph.compileSampler(DfcFixtures.CONTEXT), SamplerContext.EMPTY_UNCACHED, volume);
        List<Call> calls = List.copyOf(trace);
        trace.clear();
        compare(expected, sample(compile(graph), SamplerContext.EMPTY_UNCACHED, volume));
        assertEquals(calls, trace);
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6), trace.stream().map(Call::source).toList());
    }

    @Test void outputBackedOrdinaryInputAndUnknownInputsStayCorrect() {
        var source = new Source(0, call -> { });
        var cached = new CachingDensitySampler(0, source);
        var a = interpolated(new Source(1, call -> { }), 4, 8);
        var b = interpolated(new Source(2, call -> { }), 4, 8);
        for (DensitySampler first : List.of(cached, source)) {
            var graph = new DfcFixtures.Function(first).add(a).add(b);
            compare(sample(graph.compileSampler(DfcFixtures.CONTEXT), SamplerContext.EMPTY_UNCACHED, FULL),
                    sample(compile(graph), SamplerContext.EMPTY_UNCACHED, FULL));
        }
    }

    @Test void independentContextsCanRunOneCompiledColumnPlanConcurrently() throws Exception {
        var graph = noodle(4, 8, call -> { });
        DensitySampler compiled = compile(graph), vanilla = graph.compileSampler(DfcFixtures.CONTEXT);
        DensityBuffer expected = sample(vanilla, SamplerContext.EMPTY_UNCACHED, FULL);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 12; ++i) futures.add(executor.submit(() -> {
                TrackingArena arena = new TrackingArena();
                compare(expected, sample(compiled, arena.context(false), FULL));
                assertEquals(arena.acquired, arena.released);
            }));
            for (var future : futures) future.get();
        }
    }

    @Test void nestedSamplingKeepsTheOuterInvocationBuffersAlive() {
        DensitySampler[] nested = new DensitySampler[1];
        Source source = new Source(2, call -> { }) {
            private boolean active;
            @Override public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
                if (nested[0] != null && !this.active) {
                    this.active = true;
                    try { sample(nested[0], context, new DensityVolume(4, 8, 4, 0, 0, 0)); }
                    finally { this.active = false; }
                }
                super.sampleVolume(context, output, volume);
            }
        };
        var graph = interpolated(new Source(1, call -> { }), 4, 8).add(interpolated(source, 4, 8));
        DensityBuffer expected = sample(graph.compileSampler(DfcFixtures.CONTEXT), SamplerContext.EMPTY_UNCACHED, FULL);
        nested[0] = compile(graph);
        TrackingArena arena = new TrackingArena();
        compare(expected, sample(nested[0], arena.context(false), FULL));
        assertEquals(arena.acquired, arena.released);
    }
}
