package com.moepus.byepregen.test;

import com.moepus.byepregen.config.Config;
import com.moepus.byepregen.config.ConfigManager;
import com.moepus.byepregen.dfc.runtime.DensityColumnMetrics;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

final class DensityColumnRuntimeProbe {
    private static final Logger LOGGER = LogUtils.getLogger();

    private DensityColumnRuntimeProbe() {
    }

    static String verify() {
        Config config = ConfigManager.getConfig();
        if (!config.worldgen().arena().enabled()
                || !config.worldgen().arena().densityColumnCompiler()) {
            return "disabled";
        }
        DensityColumnFrontendProbe.verify();
        DensityColumnDelegateProbe.verify();
        DensityColumnEvaluationProbe.verify();
        DensityColumnMetrics.Snapshot metrics = DensityColumnMetrics.snapshot();
        LOGGER.info("Density column raw metrics: {}", metrics);
        require(metrics.compiled() > 0, "no RandomState column template was compiled");
        require(metrics.bound() > 0, "no NoiseChunk column evaluator was bound");
        require(metrics.bindFailures() == 0, "column evaluator binding failed");
        require(metrics.evaluatedColumns() > 0, "ByePregen column evaluator was not called");
        require(metrics.addedInterpolators() == 0, "column binding added NoiseInterpolators");
        if (Boolean.getBoolean("byepregen.verifyDfcColumn")) {
            require(metrics.verifiedBoundaries() > 0, "no real final-density boundaries were verified");
        }
        String summary = "compiled=" + metrics.compiled()
                + " bound=" + metrics.bound()
                + " evaluated=" + metrics.evaluatedColumns()
                + " bindingFallbacks=" + metrics.bindingFallbacks()
                + " addedInterpolators=" + metrics.addedInterpolators()
                + " verifiedBoundaries=" + metrics.verifiedBoundaries();
        LOGGER.info("Density column runtime metrics: {}", summary);
        return summary;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
