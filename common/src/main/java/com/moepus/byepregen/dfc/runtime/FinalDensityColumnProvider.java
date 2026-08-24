package com.moepus.byepregen.dfc.runtime;

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
