package com.moepus.byepregen.worldgen.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import com.moepus.byepregen.dfc.runtime.FinalDensityColumnProvider;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.junit.jupiter.api.Test;

final class DensityColumnAdapterTest {
    @Test
    void adapterEvaluatesAndReusesEveryBlockOfNegativeHeightColumn() {
        PointProvider provider = new PointProvider();
        DensityColumnAdapter adapter = DensityColumnAdapter.tryCreate(provider,
                new DensityColumnAdapter.ColumnBounds(-64, 320, 8),
                new DensityColumnAdapter.InterpolationSources(
                        new NoiseChunk.NoiseInterpolator[0], new double[0][]));
        double[] output = adapter.output();
        for (int z = 0; z < 4; z++) {
            adapter.evaluate(17, -16 + z, z);
            assertEquals(384, output.length);
            assertSame(output, adapter.output());
            for (int index = 0; index < output.length; index++) {
                assertEquals((index - 64.0D) * (index - 64.0D) + 1 + z, output[index]);
            }
        }
        assertEquals(4, provider.calls);
    }

    @Test
    void invalidBoundsFailBeforeEvaluation() {
        assertThrows(IllegalArgumentException.class,
                () -> new DensityColumnAdapter.ColumnBounds(-64, 320, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DensityColumnAdapter.ColumnBounds(320, -64, 8));
        assertThrows(IllegalArgumentException.class,
                () -> new DensityColumnAdapter.ColumnBounds(-64, 319, 8));
    }

    private static final class PointProvider implements FinalDensityColumnProvider {
        private int calls;
        @Override public boolean byepregen$hasFinalDensityColumn() { return true; }
        @Override
        public void byepregen$evalFinalDensityColumn(double[] output, int x, int z, int minY,
                int blockStep, ColumnEvaluationContext.InterpolationProvider provider) {
            calls++;
            assertEquals(1, blockStep);
            for (int i = 0; i < output.length; i++) {
                double y = minY + i * blockStep;
                output[i] = y * y + x + z;
            }
        }
    }
}
