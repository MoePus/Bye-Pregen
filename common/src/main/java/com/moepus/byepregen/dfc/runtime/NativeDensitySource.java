package com.moepus.byepregen.dfc.runtime;

import com.moepus.byepregen.dfc.ast.AstNodes.DelegateNode;
import net.minecraft.world.level.levelgen.densityfunction.*;

/** A terminal sampling contract. Identity distinguishes opaque occurrences, even for equal samplers. */
public final class NativeDensitySource implements ColumnDensitySampler {
    private final DensitySampler sampler;
    private final int axes;
    private final boolean eager;
    private final SamplingPlan plan;
    public NativeDensitySource(DensitySampler sampler, int axes, boolean eager) {
        this.sampler = sampler;
        this.axes = axes;
        this.eager = eager;
        this.plan = new SamplingPlan(new DelegateNode(this, (axes & DensityFunction.AXIS_Y) == 0));
    }
    public DensitySampler sampler() { return this.sampler; }
    public int axes() { return this.axes; }
    public boolean eager() { return this.eager; }
    @Override public float sampleValue(SamplerContext context, int x, int y, int z) {
        return this.sampler.sampleValue(context, x, y, z);
    }
    @Override public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
        if (output.size() != volume.size()) throw new IllegalArgumentException("Density buffer/volume size mismatch");
        if (this.eager || !this.needsBroadcast(volume)) {
            this.sampler.sampleVolume(context, output, volume);
            return;
        }
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

    private boolean needsBroadcast(DensityVolume volume) {
        // Native slicing may already have collapsed every uniform axis. Such a request can write
        // straight into the caller's buffer without another sampling workspace or column copy.
        return (this.axes & DensityFunction.AXIS_X) == 0 && volume.sizeX() > 1
                || (this.axes & DensityFunction.AXIS_Y) == 0 && volume.sizeY() > 1
                || (this.axes & DensityFunction.AXIS_Z) == 0 && volume.sizeZ() > 1;
    }

    @Override public ColumnSession openColumns(SamplerContext context, DensityVolume volume) {
        return new Session(context, volume);
    }

    /** Native leaf roots need no generated class, but retain the same owned-data column contract. */
    private final class Session implements ColumnSession {
        private final DensityVolume volume;
        private final SamplingWorkspace workspace;
        private boolean closed;

        private Session(SamplerContext context, DensityVolume volume) {
            this.volume = volume;
            this.workspace = new SamplingWorkspace(context, volume, NativeDensitySource.this.plan);
        }

        @Override public void evalColumn(int x, int z, float[] output) {
            if (this.closed) throw new IllegalStateException("Density column session is closed");
            if (x < 0 || x >= this.volume.sizeX() || z < 0 || z >= this.volume.sizeZ()) {
                throw new IndexOutOfBoundsException("Density column outside requested volume");
            }
            if (output.length != this.volume.sizeY()) throw new IllegalArgumentException("Density column height mismatch");
            this.workspace.copyColumn(NativeDensitySource.this, this.volume.blockX(x), this.volume.blockZ(z),
                    output, 0, output.length);
        }

        @Override public void close() {
            if (this.closed) return;
            this.closed = true;
            this.workspace.close();
        }
    }
}
