package com.moepus.byepregen.dfc.runtime;

import net.minecraft.world.level.levelgen.densityfunction.*;

/** A request owns its sampling data while callers consume final density columns. */
public interface ColumnDensitySampler extends DensitySampler {
    ColumnSession openColumns(SamplerContext context, DensityVolume volume);
}
