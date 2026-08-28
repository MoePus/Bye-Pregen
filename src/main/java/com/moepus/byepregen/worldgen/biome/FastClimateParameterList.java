package com.moepus.byepregen.worldgen.biome;

/** Allocation-free lookup using the seven quantized climate coordinates. */
public interface FastClimateParameterList<T> {
    T byepregen$findValue(long[] target);
}
