package com.moepus.byepregen.dfc.runtime;

public interface CompiledColumnEvaluator {
    void evalColumn(ColumnEvaluationContext context);

    float samplePoint(ColumnEvaluationContext context, int x, int y, int z);
}
