package com.moepus.byepregen.dfc.runtime;

public interface ColumnCompiledDensityFunction {
    boolean byepregen$hasColumnMethod();

    void byepregen$evalColumn(ColumnEvaluationContext context);
}
