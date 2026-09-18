package com.moepus.byepregen.dfctest;

import com.moepus.byepregen.dfc.runtime.ColumnDensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.*;

/** Exercises the Arena-facing interface while unrelated requests replace the native cache contents. */
final class DfcColumnVerifier {
    private static final float TOLERANCE = 3.0e-5F;

    private DfcColumnVerifier() { }

    static void verify(DensitySampler sampler, DensityVolume volume) {
        if (!(sampler instanceof ColumnDensitySampler columns)) return;
        SamplerContext referenceContext = context();
        SamplerContext streamingContext = context();
        DensityBuffer expected = DensityBuffer.createUnpooled(volume.size());
        sampler.sampleVolume(referenceContext, expected, volume);
        float[] actual = new float[volume.sizeY()];
        DensityVolume other = new DensityVolume(2, 3, 2, volume.minBlockX() + 9,
                volume.minBlockY() + 11, volume.minBlockZ() - 7, 2, 3, 2);
        try (var session = columns.openColumns(streamingContext, volume);
             var scratch = streamingContext.acquireBuffer(other)) {
            for (int z = 0; z < volume.sizeZ(); ++z) {
                for (int x = 0; x < volume.sizeX(); ++x) {
                    session.evalColumn(x, z, actual);
                    for (int y = 0; y < actual.length; ++y) {
                        float wanted = expected.get(volume.indexUnchecked(x, y, z));
                        if (!equivalent(wanted, actual[y])) {
                            throw new AssertionError("Streamed density mismatch at " + volume.blockX(x)
                                    + ',' + volume.blockY(y) + ',' + volume.blockZ(z) + ": " + wanted + " / " + actual[y]);
                        }
                    }
                    // Aquifer and debug surface queries can replace cached volumes between columns.
                    sampler.sampleValue(streamingContext, volume.blockX(x), volume.minBlockY() + 1, volume.blockZ(z));
                    if ((x & 3) == 0) sampler.sampleVolume(streamingContext, scratch, other);
                }
            }
        }
    }

    private static SamplerContext context() {
        return SamplerContext.builder().enableCaches().useBufferArena(new DensityBufferPool(20)).build();
    }

    private static boolean equivalent(float a, float b) {
        if (Float.floatToIntBits(a) == Float.floatToIntBits(b)) return true;
        return Float.isFinite(a) && Float.isFinite(b) && Math.abs(a - b) <= TOLERANCE * Math.max(1.0F, Math.abs(a));
    }
}
