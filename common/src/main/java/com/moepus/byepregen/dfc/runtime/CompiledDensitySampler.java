package com.moepus.byepregen.dfc.runtime;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.CacheNode;
import com.moepus.byepregen.dfc.compile.DensityColumnCompiler;
import net.minecraft.world.level.levelgen.densityfunction.*;

/** Immutable generated code; each call owns its sampling, memoization and scratch state. */
public final class CompiledDensitySampler implements ColumnDensitySampler {
    private final AstNode root;
    private final ColumnTemplate template;
    private final SamplingPlan samplingPlan;
    private final DensitySampler rootCache;

    public CompiledDensitySampler(AstNode root) {
        this.root = root;
        this.rootCache = root instanceof CacheNode cache ? cache.source() : null;
        this.samplingPlan = new SamplingPlan(root);
        this.template = DensityColumnCompiler.compile(root);
    }

    /** Unspecialized immutable graph: cache parents re-run slot placement for their complete graph. */
    public AstNode root() { return this.root; }

    @Override public float sampleValue(SamplerContext context, int x, int y, int z) {
        if (this.rootCache != null) return this.rootCache.sampleValue(context, x, y, z);
        ColumnEvaluationContext frame = PointContexts.acquire(context, x, y, z);
        try { return this.template.evaluator().samplePoint(frame, x, y, z); }
        finally { PointContexts.release(frame); }
    }

    @Override public ColumnSession openColumns(SamplerContext context, DensityVolume volume) {
        return new Session(context, volume);
    }

    @Override public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
        if (output.size() != volume.size()) throw new IllegalArgumentException("Density buffer/volume size mismatch");
        if (this.rootCache != null) { this.rootCache.sampleVolume(context, output, volume); return; }
        float[] column = new float[volume.sizeY()];
        try (ColumnSession session = this.openColumns(context, volume)) {
            for (int z = 0; z < volume.sizeZ(); ++z) {
                for (int x = 0; x < volume.sizeX(); ++x) {
                    session.evalColumn(x, z, column);
                    int offset = volume.indexUnchecked(x, 0, z);
                    for (int y = 0; y < column.length; ++y) output.set(offset + y, column[y]);
                }
            }
        }
    }

    private final class Session implements ColumnSession {
        private final DensityVolume volume;
        private final SamplingWorkspace workspace;
        private final ColumnEvaluationContext columns;
        private boolean closed;
        private Session(SamplerContext context, DensityVolume volume) {
            this.volume = volume;
            this.workspace = new SamplingWorkspace(context, volume, CompiledDensitySampler.this.samplingPlan);
            this.columns = new ColumnEvaluationContext(this.workspace);
            try { this.workspace.prepareEager(); }
            catch (RuntimeException | Error failure) { this.workspace.close(); throw failure; }
        }
        @Override public void evalColumn(int x, int z, float[] output) {
            if (this.closed) throw new IllegalStateException("Density column session is closed");
            if (x < 0 || x >= this.volume.sizeX() || z < 0 || z >= this.volume.sizeZ()) {
                throw new IndexOutOfBoundsException("Density column outside requested volume");
            }
            if (output.length != this.volume.sizeY()) throw new IllegalArgumentException("Density column height mismatch");
            this.columns.prepare(output, this.volume.blockX(x), this.volume.blockZ(z),
                    this.volume.minBlockY(), this.volume.stepBlockY());
            try {
                CompiledDensitySampler.this.template.evaluator().evalColumn(this.columns);
                DensityColumnMetrics.recordEvaluatedColumn();
            }
            finally { this.columns.clear(); }
        }
        @Override public void close() {
            if (this.closed) return;
            this.closed = true;
            this.workspace.close();
        }
    }
}
