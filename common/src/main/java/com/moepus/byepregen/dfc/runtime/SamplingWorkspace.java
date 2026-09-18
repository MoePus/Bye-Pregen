package com.moepus.byepregen.dfc.runtime;

import java.util.*;
import net.minecraft.world.level.levelgen.densityfunction.*;

/** Request-owned native samples survive cache eviction and interleaved Aquifer queries. */
public final class SamplingWorkspace implements AutoCloseable {
    private final SamplerContext context;
    private final DensityVolume volume;
    private final SamplingPlan plan;
    private final Map<DensitySampler, ConditionalSamples> selected = new IdentityHashMap<>();
    private final Map<DensitySampler, Samples> sampled = new IdentityHashMap<>();
    private final List<ScopedDensityBuffer> owned = new ArrayList<>();
    private boolean closed;

    SamplingWorkspace(SamplerContext context, DensityVolume volume, SamplingPlan plan) {
        this.plan = plan;
        this.context = context;
        this.volume = volume;
    }

    void prepareEager() {
        for (DensitySampler source : this.plan.eagerSources()) this.samples(source);
    }

    public float value(DensitySampler source, int x, int y, int z) {
        ConditionalSamples selected = this.selected(source);
        return selected == null ? this.samples(source).value(x, y, z) : selected.value(x, y, z);
    }

    public float[] column(DensitySampler source, int x, int z) {
        Samples samples = this.samples(source);
        return samples.column(x, z);
    }

    public void copyColumn(DensitySampler source, int x, int z, float[] output, int from, int to) {
        ConditionalSamples selected = this.selected(source);
        if (selected != null) { selected.copyColumn(x, z, output, from, to); return; }
        Samples samples = this.samples(source);
        if (samples.interpolation != null) {
            System.arraycopy(samples.column(x, z), from, output, from, to - from);
        } else if (samples.volume.sizeY() == this.volume.sizeY()) {
            int offset = samples.index(x, this.volume.minBlockY(), z);
            System.arraycopy(samples.buffer.values, offset + from, output, from, to - from);
        } else {
            Arrays.fill(output, from, to, samples.value(x, this.volume.minBlockY(), z));
        }
    }

    private ConditionalSamples selected(DensitySampler source) {
        if (!this.plan.conditional(source) || !(source instanceof NativeDensitySource nativeSource)
                || nativeSource.sampler() instanceof InterpolatedInput) return null;
        return this.selected.computeIfAbsent(source, ignored -> new ConditionalSamples(this.context, this.volume, nativeSource));
    }

    private Samples samples(DensitySampler source) {
        if (this.closed) throw new IllegalStateException("Density sampling workspace is closed");
        Samples existing = this.sampled.get(source);
        if (existing != null) return existing;
        Samples result = this.sample(source);
        this.sampled.put(source, result);
        return result;
    }

    private Samples sample(DensitySampler source) {
        NativeDensitySource contract = source instanceof NativeDensitySource s ? s : null;
        DensitySampler sampler = contract == null ? source : contract.sampler();
        if (sampler instanceof InterpolatedInput input) {
            ColumnPlan plan = new ColumnPlan(input.cellSizeXz(), input.cellSizeY());
            if (plan.supportsVolume(this.volume)) return this.interpolate(input, plan);
        }
        int axes = contract == null || contract.eager() ? DensityFunction.ALL_AXES : contract.axes();
        DensityVolume request = this.slice(axes);
        DensityBuffer buffer = this.acquire(request.size());
        sampler.sampleVolume(this.context, buffer, request);
        return new Samples(buffer, request, null, null);
    }

    private Samples interpolate(InterpolatedInput input, ColumnPlan plan) {
        DensityVolume request = plan.gridVolume(this.volume);
        DensityBuffer grid = this.acquire(request.size());
        input.input().sampleVolume(this.context, grid, request);
        float[] column = this.acquire(this.volume.sizeY()).values;
        InterpolationColumn interpolation = new InterpolationColumn(grid.values, column, request, plan, this::acquire);
        return new Samples(grid, request, interpolation, column);
    }

    private DensityVolume slice(int axes) {
        return new DensityVolume((axes & 1) == 0 ? 1 : this.volume.sizeX(),
                (axes & 2) == 0 ? 1 : this.volume.sizeY(), (axes & 4) == 0 ? 1 : this.volume.sizeZ(),
                this.volume.minBlockX(), this.volume.minBlockY(), this.volume.minBlockZ(),
                this.volume.stepBlockX(), this.volume.stepBlockY(), this.volume.stepBlockZ());
    }

    private ScopedDensityBuffer acquire(int size) {
        ScopedDensityBuffer result = this.context.acquireBuffer(new DensityVolume(1, size, 1, 0, 0, 0));
        this.owned.add(result);
        return result;
    }

    @Override public void close() {
        if (this.closed) return;
        this.closed = true;
        for (int i = this.owned.size() - 1; i >= 0; --i) this.owned.get(i).close();
        this.sampled.clear();
    }

    private final class Samples {
        private final DensityBuffer buffer;
        private final DensityVolume volume;
        private final InterpolationColumn interpolation;
        private final float[] column;
        private int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
        private Samples(DensityBuffer buffer, DensityVolume volume, InterpolationColumn interpolation, float[] column) {
            this.buffer = buffer;
            this.volume = volume;
            this.interpolation = interpolation;
            this.column = column;
        }
        private int index(int x, int y, int z) {
            return this.volume.indexUnchecked(this.volume.sizeX() == 1 ? 0 : (x - this.volume.minBlockX()) / this.volume.stepBlockX(),
                    this.volume.sizeY() == 1 ? 0 : (y - this.volume.minBlockY()) / this.volume.stepBlockY(),
                    this.volume.sizeZ() == 1 ? 0 : (z - this.volume.minBlockZ()) / this.volume.stepBlockZ());
        }
        private float value(int x, int y, int z) {
            if (this.interpolation == null) return this.buffer.get(this.index(x, y, z));
            return this.column(x, z)[(y - SamplingWorkspace.this.volume.minBlockY()) / SamplingWorkspace.this.volume.stepBlockY()];
        }
        private float[] column(int x, int z) {
            if (this.interpolation == null) throw new IllegalStateException("Source has no interpolation column");
            int localX = x - SamplingWorkspace.this.volume.minBlockX();
            int localZ = z - SamplingWorkspace.this.volume.minBlockZ();
            if (this.lastZ != localZ) this.interpolation.prepareZ(localZ);
            if (this.lastX != localX || this.lastZ != localZ) this.interpolation.fill(localX);
            this.lastX = localX;
            this.lastZ = localZ;
            return this.column;
        }
    }
}
