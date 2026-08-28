package com.moepus.byepregen.worldgen.biome;

import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.NoiseRouter;

/** Runtime access to a NoiseChunk-bound compiled density column. */
public interface BiomeColumnEvaluator {
    Climate.Sampler byepregen$climateSampler(
            NoiseRouter router,
            List<Climate.ParameterPoint> spawnTarget
    );

    boolean byepregen$prepareBiomeColumns();

    boolean byepregen$hasDepthOnlyClimate();

    boolean byepregen$evalBiomeColumn(Root root, Request request);

    enum Root {
        TEMPERATURE,
        HUMIDITY,
        CONTINENTALNESS,
        EROSION,
        DEPTH,
        WEIRDNESS
    }

    record Request(
            double[] output,
            int blockX,
            int blockZ,
            int minBlockY,
            int blockStep
    ) {
        public Request {
            Objects.requireNonNull(output, "output");
            if (output.length == 0) throw new IllegalArgumentException("Column output must not be empty");
            if (blockStep <= 0) throw new IllegalArgumentException("Column block step must be positive");
        }
    }
}
