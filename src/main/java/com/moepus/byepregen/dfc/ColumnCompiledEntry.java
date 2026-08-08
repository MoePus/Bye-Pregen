package com.moepus.byepregen.dfc;

import com.moepus.byepregen.dfc.column.ColumnEvaluationContext;

/** Implemented directly by C2ME's generated DfcCompiled classes. */
public interface ColumnCompiledEntry {
    boolean byepregen$hasColumn(int rootIndex);

    void byepregen$evalColumn(int rootIndex, ColumnEvaluationContext context);
}
