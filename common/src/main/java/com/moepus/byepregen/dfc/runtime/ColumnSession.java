package com.moepus.byepregen.dfc.runtime;

public interface ColumnSession extends AutoCloseable {
    void evalColumn(int localX, int localZ, float[] output);
    @Override void close();
}
