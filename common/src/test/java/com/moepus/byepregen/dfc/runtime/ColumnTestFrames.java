package com.moepus.byepregen.dfc.runtime;

import com.moepus.byepregen.dfc.ast.AstNodes.ConstantNode;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * Test-scoped access to a column frame backed by a real sampling workspace.
 *
 * <p>26.3 made {@link SamplingWorkspace} and {@link SamplingPlan} package private, so the
 * pre-26.3 {@code new ColumnEvaluationContext()} fixture cannot be built from the {@code dfc}
 * test packages. This factory mirrors what {@code CompiledDensitySampler.Session} does.
 */
public final class ColumnTestFrames {
    private ColumnTestFrames() {
    }

    /** A column frame for graphs that reach no interpolated or opaque delegate source. */
    public static ColumnEvaluationContext column(int sizeY) {
        return column(sizeY, 1);
    }

    /** A column frame whose native samples advance by {@code cellHeight} blocks per lane. */
    public static ColumnEvaluationContext column(int sizeY, int cellHeight) {
        DensityVolume volume = new DensityVolume(1, sizeY, 1, 0, 0, 0, 1, cellHeight, 1);
        SamplingPlan plan = new SamplingPlan(new ConstantNode(0.0F));
        return new ColumnEvaluationContext(
                new SamplingWorkspace(SamplerContext.EMPTY_UNCACHED, volume, plan));
    }

    /** A frame already prepared as an active column of {@code output.length} lanes. */
    public static ColumnEvaluationContext prepared(float[] output, int blockX, int blockZ,
                                                  int minY, int cellHeight) {
        ColumnEvaluationContext context = column(output.length, cellHeight);
        context.prepare(output, blockX, blockZ, minY, cellHeight);
        return context;
    }
}
