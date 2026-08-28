package com.moepus.byepregen.worldgen.biome;

/** Climate lookup for a column where only the depth coordinate changes. */
public interface DepthClimateParameterList<T> {
    void byepregen$beginDepthColumn(long[] target);

    T byepregen$findValueAtDepth(long depth);
}
