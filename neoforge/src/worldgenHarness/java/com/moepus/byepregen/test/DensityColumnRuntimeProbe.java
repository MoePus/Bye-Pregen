package com.moepus.byepregen.test;

import com.moepus.byepregen.config.Config;
import com.moepus.byepregen.config.ConfigManager;
import com.moepus.byepregen.dfc.runtime.DensityColumnMetrics;
import com.moepus.byepregen.integration.runtime.ModEnvironment;
import com.mojang.logging.LogUtils;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;

final class DensityColumnRuntimeProbe {
    private static final int BIOME_ROOT_COUNT = 6;
    private static final int BIOME_QUART_COLUMNS_PER_FILL = 4 * 4;
    private static final int BIOME_COLUMNS_PER_FILL = BIOME_ROOT_COUNT * 4 * 4;
    private static final int MIN_DIRECT_FILL_PERCENT = 99;
    private static final long BIOME_SETTLE_TIMEOUT_NANOS = 5_000_000_000L;
    private static final long BIOME_SETTLE_POLL_NANOS = 10_000_000L;
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
        if (!biomeColumnsConflict()) ClimateRTreeColumnProbe.verify();
        DensityColumnMetrics.Snapshot metrics = awaitBiomeOutcomes();
        LOGGER.info("Density column raw metrics: {}", metrics);
        require(metrics.compiled() > 0, "no RandomState column template was compiled");
        require(metrics.bound() > 0, "no NoiseChunk column evaluator was bound");
        require(metrics.bindFailures() == 0, "column evaluator binding failed");
        require(metrics.evaluatedColumns() > 0, "ByePregen column evaluator was not called");
        require(metrics.addedInterpolators() == 0, "column binding added NoiseInterpolators");
        verifyBiomeMetrics(metrics);
        if (Boolean.getBoolean("byepregen.verifyDfcColumn")) {
            require(metrics.verifiedBoundaries() > 0, "no real final-density boundaries were verified");
            require(metrics.biomeVerifiedCells() > 0, "no direct biome cells were verified");
        }
        String summary = "compiled=" + metrics.compiled()
                + " bound=" + metrics.bound()
                + " evaluated=" + metrics.evaluatedColumns()
                + " bindingFallbacks=" + metrics.bindingFallbacks()
                + " addedInterpolators=" + metrics.addedInterpolators()
                + " verifiedBoundaries=" + metrics.verifiedBoundaries()
                + " biomeBound=" + metrics.biomeBound()
                + " biomeEvaluatedColumns=" + metrics.biomeEvaluatedColumns()
                + " biomeDepthCachedColumns=" + metrics.biomeDepthCachedColumns()
                + " biomeFillAttempts=" + metrics.biomeFillAttempts()
                + " biomeArenaFallbacks=" + metrics.biomeArenaFallbacks()
                + " biomeSourceFallbacks=" + metrics.biomeSourceFallbacks()
                + " biomeBindFallbacks=" + metrics.biomeBindFallbacks()
                + " biomeEvaluationFallbacks=" + metrics.biomeEvaluationFallbacks()
                + " biomeFills=" + metrics.biomeFills()
                + " biomeVerifiedCells=" + metrics.biomeVerifiedCells();
        LOGGER.info("Density column runtime metrics: {}", summary);
        return summary;
    }

    private static void verifyBiomeMetrics(DensityColumnMetrics.Snapshot metrics) {
        if (biomeColumnsConflict()) {
            require(metrics.biomeFillAttempts() == 0,
                    "biome column hook ran despite a conflicting biome mod");
            require(metrics.biomeBound() == 0,
                    "biome columns were bound despite a conflicting biome mod");
            require(metrics.biomeFills() == 0,
                    "biome palettes were filled directly despite a conflicting biome mod");
            require(metrics.biomeDepthCachedColumns() == 0,
                    "biome R-tree column cache ran despite a conflicting biome mod");
            return;
        }
        long attempts = metrics.biomeFillAttempts();
        long fills = metrics.biomeFills();
        require(attempts > 0, "biome column hook was not called");
        require(attempts == metrics.biomeOutcomes(),
                "biome fill attempts do not match classified outcomes");
        require(metrics.biomeBindFallbacks() == 0, "biome column binding fell back");
        require(metrics.biomeEvaluationFallbacks() == 0, "biome column evaluation fell back");
        require(metrics.biomeBound() >= BIOME_ROOT_COUNT
                        && metrics.biomeBound() % BIOME_ROOT_COUNT == 0,
                "biome climate columns were not bound in complete root sets");
        require(metrics.biomeEvaluatedColumns() == fills * BIOME_COLUMNS_PER_FILL,
                "biome evaluated-column count does not match successful fills");
        require(metrics.biomeDepthCachedColumns() == fills * BIOME_QUART_COLUMNS_PER_FILL,
                "Vanilla climate roots did not use the depth-only R-tree cache");
        require(fills * 100L >= attempts * MIN_DIRECT_FILL_PERCENT,
                "direct biome fill coverage fell below " + MIN_DIRECT_FILL_PERCENT + '%');
    }

    private static boolean biomeColumnsConflict() {
        return ModEnvironment.isModLoaded("reterraforged")
                || ModEnvironment.isModLoaded("terrablender")
                || ModEnvironment.isModLoaded("blueprint");
    }

    private static DensityColumnMetrics.Snapshot awaitBiomeOutcomes() {
        long deadline = System.nanoTime() + BIOME_SETTLE_TIMEOUT_NANOS;
        DensityColumnMetrics.Snapshot metrics;
        do {
            metrics = DensityColumnMetrics.snapshot();
            if (metrics.biomeFillAttempts() == metrics.biomeOutcomes()) return metrics;
            LockSupport.parkNanos(BIOME_SETTLE_POLL_NANOS);
        } while (System.nanoTime() < deadline);
        return metrics;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
