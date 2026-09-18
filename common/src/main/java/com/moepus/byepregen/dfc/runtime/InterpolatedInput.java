package com.moepus.byepregen.dfc.runtime;

import net.minecraft.world.level.levelgen.densityfunction.*;

/** Compilation-time interpolation metadata; the native sampler remains the fallback and point path. */
public record InterpolatedInput(DensitySampler input, DensitySampler fallback, int cellSizeXz, int cellSizeY)
        implements DensitySampler {
    @Override
    public float sampleValue(SamplerContext context, int x, int y, int z) {
        return this.fallback.sampleValue(context, x, y, z);
    }

    @Override
    public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
        this.fallback.sampleVolume(context, output, volume);
    }
}
