package com.moepus.byepregen.dfc;

import com.moepus.byepregen.dfc.runtime.ColumnDensitySampler;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.densityfunction.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static com.moepus.byepregen.dfc.ColumnTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** 64a6c84's interior-density contract against the 26.3 native float interpolator. */
final class InterpolatedFinalDensityTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void nonlinearFinalDensityIsNotInterpolatedFromItsCornerResults() {
        DensitySampler ramp = new Source(0, call -> { }) {
            @Override public float sampleValue(SamplerContext context, int x, int y, int z) {
                return Math.floorMod(y, 16) / 4.0F - 1.0F;
            }
        };
        DensityFunction input = interpolated(ramp, 4, 8);
        var volume = new DensityVolume(8, 32, 8, -8, -32, -8);
        // Both cell ends are positive after abs, while the actual interior is negative.
        DensityFunction density = input.abs().add(-0.25F);
        DensityBuffer actual = sample(compile(density), SamplerContext.EMPTY_UNCACHED, volume);
        assertTrue(actual.get(volume.indexUnchecked(0, 0, 0)) > 0);
        assertTrue(actual.get(volume.indexUnchecked(0, 4, 0)) < 0);
        for (DensityFunction graph : List.of(density, DensityFunctions.min(input, DensityFunctions.constant(0.25F)),
                DensityFunctions.max(input, DensityFunctions.constant(-0.25F)), input.squeeze(),
                DensityFunctions.rangeChoice(input, -0.3F, 0.3F, input.abs(), input.squeeze()))) {
            assertVolume(graph, volume);
        }
    }

    @Test void nestedAndMixedInterpolatorsRetainNativePointAndVolumePaths() {
        for (int[] cell : new int[][]{{4, 8}, {8, 4}, {3, 5}}) {
            DensityFunction inner = interpolated(new Source(1, call -> { }), cell[0], cell[1]);
            DensityFunction outer = DensityFunctions.interpolated(inner.abs().add(-0.125F), cell[0] * 2, cell[1] * 2);
            DensityFunction graph = outer.squeeze().add(inner.square().mul(0.375F));
            var aligned = new DensityVolume(cell[0] * 4, cell[1] * 4, cell[0] * 4,
                    -cell[0] * 4, -cell[1] * 4, -cell[0] * 4);
            assertVolume(graph, aligned);
            for (DensityVolume volume : DfcFixtures.VOLUMES) assertVolume(graph, volume);
            // Native sampleValue uses lerp3; sampleVolume uses Z/X lerps and incremental Y.
            DfcFixtures.compare(graph.compileSampler(DfcFixtures.CONTEXT), compile(graph));
        }
    }

    @Test void streamedColumnsSurviveReorderingAndAquiferStyleCacheReplacement() {
        var cached = new CachingDensitySampler(0, new Source(2, call -> { }));
        DensityFunction input = interpolated(cached, 4, 8);
        DensityFunction graph = DensityFunctions.rangeChoice(input, -0.3F, 0.3F,
                input.square().add(0.125F), input.squeeze());
        DensitySampler nativeSampler = graph.compileSampler(DfcFixtures.CONTEXT);
        var compiled = (ColumnDensitySampler) compile(graph);
        var volume = new DensityVolume(8, 32, 8, -16, -64, -16);
        DensityBuffer expected = sample(nativeSampler, SamplerContext.EMPTY_UNCACHED, volume);
        TrackingArena arena = new TrackingArena();
        SamplerContext context = arena.context(true);
        float[] column = new float[volume.sizeY()];
        try (var session = compiled.openColumns(context, volume)) {
            for (int index = volume.sizeX() * volume.sizeZ() - 1; index >= 0; --index) {
                int x = index % volume.sizeX(), z = index / volume.sizeX();
                // Replace the source cache's coarse grid by an unrelated full-resolution volume.
                sample(cached, context, new DensityVolume(3, 7, 2, 97, -13, -83));
                compiled.sampleValue(context, volume.blockX(x), -61, volume.blockZ(z));
                session.evalColumn(x, z, column);
                assertColumn(expected, column, volume, x, z);
                session.evalColumn(0, 0, column);
                assertColumn(expected, column, volume, 0, 0);
            }
        }
        assertEquals(1, arena.acquired - arena.released, "Only the native cache may retain a buffer");
    }

    private static void assertVolume(DensityFunction graph, DensityVolume volume) {
        DensityBuffer expected = sample(graph.compileSampler(DfcFixtures.CONTEXT), SamplerContext.EMPTY_UNCACHED, volume);
        DensityBuffer actual = sample(compile(graph), SamplerContext.EMPTY_UNCACHED, volume);
        for (int i = 0; i < expected.size(); ++i) assertDensity(expected.get(i), actual.get(i));
    }

    private static void assertColumn(DensityBuffer expected, float[] actual, DensityVolume volume, int x, int z) {
        for (int y = 0; y < actual.length; ++y) assertDensity(expected.get(volume.indexUnchecked(x, y, z)), actual[y]);
    }

    private static void assertDensity(float expected, float actual) {
        DfcFixtures.near(expected, actual);
        assertEquals(expected > 0, actual > 0, "Aquifer solid/non-solid classification");
    }
}
