package com.moepus.byepregen.dfc;

import com.moepus.byepregen.dfc.column.ColumnEvaluationContext;

public interface ColumnCompiledDensityFunction {
    boolean byepregen$hasColumnMethod();

    void byepregen$evalColumn(ColumnEvaluationContext context);
}
