package com.moepus.byepregen.dfc;

import com.moepus.byepregen.dfc.column.ColumnEvaluationContext;

public interface FinalDensityColumnProvider {
    boolean byepregen$hasFinalDensityColumn();

    void byepregen$evalFinalDensityColumn(
            double[] output,
            int blockX,
            int blockZ,
            int minY,
            int cellHeight,
            ColumnEvaluationContext.InterpolationProvider interpolationProvider
    );
}
